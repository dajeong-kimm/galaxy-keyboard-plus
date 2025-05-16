from fastapi import APIRouter, Form
from typing import Optional, Dict
import logging
import time
import json
import asyncio
import traceback
from datetime import datetime
from app.utils.ai_utils import determine_image_query_intent
from app.utils.async_utils import save_query_async, save_result_async
from app.utils.cache_utils import (
    get_cache_key,
    get_from_cache,
    set_cache,
    get_cache_status,
    clear_cache,
)
from app.services.image_service import process_photo_search
from app.services.info_service import process_info_search
from app.config.settings import CACHE_TTL_SECONDS, MAX_CACHE_SIZE

router = APIRouter()
logger = logging.getLogger(__name__)


@router.post("/image/")
async def process_image_query(user_id: str = Form(...), query: str = Form(...)):
    """이미지 관련 질문 처리 API"""
    total_start = time.time()

    logger.info(f"🔍 이미지 쿼리 시작 - user: {user_id}, query: {query}")

    # 캐시 확인
    cache_key = get_cache_key(user_id, query)
    cached_result = get_from_cache(cache_key, CACHE_TTL_SECONDS)
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

        # 3. 의도에 따른 처리
        if intent == "find_photo":
            # 사진 찾기 로직
            result = await process_photo_search(user_id, query, timings)
        else:  # get_info
            # 정보 찾기 로직
            result = await process_info_search(user_id, query, timings)

        # 전체 시간
        timings["total"] = time.time() - total_start
        result["_timings"] = timings
        result["_from_cache"] = False

        # 캐시에 저장
        set_cache(cache_key, result, CACHE_TTL_SECONDS, MAX_CACHE_SIZE)

        # 결과 저장 (비동기)
        asyncio.create_task(
            save_result_async(
                user_id,
                "assistant",
                json.dumps(result, ensure_ascii=False),
                int(time.time()),
            )
        )

        # 성능 로깅
        log_performance_summary(intent, timings)

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


def log_performance_summary(intent: str, timings: Dict):
    """성능 요약 로깅"""
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


# 캐시 관리 엔드포인트
@router.get("/image/cache/status")
async def get_cache_status_endpoint():
    """캐시 상태 확인"""
    status = get_cache_status()
    status["max_size"] = MAX_CACHE_SIZE
    status["ttl_seconds"] = CACHE_TTL_SECONDS
    return status


@router.delete("/image/cache/clear")
async def clear_cache_endpoint():
    """캐시 초기화"""
    cleared_items = clear_cache()
    return {"cleared_items": cleared_items}
