import os
import re
from dotenv import load_dotenv
from app.utils.embedding import get_text_embedding
from pinecone import Pinecone
from openai import OpenAI
from app.utils.chat_vector_store import search_chat_history
import json

load_dotenv()

pc = Pinecone(api_key=os.getenv("PINECONE_API_KEY"))
index = pc.Index(os.getenv("PINECONE_INDEX_NAME"))

openai = OpenAI(api_key=os.getenv("OPENAI_API_KEY_2"))


def determine_query_intent(query: str) -> str:
    """질문 의도 파악 - 사진 검색 판별 강화"""

    # 사진 검색 키워드 확장
    photo_keywords = [
        "사진",
        "찾아",
        "보여",
        "이미지",
        "찾아줘",
        "있나",
        "있어",
        "어디",
        "찍은",
        "촬영",
        "찰칵",
        "어딨",
        "보고싶",
        "볼래",
    ]

    # 색상 키워드 (사진 검색의 강력한 지표)
    color_keywords = [
        "빨간",
        "파란",
        "노란",
        "초록",
        "검은",
        "하얀",
        "분홍",
        "보라",
        "주황",
        "회색",
        "갈색",
        "금색",
        "은색",
        "하늘색",
        "연두색",
    ]

    # 객체/물건 키워드 (사진에 자주 등장하는 대상) - 에어컨 추가
    object_keywords = [
        "인형",
        "장난감",
        "물건",
        "옷",
        "차",
        "집",
        "동물",
        "꽃",
        "나무",
        "가방",
        "신발",
        "모자",
        "시계",
        "책",
        "음식",
        "케이크",
        "선물",
        "에어컨",
        "에어컨디셔너",
        "냉방기",
        "냉방",
    ]

    # 정보 요청 키워드 (명확히 정보를 원하는 경우)
    info_keywords = [
        "설명",
        "무엇",
        "뜻",
        "의미",
        "정의",
        "역사",
        "유래",
        "방법",
        "이유",
        "어떻게",
    ]

    query_lower = query.lower()

    # 색상 + 객체 조합은 거의 확실하게 사진 검색
    has_color = any(color in query_lower for color in color_keywords)
    has_object = any(obj in query_lower for obj in object_keywords)

    if has_color and has_object:
        return "photo_search"

    # 색상만 있어도 높은 확률로 사진 검색
    if has_color:
        return "photo_search"

    # 객체만 있어도 사진 검색 (like 에어컨)
    if has_object:
        return "photo_search"

    # 사진 키워드가 있으면 사진 검색
    if any(keyword in query_lower for keyword in photo_keywords):
        return "photo_search"

    # 정보 키워드만 있고 다른 키워드가 없으면 정보 검색
    if any(keyword in query_lower for keyword in info_keywords) and not has_object:
        return "info_request"

    # 기본값은 사진 검색으로 (대부분의 경우 사진 검색을 원함)
    return "photo_search"


def enhance_query_with_personal_context_v2(user_id: str, query: str) -> list[str]:
    """개선된 쿼리 확장 - 색상과 객체 분리 및 다양한 조합 생성"""

    # 1. 색상과 객체 분리 추출
    color_keywords = {
        "빨간": ["빨간", "빨강", "붉은", "레드", "적색"],
        "파란": ["파란", "파랑", "푸른", "블루", "청색", "하늘색"],
        "노란": ["노란", "노랑", "옐로우", "황색", "금색"],
        "초록": ["초록", "녹색", "그린", "연두"],
        "검은": ["검은", "검정", "블랙", "까만"],
        "하얀": ["하얀", "하양", "흰", "화이트", "백색"],
        "분홍": ["분홍", "핑크", "연분홍", "분홍색"],
    }

    object_keywords = {
        "인형": ["인형", "장난감", "토이", "곰돌이", "테디베어", "봉제인형"],
        "드레스": ["드레스", "원피스", "의상", "옷", "치마"],
        "차": ["차", "자동차", "카", "차량"],
        "꽃": ["꽃", "플라워", "꽃다발", "화초"],
        "케이크": ["케이크", "케잌", "생일케이크", "디저트"],
        "가방": ["가방", "백", "핸드백", "배낭"],
        "에어컨": ["에어컨", "에어컨디셔너", "냉방기", "냉방", "튵바이튵"],
    }

    # 쿼리 분석
    found_colors = []
    found_objects = []
    found_color_keys = []
    found_object_keys = []

    query_words = query.lower().split()

    # 색상 찾기
    for color_key, variations in color_keywords.items():
        for variation in variations:
            if variation in query.lower():
                found_colors.append(variation)
                found_color_keys.append(color_key)
                break

    # 객체 찾기
    for object_key, variations in object_keywords.items():
        for variation in variations:
            if variation in query.lower():
                found_objects.append(variation)
                found_object_keys.append(object_key)
                break

    # 객체가 명시되지 않은 경우 추가 분석
    if not found_objects:
        for word in query_words:
            if len(word) >= 2 and word not in [
                "사진",
                "찾아",
                "보여",
                "있나",
                "있어",
                "줘",
                "주세요",
            ]:
                if word not in [v for vars in color_keywords.values() for v in vars]:
                    found_objects.append(word)

    # 2. 쿼리 의도 파악
    query_intent = determine_query_intent(query)
    is_photo_search = query_intent == "photo_search"

    # 3. 기본 확장 쿼리 생성 (색상과 객체의 다양한 조합)
    base_queries = [query]  # 원본 쿼리는 항상 포함

    if found_colors and found_objects:
        # 색상과 객체가 모두 있는 경우 - 다양한 조합 생성
        for color in found_colors:
            for obj in found_objects:
                base_queries.extend(
                    [
                        f"{color} {obj}",
                        f"{color}색 {obj}",
                        f"{color} {obj} 사진",
                        f"{obj} {color}",
                    ]
                )

        # 색상 변형 추가
        for color_key in found_color_keys:
            for variation in color_keywords[color_key][:3]:  # 상위 3개 변형만
                base_queries.append(f"{variation} {found_objects[0]}")

        # 객체 변형 추가
        for object_key in found_object_keys:
            for variation in object_keywords[object_key][:3]:  # 상위 3개 변형만
                base_queries.append(f"{found_colors[0]} {variation}")

        # 각 요소만으로도 검색
        base_queries.extend(found_objects)
        base_queries.extend([f"{color} 색상" for color in found_colors])

    elif found_colors:
        # 색상만 있는 경우
        for color in found_colors:
            base_queries.extend(
                [
                    f"{color} 사진",
                    f"{color}색",
                    f"{color} 색상",
                    f"{color} 물건",
                    f"{color} 있는 사진",
                ]
            )

    elif found_objects:
        # 객체만 있는 경우
        for obj in found_objects:
            base_queries.extend(
                [
                    f"{obj} 사진",
                    f"{obj} 있는 사진",
                    f"{obj} 찾아줘",
                    obj,
                ]
            )

    # 4. LLM을 통한 추가 확장
    visual_analysis = f"""
    색상: {', '.join(found_colors) if found_colors else '없음'}
    객체: {', '.join(found_objects) if found_objects else '없음'}
    """

    if is_photo_search:
        prompt = f"""
        사용자가 "{query}"라고 검색했습니다.
        
        분석 결과:
        {visual_analysis}
        
        이 검색어와 관련된 다양한 사진 검색 쿼리를 생성해주세요:
        1. 색상과 객체의 다양한 조합
        2. 동의어나 유사어 사용 (예: 인형 → 장난감, 봉제인형)
        3. 구체적인 설명 추가 (예: 파란색 인형 → 하늘색 테디베어)
        4. 관련 상황이나 맥락 추가
        
        5-7개의 검색 쿼리를 JSON 리스트로 반환해주세요.
        색상과 객체가 있다면 반드시 둘을 조합한 쿼리도 포함해주세요.
        """
    else:
        prompt = f"""
        다음 사용자 질문과 유사한 의미의 정보 검색 쿼리 3-5개를 생성해주세요.
        사용자 질문: "{query}"
        
        JSON 리스트 형태로 반환해주세요.
        """

    response = openai.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": prompt}],
        temperature=0.7,
        max_tokens=300,
    )

    try:
        llm_queries = json.loads(response.choices[0].message.content.strip())

        # 중복 제거 및 결합
        all_queries = base_queries + llm_queries
        seen = set()
        result = []

        for q in all_queries:
            q_normalized = q.strip().lower()
            if q_normalized not in seen and q.strip():
                seen.add(q_normalized)
                result.append(q.strip())

        # 원본 쿼리를 첫 번째로 유지
        if query in result:
            result.remove(query)
        result.insert(0, query)

        return result[:10]  # 최대 10개 반환

    except Exception as e:
        print(f"LLM 쿼리 파싱 오류: {e}")
        # 폴백: 기본 확장만 반환
        seen = set()
        result = []
        for q in base_queries:
            q_normalized = q.strip().lower()
            if q_normalized not in seen and q.strip():
                seen.add(q_normalized)
                result.append(q.strip())
        return result[:7]


def search_similar_items_enhanced(
    user_id: str, queries: list[str], target: str, top_k: int = 5
) -> list[dict]:
    """향상된 벡터 검색 - 여러 쿼리로 검색 후 병합"""
    # 실제로는 optimized 버전을 사용하지만 호환성을 위해 유지
    return search_similar_items_enhanced_optimized(user_id, queries, target, top_k)


def search_similar_items_enhanced_optimized(
    user_id: str, queries: list[str], target: str, top_k: int = 5
) -> list[dict]:
    """최적화된 벡터 검색 - 임베딩 재사용 및 배치 처리"""
    namespace = f"{user_id}_{target}"

    # 디버깅을 위한 로깅 추가
    print(f"🔍 검색 중: namespace={namespace}, queries={queries[:3]}, target={target}")

    # 1. 쿼리 수 조정 (색상+객체 조합은 더 많은 쿼리 사용)
    max_queries = 5 if len(queries) > 5 else len(queries)
    all_texts = queries[:max_queries]

    # 2. 배치 임베딩 생성
    response = openai.embeddings.create(model="text-embedding-ada-002", input=all_texts)
    vectors = [item.embedding for item in response.data]

    # 3. 병렬 Pinecone 쿼리
    all_results = {}
    import concurrent.futures

    with concurrent.futures.ThreadPoolExecutor(max_workers=max_queries) as executor:
        futures = []
        for i, vector in enumerate(vectors):
            future = executor.submit(
                index.query,
                vector=vector,
                namespace=namespace,
                top_k=top_k * 2,  # 더 많은 후보 확보
                include_metadata=True,
            )
            futures.append((i, future))

        # 결과 수집 및 병합
        for i, future in futures:
            response = future.result()
            print(f"✅ 쿼리 {i} 결과: {len(response['matches'])}개 매치")

            for match in response["matches"]:
                match_id = match["id"]
                # 높은 점수로 업데이트
                if (
                    match_id not in all_results
                    or match["score"] > all_results[match_id]["score"]
                ):
                    all_results[match_id] = match

    print(f"📊 총 수집된 결과: {len(all_results)}개")

    # 4. 결과 정렬 및 반환
    sorted_matches = sorted(
        all_results.values(), key=lambda x: x["score"], reverse=True
    )[:top_k]

    results = []
    for match in sorted_matches:
        full_text = match["metadata"].get("text", "")
        if ": " in full_text:
            image_id, description = full_text.split(": ", 1)
        else:
            image_id, description = "unknown", full_text

        image_id = re.sub(r"\s*\([^)]*\)", "", image_id).strip()

        results.append(
            {
                "score": round(match["score"], 3),
                "id": image_id,
                "text": description,
            }
        )

    print(f"🎯 최종 반환 결과: {len(results)}개")
    return results


def filter_relevant_items_with_context(
    original_query: str, expanded_query: str, items: list[dict], item_type: str
) -> list[dict]:
    """개선된 필터링 - 색상과 객체 매칭 강화"""

    print(
        f"🎯 필터링 시작: {item_type}, 입력 항목 수: {len(items)}, 쿼리: {original_query}"
    )

    if not items:
        return []

    if len(items) <= 3:
        return items

    # 쿼리 분석
    colors = ["빨간", "파란", "노란", "초록", "검은", "하얀", "분홍", "보라", "주황"]
    objects = [
        "인형",
        "에어컨",
        "드레스",
        "차",
        "꽃",
        "케이크",
        "가방",
        "장난감",
        "옷",
        "동물",
    ]
    found_color = None
    found_object = None

    query_lower = original_query.lower()
    for color in colors:
        if color in query_lower:
            found_color = color
            break

    # 객체 추출 (색상 제외)
    for obj in objects:
        if obj in query_lower:
            found_object = obj
            break

    # 객체가 명시적으로 없는 경우 원본 양식대로
    if not found_object:
        words = original_query.split()
        for word in words:
            if (
                word not in colors
                and len(word) >= 2
                and word not in ["사진", "찾아", "보여", "있나"]
            ):
                found_object = word
                break

    print(f"🎨 찾은 색상: {found_color}, 찾은 객체: {found_object}")

    # 필터링 로직
    filtered_items = []

    for item in items:
        text_lower = item.get("text", "").lower()
        score_boost = 0

        # 색상과 객체 모두 매칭
        if found_color and found_object:
            if found_color in text_lower and found_object in text_lower:
                score_boost = 0.5
                print(f"✅ 색상+객체 매칭: {item['id']}")
            elif found_color in text_lower or found_object in text_lower:
                score_boost = 0.3
                print(f"✔️ 부분 매칭: {item['id']}")
        # 색상만 매칭
        elif found_color and found_color in text_lower:
            score_boost = 0.4
            print(f"🎨 색상 매칭: {item['id']}")
        # 객체만 매칭
        elif found_object and found_object in text_lower:
            score_boost = 0.4
            print(f"📦 객체 매칭: {item['id']}")

        # 점수 조정
        adjusted_score = item.get("score", 0) + score_boost

        # 임계값 이상만 포함 (0.01로 낮춰서 더 많은 결과 허용)
        if adjusted_score >= 0.01:
            item["adjusted_score"] = adjusted_score
            filtered_items.append(item)
        else:
            print(f"❌ 필터링 제외: {item['id']} (score: {adjusted_score})")

    # 조정된 점수로 정렬
    filtered_items.sort(
        key=lambda x: x.get("adjusted_score", x.get("score", 0)), reverse=True
    )

    print(f"🎯 필터링 결과: {len(filtered_items)}개")
    return filtered_items[:7]


def filter_relevant_chat_history(query: str, history: list[dict]) -> list[dict]:
    """현재 질문과 관련된 대화만 필터링"""
    if not history:
        return []

    context_keywords = ["이전에", "아까", "방금", "그때", "다시", "그거", "그것"]
    needs_context = any(keyword in query for keyword in context_keywords)

    if not needs_context:
        return []

    return history[:3]  # 최근 3개만 반환


def generate_answer_by_intent(
    user_id: str,
    query: str,
    info_results: list[dict],
    photo_results: list[dict],
    query_intent: str,
) -> dict:
    """질문 의도에 따라 LLM을 통해 자연스러운 응답 생성"""

    # 검색 결과가 모두 비어있는 경우
    if not photo_results and not info_results:
        answer = f"'{query}'에 대한 관련 정보나 사진을 찾을 수 없었습니다."
        return {
            "answer": answer,
            "photo_results": [],
            "info_results": [],
            "query_intent": query_intent,
        }

    # 결과 정리
    combined_text = []
    has_photo = bool(photo_results)
    has_info = bool(info_results)

    if photo_results:
        combined_text.append("찾은 사진들:")
        for i, item in enumerate(photo_results[:5]):
            combined_text.append(f"{i+1}. {item.get('text', '')[:300]}")

    if info_results:
        combined_text.append("\n관련 정보:")
        for i, item in enumerate(info_results[:3]):
            combined_text.append(f"{i+1}. {item.get('text', '')[:300]}")

    # 사진 검색이고 사진이 있는 경우 특별한 프롬프트
    if query_intent == "photo_search" and has_photo:
        prompt = f"""
사용자가 "{query}"를 요청했습니다.

다음은 검색된 사진들의 설명입니다:
{chr(10).join(combined_text)}

위 사진들을 바탕으로 사용자에게 찾은 사진을 자연스럽게 설명해주세요.
중요: 사진이 있다는 것을 명확히 알리고, 각 사진에 어떤 내용이 담겨 있는지 설명해주세요.
절대 "사진을 찾을 수 없다"고 말하지 마세요.
번호나 ID를 언급하지 말고 자연스럽게 설명하세요.
"""
    else:
        # 기본 프롬프트
        prompt = f"""
사용자 질문: "{query}"

{chr(10).join(combined_text)}

위 내용을 바탕으로 사용자의 질문에 자연스럽게 답변해주세요.
찾은 내용이 없다면 그렇게 알려주고, 있다면 간단히 요약해서 설명해주세요.
"""

    response = openai.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": prompt}],
        max_tokens=300,
        temperature=0.5,
    )

    answer = response.choices[0].message.content.strip()

    return {
        "answer": answer,
        "photo_results": photo_results[:5],
        "info_results": info_results[:5],
        "query_intent": query_intent,
    }


def needs_context(query: str) -> bool:
    """맥락이 필요한 쿼리인지 판단"""
    context_keywords = [
        "이전에",
        "아까",
        "방금",
        "그때",
        "다시",
        "그거",
        "그것",
        "저번에",
        "어제",
        "지난",
    ]
    return any(keyword in query for keyword in context_keywords)
