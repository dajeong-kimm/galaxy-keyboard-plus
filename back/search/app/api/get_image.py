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
from app.services.conversation_service import process_conversation
from app.config.settings import CACHE_TTL_SECONDS, MAX_CACHE_SIZE

router = APIRouter()
logger = logging.getLogger(__name__)


@router.post("/image/")
async def process_image_query(user_id: str = Form(...), query: str = Form(...)):
    """
    사용자의 자연어 질의에 따라 이미지를 검색하거나,
    벡터 정보 검색 또는 일반 대화를 처리하는 메인 API 엔드포인트
    """
    total_start = time.time()
    logger.info(f"🔍 쿼리 시작 - user: {user_id}, query: {query}")

    # 캐시 키는 생성하지만 실제 캐싱은 비활성화
    cache_key = get_cache_key(user_id, query)
    # cached_result = get_from_cache(cache_key, CACHE_TTL_SECONDS)
    # if cached_result:
    #     cached_result["_timings"]["total"] = time.time() - total_start
    #     cached_result["_from_cache"] = True
    #     return cached_result

    timestamp = int(time.time())
    timings = {}

    try:
        # 1. 사용자 쿼리 비동기 저장
        asyncio.create_task(save_query_async(user_id, "user", query, timestamp))

        # 2. 쿼리 의도 파악: 사진 검색, 정보 요청, 일반 대화 중 하나
        intent_start = time.time()
        intent = await determine_image_query_intent(query)
        timings["intent_detection"] = time.time() - intent_start
        logger.info(f"🎯 의도 파악: {intent} ({timings['intent_detection']:.3f}초)")

        # 3. 의도별 처리 분기
        if intent == "find_photo":
            # 📸 사용자 질문이 이미지 관련일 경우 → 이미지 검색
            result = await process_photo_search(user_id, query, timings)
        elif intent == "conversation":
            # 💬 일반 대화일 경우 → LLM 기반 응답 생성
            result = await process_conversation(user_id, query, timings)
        else:
            # 📚 정보 검색일 경우 → 쿼리 확장 + 벡터 검색 + 답변 생성
            result = await process_info_search(user_id, query, timings)

        # 4. 전체 처리 시간 저장
        timings["total"] = time.time() - total_start
        result["_timings"] = timings
        result["_from_cache"] = False  # 캐싱 비활성화됨 - 항상 false

        # 5. 결과 캐시 저장 비활성화
        # set_cache(cache_key, result, CACHE_TTL_SECONDS, MAX_CACHE_SIZE)

        # 6. 비동기 결과 저장
        asyncio.create_task(
            save_result_async(
                user_id,
                "assistant",
                json.dumps(result, ensure_ascii=False),
                int(time.time()),
            )
        )

        # 7. 성능 로그 출력
        log_performance_summary(intent, timings)

        return result

    except Exception as e:
        # 예외 처리 및 로그 출력
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
    if intent == "conversation":
        logger.info(
            f"""
⏱️ API 성능 요약 (대화):
- 의도 파악: {timings['intent_detection']:.3f}초
- 대화 응답 생성: {timings.get('conversation_response', 0):.3f}초
- 전체 시간: {timings['total']:.3f}초
            """
        )
    else:
        logger.info(
            f"""
⏱️ API 성능 요약:
- 의도 파악: {timings['intent_detection']:.3f}초
- {'키워드 추출' if intent == 'find_photo' else '쿼리 확장'}: {timings.get('keyword_extraction', timings.get('query_expansion', 0)):.3f}초
- {'DB 검색' if intent == 'find_photo' else '벡터 검색'}: {timings.get('db_search', timings.get('vector_search', 0)):.3f}초
- {'DB 검색' if intent == 'find_photo' else '답변 생성'}: {timings.get('db_search', timings.get('answer_generation', 0)):.3f}초
- 전체 시간: {timings['total']:.3f}초
            """
        )
