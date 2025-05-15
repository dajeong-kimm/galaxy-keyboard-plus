from fastapi import APIRouter, Form
from typing import Optional, List, Dict
from app.utils.semantic_search import search_similar_items_enhanced_optimized
from app.utils.chat_vector_store import save_chat_vector_to_pinecone
import json
import time
import asyncio
from concurrent.futures import ThreadPoolExecutor
import logging
import psycopg2
import os
from openai import OpenAI
from dotenv import load_dotenv
import hashlib
from datetime import datetime, timedelta
import traceback

load_dotenv()
router = APIRouter()
logger = logging.getLogger(__name__)

# ThreadPoolExecutor
executor = ThreadPoolExecutor(max_workers=10)

# DB 연결 설정
DB_PARAMS = {
    "host": "3.38.95.110",
    "port": "5434",
    "database": os.getenv("POSTGRES_RAG_DB_NAME"),
    "user": os.getenv("POSTGRES_RAG_USER"),
    "password": os.getenv("POSTGRES_RAG_PASSWORD"),
}

# OpenAI 클라이언트
openai_client = OpenAI(api_key=os.getenv("OPENAI_API_KEY_2"))

# 캐시 설정
cache: Dict[str, Dict] = {}
CACHE_TTL_SECONDS = 3600
MAX_CACHE_SIZE = 500


def get_cache_key(user_id: str, query: str) -> str:
    """캐시 키 생성"""
    cache_data = f"image:{user_id}:{query}"
    return hashlib.md5(cache_data.encode()).hexdigest()


def get_from_cache(key: str) -> Optional[Dict]:
    """캐시에서 데이터 가져오기"""
    if key in cache:
        cached_data = cache[key]
        if datetime.now() < cached_data["expires_at"]:
            logger.info(f"✅ 캐시 히트: {key}")
            return cached_data["data"]
        else:
            del cache[key]
            logger.info(f"🗑️ 만료된 캐시 삭제: {key}")
    return None


def set_cache(key: str, data: Dict):
    """캐시에 데이터 저장"""
    if len(cache) >= MAX_CACHE_SIZE:
        oldest_key = min(cache.keys(), key=lambda k: cache[k]["created_at"])
        del cache[oldest_key]
        logger.info(f"🗑️ 캐시 크기 초과로 오래된 항목 제거: {oldest_key}")

    cache[key] = {
        "data": data,
        "created_at": datetime.now(),
        "expires_at": datetime.now() + timedelta(seconds=CACHE_TTL_SECONDS),
    }
    logger.info(f"💾 캐시 저장: {key}")


async def determine_image_query_intent(query: str) -> str:
    """이미지 관련 질문의 의도를 파악"""

    def sync_determine_intent():
        prompt = f"""
다음 질문을 분석하여 사용자의 의도를 파악하세요.
- 특정 사진을 찾는 질문인 경우: "find_photo"
- 정보를 물어보는 질문인 경우: "get_info"

질문: {query}

사진을 찾는 표현들:
- "사진", "이미지", "찍은", "촬영한", "보여줘", "찾아줘", "있어?", "어디"
- 특정 시간/장소/활동과 함께 사진을 언급하는 경우

정보를 묻는 표현들:
- "내용", "정보", "알려줘", "설명", "어때?", "뭐야?", "무엇"
- 사실이나 지식을 묻는 경우

응답은 "find_photo" 또는 "get_info" 중 하나만 반환하세요.
"""

        response = openai_client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {
                    "role": "system",
                    "content": "너는 의도 분류 전문가야. 정확하게 분류해줘.",
                },
                {"role": "user", "content": prompt},
            ],
            temperature=0,
        )

        return response.choices[0].message.content.strip().lower()

    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(executor, sync_determine_intent)


async def extract_photo_keywords(query: str) -> List[str]:
    """사진 검색을 위한 키워드 추출 - 개선된 버전"""

    def sync_extract_keywords():
        prompt = f"""
다음 질문에서 사진을 찾기 위한 모든 관련 키워드를 추출하세요.
날짜/시간 키워드는 제외하고, 사진의 내용을 나타내는 핵심 키워드만 추출합니다.

질문: {query}

카테고리별 키워드:
1. 장소: 집, 회사, 카페, 공원, 바다, 산, 식당, 학교, 호텔, 거리, 매장
2. 활동: 여행, 파티, 회의, 식사, 운동, 산책, 쇼핑, 놀이, 행사, 축제
3. 사람: 가족, 친구, 동료, 애완동물, 연인, 부모님, 아이, 손님
4. 사물: 음식, 차, 건물, 풍경, 꽃, 선물, 케이크, 문서, 제품
5. 상황: 생일, 기념일, 휴가, 출장, 모임, 데이트, 결혼식

추출 규칙:
1. 날짜/시간 표현 제외
2. 동사는 명사로 변환
3. 가능한 많은 연관 키워드 포함
4. 문맥상 암시된 키워드도 추가

예시:
"생일 파티 사진" → ["생일", "파티", "축하", "케이크", "친구", "모임", "기념일"]
"회사에서 찍은 사진" → ["회사", "사무실", "직장", "동료", "업무", "미팅"]
"여행 가서 찍은 풍경" → ["여행", "풍경", "관광", "휴가", "자연", "경치"]

JSON 배열로만 반환하세요.
"""

        response = openai_client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {
                    "role": "system",
                    "content": "키워드 추출 전문가. 사진 검색에 도움되는 모든 키워드를 추출해. JSON만 반환.",
                },
                {"role": "user", "content": prompt},
            ],
            temperature=0.3,
        )

        keywords_raw = response.choices[0].message.content

        # 코드블록 제거
        if "```" in keywords_raw:
            keywords_raw = (
                keywords_raw.replace("```json", "").replace("```", "").strip()
            )

        try:
            keywords = json.loads(keywords_raw)

            # 키워드 확장
            expanded_keywords = set(keywords)

            for keyword in keywords:
                # 활동 관련 확장
                if keyword in ["여행", "관광", "휴가"]:
                    expanded_keywords.update(["나들이", "외출", "탐방"])
                elif keyword in ["파티", "모임"]:
                    expanded_keywords.update(["행사", "축하", "기념"])
                elif keyword in ["식사", "음식"]:
                    expanded_keywords.update(["맛집", "요리", "외식"])

                # 장소 관련 확장
                if keyword == "회사":
                    expanded_keywords.update(["사무실", "직장", "업무"])
                elif keyword == "집":
                    expanded_keywords.update(["홈", "거실", "방"])
                elif keyword in ["바다", "해변"]:
                    expanded_keywords.update(["해안", "바닷가", "해수욕장"])

                # 사람 관련 확장
                if keyword == "가족":
                    expanded_keywords.update(["부모", "형제", "친척"])
                elif keyword == "친구":
                    expanded_keywords.update(["동료", "친목", "우정"])

            return list(expanded_keywords)

        except json.JSONDecodeError:
            return [
                kw.strip() for kw in keywords_raw.strip("[]").split(",") if kw.strip()
            ]

    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(executor, sync_extract_keywords)


async def search_photos_by_keywords(user_id: str, keywords: List[str]) -> List[Dict]:
    """키워드로 DB에서 사진 검색 - 매치 점수 포함"""

    def sync_search_photos():
        connection = psycopg2.connect(**DB_PARAMS)
        cursor = connection.cursor()

        try:
            # 개선된 검색 쿼리 - 매치 점수와 정보 포함
            query = """
            WITH keyword_matches AS (
                SELECT 
                    i.access_id,
                    i.image_time,
                    i.caption,
                    ik.keyword,
                    CASE 
                        WHEN ik.keyword = ANY(%s) THEN 1.0
                        ELSE 0.5
                    END as match_score
                FROM images i
                JOIN image_keywords ik ON i.id = ik.image_id
                WHERE i.user_id = %s 
                AND (
                    ik.keyword = ANY(%s)
                    OR EXISTS (
                        SELECT 1 FROM unnest(%s::text[]) AS search_kw
                        WHERE ik.keyword ILIKE '%%' || search_kw || '%%'
                    )
                )
            ),
            aggregated AS (
                SELECT 
                    access_id,
                    image_time,
                    caption,
                    COUNT(DISTINCT keyword) as keyword_count,
                    SUM(match_score) as total_score,
                    STRING_AGG(DISTINCT keyword, ', ') as matched_keywords
                FROM keyword_matches
                GROUP BY access_id, image_time, caption
            )
            SELECT 
                access_id,
                caption,
                keyword_count,
                total_score,
                matched_keywords,
                image_time
            FROM aggregated
            ORDER BY total_score DESC, keyword_count DESC, image_time DESC
            LIMIT 30;
            """

            cursor.execute(query, (keywords, user_id, keywords, keywords))
            results = cursor.fetchall()

            # 결과를 딕셔너리 리스트로 변환
            photo_results = []
            for row in results[:10]:  # 상위 10개만
                photo_results.append(
                    {
                        "access_id": row[0],
                        "caption": row[1],
                        "match_count": row[2],
                        "score": float(row[3]),
                        "matched_keywords": row[4],
                        "image_time": row[5].isoformat() if row[5] else None,
                    }
                )

                logger.info(
                    f"📷 사진: {row[0]}, 점수: {row[3]:.1f}, 매치: {row[2]}개, 키워드: {row[4]}"
                )

            return photo_results

        finally:
            cursor.close()
            connection.close()

    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(executor, sync_search_photos)


async def expand_info_query(query: str) -> List[str]:
    """정보 검색을 위한 쿼리 확장"""

    def sync_expand_query():
        prompt = f"""
다음 질문과 관련된 다양한 검색 쿼리를 생성하세요.
원본 질문의 의미를 유지하면서 동의어, 관련어, 다양한 표현을 사용합니다.

원본 질문: {query}

변형 규칙:
1. 핵심 키워드 포함
2. 동의어 사용
3. 약어와 전체 표현
4. 한국어와 영어 혼용
5. 관련 용어 추가

예시:
"스타벅스 와이파이 알려줘" → 
["스타벅스 WiFi", "스타벅스 와이파이", "starbucks wifi password", "스타벅스 인터넷", "스타벅스 무선인터넷", "스타벅스 비밀번호", "KT WiFi zone", "스타벅스 네트워크"]

JSON 배열로 5-7개의 변형 쿼리를 반환하세요.
"""

        response = openai_client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {"role": "system", "content": "쿼리 확장 전문가. JSON 배열만 반환."},
                {"role": "user", "content": prompt},
            ],
            temperature=0.3,
        )

        queries_raw = response.choices[0].message.content

        # 코드블록 제거
        if "```" in queries_raw:
            queries_raw = queries_raw.replace("```json", "").replace("```", "").strip()

        try:
            expanded = json.loads(queries_raw)
            expanded.append(query)  # 원본 쿼리 포함
            return list(set(expanded))[:8]
        except:
            return [query]

    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(executor, sync_expand_query)


async def generate_info_answer(
    user_id: str, query: str, context_info: List[Dict]
) -> str:
    """정보 기반 질문에 대한 답변 생성"""

    def sync_generate_answer():
        # context 정보를 더 체계적으로 정리
        context_texts = []
        for i, item in enumerate(context_info[:5]):  # 상위 5개만 사용
            text = item.get("text", "").strip()
            if text:
                context_texts.append(f"{i+1}. {text}")

        context_text = "\n".join(context_texts)

        if not context_text:
            return "관련 정보를 찾을 수 없습니다. 다른 질문을 해보세요."

        prompt = f"""
사용자의 질문에 대해 아래 제공된 정보를 활용하여 답변하세요.
정보가 부족하다면 그 사실을 언급하고, 알려진 내용만으로 답변하세요.

[제공된 정보]
{context_text}

[사용자 질문]
{query}

답변 작성 규칙:
1. 제공된 정보를 기반으로 정확하게 답변
2. 친근하고 자연스러운 대화체 사용
3. 불확실한 내용은 추측하지 말고 명시
4. 필요시 추가 정보를 요청하는 안내 포함

답변:
"""

        response = openai_client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {
                    "role": "system",
                    "content": "너는 사용자의 개인 비서야. 제공된 정보를 활용해 정확하고 도움이 되는 답변을 해줘.",
                },
                {"role": "user", "content": prompt},
            ],
            temperature=0.7,
        )

        return response.choices[0].message.content

    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(executor, sync_generate_answer)


async def save_query_async(user_id: str, role: str, content: str, timestamp: int):
    """쿼리 저장을 위한 비동기 래퍼"""
    try:
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(
            executor, save_chat_vector_to_pinecone, user_id, role, content, timestamp
        )
        logger.info(f"✅ 쿼리 저장 완료: {user_id}")
    except Exception as e:
        logger.error(f"❌ 쿼리 저장 실패: {user_id} - {str(e)}")


async def save_result_async(user_id: str, role: str, content: str, timestamp: int):
    """결과 저장을 위한 비동기 래퍼"""
    try:
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(
            executor, save_chat_vector_to_pinecone, user_id, role, content, timestamp
        )
        logger.info(f"✅ 결과 저장 완료: {user_id}")
    except Exception as e:
        logger.error(f"❌ 결과 저장 실패: {user_id} - {str(e)}")


@router.post("/image/")
async def process_image_query(user_id: str = Form(...), query: str = Form(...)):
    """이미지 관련 질문 처리 API"""
    total_start = time.time()

    logger.info(f"🔍 이미지 쿼리 시작 - user: {user_id}, query: {query}")

    # 캐시 확인
    cache_key = get_cache_key(user_id, query)
    cached_result = get_from_cache(cache_key)
    if cached_result:
        cached_result["_timings"]["total"] = time.time() - total_start
        cached_result["_from_cache"] = True
        return cached_result

    timestamp = int(time.time())
    timings = {}

    try:
        # 1. 쿼리 저장 (비동기)
        asyncio.create_task(save_query_async(user_id, "user", query, timestamp))

        # 2. 의도 파악
        intent_start = time.time()
        intent = await determine_image_query_intent(query)
        timings["intent_detection"] = time.time() - intent_start
        logger.info(f"🎯 의도 파악: {intent} ({timings['intent_detection']:.3f}초)")

        if intent == "find_photo":
            # 사진 찾기 로직

            # 3-1. 키워드 추출
            keyword_start = time.time()
            keywords = await extract_photo_keywords(query)
            timings["keyword_extraction"] = time.time() - keyword_start
            logger.info(
                f"🔍 추출된 키워드 ({len(keywords)}개): {keywords[:10]} ({timings['keyword_extraction']:.3f}초)"
            )

            # 4-1. DB에서 사진 검색
            db_search_start = time.time()
            photo_results = await search_photos_by_keywords(user_id, keywords)
            timings["db_search"] = time.time() - db_search_start

            # photo_ids만 추출
            photo_ids = [photo["access_id"] for photo in photo_results]

            logger.info(
                f"📷 검색된 사진: {len(photo_ids)}개 ({timings['db_search']:.3f}초)"
            )

            # 결과 구성
            result = {
                "type": "photo_search",
                "query": query,
                "keywords": keywords,
                "photo_ids": photo_ids,
                "photo_details": photo_results[:5],  # 상위 5개 상세 정보
                "answer": "",
                "count": len(photo_ids),
                "_timings": timings,
            }

        else:  # get_info
            # 정보 찾기 로직

            # 3-2. 쿼리 확장
            query_expand_start = time.time()
            expanded_queries = await expand_info_query(query)
            timings["query_expansion"] = time.time() - query_expand_start
            logger.info(f"🔍 확장된 쿼리: {expanded_queries}")

            # 3-3. 벡터 검색으로 관련 정보 찾기
            vector_search_start = time.time()

            # namespace를 user_id_information으로 설정
            namespace = f"{user_id}_information"

            loop = asyncio.get_event_loop()

            logger.info(
                f"🔍 벡터 검색 시작 - namespace: {namespace}, queries: {expanded_queries}"
            )

            # 먼저 원본 쿼리로 검색
            context_info = []
            try:
                # 1. 확장된 쿼리로 검색
                result1 = await loop.run_in_executor(
                    executor,
                    search_similar_items_enhanced_optimized,
                    user_id,
                    expanded_queries,
                    "info",
                    20,
                )
                context_info.extend(result1)
                logger.info(f"✅ 확장된 쿼리 결과: {len(result1)}개")

                # 2. 원본 쿼리로도 검색
                if len(context_info) < 5:
                    result2 = await loop.run_in_executor(
                        executor,
                        search_similar_items_enhanced_optimized,
                        user_id,
                        [query],
                        "info",
                        10,
                    )
                    context_info.extend(result2)
                    logger.info(f"✅ 원본 쿼리 결과: {len(result2)}개")

                # 중복 제거
                seen_texts = set()
                unique_results = []
                for item in context_info:
                    text = item.get("text", "")
                    if text and text not in seen_texts:
                        seen_texts.add(text)
                        unique_results.append(item)

                context_info = unique_results[:20]

                # 검색 결과 디버깅
                if context_info:
                    logger.info(f"🔍 검색 결과 샘플:")
                    for i, info in enumerate(context_info[:3]):
                        logger.info(f"  {i+1}. {info.get('text', '')[:100]}...")
                else:
                    logger.warning(f"⚠️ 검색 결과 없음 - namespace: {namespace}")

            except Exception as e:
                logger.error(f"❌ 벡터 검색 실패: {str(e)}", exc_info=True)
                context_info = []

            timings["vector_search"] = time.time() - vector_search_start
            logger.info(
                f"📚 검색된 정보: {len(context_info)}개 ({timings['vector_search']:.3f}초)"
            )

            # 4-2. 답변 생성
            answer_start = time.time()
            answer = await generate_info_answer(user_id, query, context_info)
            timings["answer_generation"] = time.time() - answer_start
            logger.info(f"✍️ 답변 생성 완료 ({timings['answer_generation']:.3f}초)")

            # 결과 구성
            result = {
                "type": "info_search",
                "query": query,
                "answer": answer,
                "context_count": len(context_info),
                "photo_ids": [],
                "_timings": timings,
                "_debug": {
                    "expanded_queries": expanded_queries,
                    "namespace": namespace,
                    "context_sample": (
                        context_info[0].get("text", "")[:200] if context_info else None
                    ),
                },
            }

        # 전체 시간
        timings["total"] = time.time() - total_start
        result["_timings"] = timings
        result["_from_cache"] = False

        # 캐시에 저장
        set_cache(cache_key, result)

        # 결과 저장 (비동기)
        asyncio.create_task(
            save_result_async(
                user_id,
                "assistant",
                json.dumps(result, ensure_ascii=False),
                int(time.time()),
            )
        )

        logger.info(
            f"""
⏱️ Image API 성능 요약:
- 의도 파악: {timings['intent_detection']:.3f}초
- {'키워드 추출' if intent == 'find_photo' else '쿼리 확장'}: {timings.get('keyword_extraction', timings.get('query_expansion', 0)):.3f}초
- {'DB 검색' if intent == 'find_photo' else '벡터 검색'}: {timings.get('db_search', timings.get('vector_search', 0)):.3f}초
- {'DB 검색' if intent == 'find_photo' else '답변 생성'}: {timings.get('db_search', timings.get('answer_generation', 0)):.3f}초
- 전체 시간: {timings['total']:.3f}초
        """
        )

        return result

    except Exception as e:
        error_time = time.time() - total_start
        logger.error(
            f"❌ 처리 중 오류 발생 ({error_time:.3f}초): {str(e)}", exc_info=True
        )
        return {
            "error": "요청 처리 중 오류가 발생했습니다.",
            "detail": str(e),
            "timings": timings,
            "processing_time": f"{error_time:.3f}초",
        }


# 캐시 관리 엔드포인트
@router.get("/image/cache/status")
async def get_cache_status():
    """캐시 상태 확인"""
    current_time = datetime.now()
    valid_count = sum(1 for data in cache.values() if current_time < data["expires_at"])
    expired_count = len(cache) - valid_count

    return {
        "total_items": len(cache),
        "valid_items": valid_count,
        "expired_items": expired_count,
        "max_size": MAX_CACHE_SIZE,
        "ttl_seconds": CACHE_TTL_SECONDS,
    }


@router.delete("/image/cache/clear")
async def clear_cache():
    """캐시 초기화"""
    cache_size = len(cache)
    cache.clear()
    logger.info(f"🗑️ 캐시 초기화: {cache_size}개 항목 삭제")
    return {"cleared_items": cache_size}


# 검색 테스트를 위한 디버그 엔드포인트
@router.post("/image/debug-search")
async def debug_search(
    user_id: str = Form(...),
    query: str = Form(...),
    namespace: Optional[str] = Form(None),
):
    """검색 디버깅을 위한 엔드포인트"""
    try:
        # namespace가 제공되지 않으면 기본값 사용
        if namespace is None:
            namespace = f"{user_id}_information"

        logger.info(
            f"🔍 디버그 검색 - user: {user_id}, query: {query}, namespace: {namespace}"
        )

        # 쿼리 확장
        expanded_queries = await expand_info_query(query)
        logger.info(f"🔍 확장된 쿼리: {expanded_queries}")

        loop = asyncio.get_event_loop()

        # 여러 방법으로 검색 시도
        all_results = []

        # 1. 확장된 쿼리로 검색
        logger.info(f"🎯 확장된 쿼리로 검색 시도...")
        result1 = await loop.run_in_executor(
            executor,
            search_similar_items_enhanced_optimized,
            user_id,
            expanded_queries,
            "info",
            20,
        )
        all_results.extend(result1)
        logger.info(f"✅ 확장 쿼리 결과: {len(result1)}개")

        # 2. 원본 쿼리로만 검색
        logger.info(f"🎯 원본 쿼리로 검색 시도...")
        result2 = await loop.run_in_executor(
            executor,
            search_similar_items_enhanced_optimized,
            user_id,
            [query],
            "info",
            20,
        )
        all_results.extend(result2)
        logger.info(f"✅ 원본 쿼리 결과: {len(result2)}개")

        # 3. 단순 키워드로 검색
        keywords = query.split()
        logger.info(f"🎯 키워드로 검색 시도: {keywords}")
        result3 = await loop.run_in_executor(
            executor,
            search_similar_items_enhanced_optimized,
            user_id,
            keywords,
            "info",
            20,
        )
        all_results.extend(result3)
        logger.info(f"✅ 키워드 결과: {len(result3)}개")

        # 중복 제거
        unique_results = []
        seen_texts = set()
        for result in all_results:
            text = result.get("text", "")
            if text and text not in seen_texts:
                seen_texts.add(text)
                unique_results.append(result)

        return {
            "query": query,
            "expanded_queries": expanded_queries,
            "namespace": namespace,
            "results_count": len(unique_results),
            "results_breakdown": {
                "expanded_queries_count": len(result1),
                "original_query_count": len(result2),
                "keywords_count": len(result3),
            },
            "results": [
                {
                    "text": result.get("text", "")[:200] + "...",
                    "score": result.get("score", 0),
                    "metadata": result.get("metadata", {}),
                }
                for result in unique_results[:5]
            ],
            "debug_info": {
                "user_id": user_id,
                "namespace_used": namespace,
                "keywords_tried": keywords,
            },
        }
    except Exception as e:
        logger.error(f"❌ 디버그 검색 오류: {str(e)}", exc_info=True)
        return {
            "error": str(e),
            "query": query,
            "namespace": namespace,
            "traceback": traceback.format_exc(),
        }
