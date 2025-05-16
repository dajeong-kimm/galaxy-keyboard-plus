import asyncio
import logging
import time
import json
from typing import Dict
from app.utils.ai_utils import generate_conversation_response
from app.utils.async_utils import save_query_async, save_result_async

logger = logging.getLogger(__name__)


async def process_conversation(user_id: str, query: str, timings: Dict) -> Dict:
    """대화형 메시지 처리"""
    # 대화형 응답 생성
    response_start = time.time()
    response = await generate_conversation_response(user_id, query)
    timings["conversation_response"] = time.time() - response_start

    logger.info(f"💬 대화 응답 생성 완료 ({timings['conversation_response']:.3f}초)")

    # 결과 구성
    return {
        "type": "conversation",
        "query": query,
        "answer": response,
        "photo_ids": [],  # 빈 배열 유지
        "_timings": timings,
    }
