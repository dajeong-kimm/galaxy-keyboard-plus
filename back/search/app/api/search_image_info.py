from fastapi import APIRouter, Form
from typing import Optional
from app.utils.semantic_search import (
    enhance_query_with_personal_context_v2,
    determine_query_intent,
    search_similar_items_enhanced,
    search_similar_items_enhanced_optimized,
    filter_relevant_items_with_context,
    generate_answer_by_intent,
)
from app.utils.chat_vector_store import save_chat_vector_to_pinecone
import json, time
import asyncio
from concurrent.futures import ThreadPoolExecutor
import logging
import hashlib
from datetime import datetime, timedelta
from typing import Dict, Optional

router = APIRouter()
logger = logging.getLogger(__name__)

# ThreadPoolExecutor 워커 수 증가
executor = ThreadPoolExecutor(max_workers=20)  # 기존 5에서 20으로 증가

# 간단한 메모리 캐시
cache: Dict[str, Dict] = {}
CACHE_TTL_SECONDS = 3600  # 1시간
MAX_CACHE_SIZE = 1000  # 최대 캐시 크기


def get_cache_key(user_id: str, query: str, top_k_photo: int, top_k_info: int) -> str:
    """캐시 키 생성"""
    cache_data = f"answer:{user_id}:{query}:{top_k_photo}:{top_k_info}"
    return hashlib.md5(cache_data.encode()).hexdigest()


def get_from_cache(key: str) -> Optional[Dict]:
    """캐시에서 데이터 가져오기"""
    if key in cache:
        cached_data = cache[key]
        # TTL 확인
        if datetime.now() < cached_data["expires_at"]:
            logger.info(f"✅ 캐시 히트: {key}")
            return cached_data["data"]
        else:
            # 만료된 캐시 삭제
            del cache[key]
            logger.info(f"🗑️ 만료된 캐시 삭제: {key}")
    return None


def set_cache(key: str, data: Dict):
    """캐시에 데이터 저장"""
    # 캐시 크기 제한 - LRU 방식으로 오래된 항목 제거
    if len(cache) >= MAX_CACHE_SIZE:
        # 가장 오래된 항목 찾아서 제거
        oldest_key = min(cache.keys(), key=lambda k: cache[k]["created_at"])
        del cache[oldest_key]
        logger.info(f"🗑️ 캐시 크기 초과로 오래된 항목 제거: {oldest_key}")

    cache[key] = {
        "data": data,
        "created_at": datetime.now(),
        "expires_at": datetime.now() + timedelta(seconds=CACHE_TTL_SECONDS),
    }
    logger.info(f"💾 캐시 저장: {key}")


# 비동기 저장 함수들
async def _save_query_async(user_id: str, role: str, content: str, timestamp: int):
    """Async wrapper for saving query"""
    loop = asyncio.get_event_loop()
    await loop.run_in_executor(
        executor, save_chat_vector_to_pinecone, user_id, role, content, timestamp
    )


async def _save_result_async(user_id: str, role: str, content: str, timestamp: int):
    """Async wrapper for saving result"""
    try:
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(
            executor, save_chat_vector_to_pinecone, user_id, role, content, timestamp
        )
        logger.info(f"✅ 결과 저장 완료: {user_id}")
    except Exception as e:
        logger.error(f"❌ 결과 저장 실패: {user_id} - {str(e)}")


@router.post("/answer/")
async def search(
    user_id: str = Form(...),
    query: str = Form(...),
    top_k_photo: Optional[int] = Form(5),
    top_k_info: Optional[int] = Form(5),
):
    # 전체 시작 시간
    total_start = time.time()

    # 캐시 확인
    cache_key = get_cache_key(user_id, query, top_k_photo, top_k_info)
    cached_result = get_from_cache(cache_key)
    if cached_result:
        cached_result["_timings"]["total"] = time.time() - total_start
        cached_result["_from_cache"] = True
        return cached_result

    timestamp = int(time.time())
    loop = asyncio.get_event_loop()

    # 각 단계별 시간 기록용 딕셔너리
    timings = {}

    try:
        # 1. 사용자 쿼리 저장 (완전 비동기)
        asyncio.create_task(_save_query_async(user_id, "user", query, timestamp))

        # 2. 쿼리 확장
        expand_start = time.time()
        expanded_queries = await loop.run_in_executor(
            executor, enhance_query_with_personal_context_v2, user_id, query
        )
        timings["query_expansion"] = time.time() - expand_start
        logger.info(f"⏱️ 쿼리 확장: {timings['query_expansion']:.3f}초")
        logger.info(f"🔍 의미 기반 확장 쿼리 (전체): {expanded_queries}")
        logger.info(f"🔍 의미 기반 확장 쿼리 (Top 3): {expanded_queries[:3]}")

        # 3. 질문 의도 파악
        intent_start = time.time()
        query_intent = determine_query_intent(query)
        timings["intent_detection"] = time.time() - intent_start
        logger.info(f"⏱️ 의도 파악: {timings['intent_detection']:.3f}초")

        # 4. 벡터 검색 (병렬)
        vector_search_start = time.time()
        info_search_task = loop.run_in_executor(
            executor,
            search_similar_items_enhanced_optimized,
            user_id,
            expanded_queries,
            "info",
            top_k_info,
        )

        photo_search_task = loop.run_in_executor(
            executor,
            search_similar_items_enhanced_optimized,
            user_id,
            expanded_queries,
            "photo",
            top_k_photo,
        )

        raw_info_results, raw_photo_results = await asyncio.gather(
            info_search_task, photo_search_task
        )
        timings["vector_search"] = time.time() - vector_search_start
        logger.info(f"⏱️ 벡터 검색 (병렬): {timings['vector_search']:.3f}초")

        # 5. 결과 필터링 (병렬)
        filter_start = time.time()
        info_filter_task = loop.run_in_executor(
            executor,
            filter_relevant_items_with_context,
            query,
            "",
            raw_info_results,
            "정보",
        )

        photo_filter_task = loop.run_in_executor(
            executor,
            filter_relevant_items_with_context,
            query,
            "",
            raw_photo_results,
            "사진",
        )

        info_results, photo_results = await asyncio.gather(
            info_filter_task, photo_filter_task
        )
        timings["filtering"] = time.time() - filter_start
        logger.info(f"⏱️ 결과 필터링 (병렬): {timings['filtering']:.3f}초")

        # 6. 답변 생성
        answer_start = time.time()
        result = await loop.run_in_executor(
            executor,
            generate_answer_by_intent,
            user_id,
            query,
            info_results,
            photo_results,
            query_intent,
        )
        timings["answer_generation"] = time.time() - answer_start
        logger.info(f"⏱️ 답변 생성: {timings['answer_generation']:.3f}초")

        # 전체 시간 (사용자 응답 시점)
        timings["total"] = time.time() - total_start

        # 결과에 타이밍 정보 포함 (디버깅용)
        result["_timings"] = timings
        result["_from_cache"] = False

        # 캐시에 저장
        set_cache(cache_key, result)

        # 7. 결과 저장 (응답 후 비동기로 처리)
        serialized_result = json.dumps(result, ensure_ascii=False)
        asyncio.create_task(
            _save_result_async(
                user_id, "assistant", serialized_result, int(time.time())
            )
        )

        # 요약 로그
        logger.info(
            f"""
⏱️ 검색 API 성능 요약:
- 쿼리 확장: {timings['query_expansion']:.3f}초
- 의도 파악: {timings['intent_detection']:.3f}초
- 벡터 검색: {timings['vector_search']:.3f}초
- 결과 필터링: {timings['filtering']:.3f}초
- 답변 생성: {timings['answer_generation']:.3f}초
- 전체 시간: {timings['total']:.3f}초 (응답 시점)
        """
        )

        # 응답 즉시 반환
        return result

    except Exception as e:
        logger.error(f"Search error: {str(e)}", exc_info=True)
        timings["error"] = time.time() - total_start
        logger.error(f"⏱️ 에러 발생 시점: {timings['error']:.3f}초")

        from fastapi.responses import JSONResponse

        return JSONResponse(
            status_code=500,
            content={
                "error": "검색 처리 중 오류가 발생했습니다.",
                "detail": str(e),
                "timings": timings,
            },
        )
        raise

    finally:
        pass


# 캐시 관리 엔드포인트
@router.get("/cache/status")
async def get_cache_status():
    """캐시 상태 확인"""
    valid_count = 0
    expired_count = 0
    current_time = datetime.now()

    for key, data in cache.items():
        if current_time < data["expires_at"]:
            valid_count += 1
        else:
            expired_count += 1

    return {
        "total_items": len(cache),
        "valid_items": valid_count,
        "expired_items": expired_count,
        "max_size": MAX_CACHE_SIZE,
        "ttl_seconds": CACHE_TTL_SECONDS,
        "memory_usage_mb": sum(len(str(v).encode("utf-8")) for v in cache.values())
        / (1024 * 1024),
    }


@router.delete("/cache/clear")
async def clear_cache():
    """캐시 초기화"""
    old_size = len(cache)
    cache.clear()
    logger.info(f"🗑️ 캐시 초기화됨: {old_size}개 항목 삭제")
    return {"cleared_items": old_size, "message": "캐시가 초기화되었습니다."}


@router.delete("/cache/expired")
async def clear_expired_cache():
    """만료된 캐시 항목만 삭제"""
    current_time = datetime.now()
    expired_keys = []

    for key, data in cache.items():
        if current_time >= data["expires_at"]:
            expired_keys.append(key)

    for key in expired_keys:
        del cache[key]

    logger.info(f"🗑️ 만료된 캐시 {len(expired_keys)}개 항목 삭제")
    return {
        "deleted_items": len(expired_keys),
        "message": f"{len(expired_keys)}개의 만료된 항목이 삭제되었습니다.",
    }


# 애플리케이션 종료 시에만 정리
@router.on_event("shutdown")
async def shutdown_event():
    executor.shutdown(wait=True)
