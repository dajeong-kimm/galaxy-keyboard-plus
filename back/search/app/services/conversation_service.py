import asyncio
import logging
import time
import json
from typing import Dict, List
from app.utils.ai_utils import generate_conversation_response
from app.utils.async_utils import save_query_async, save_result_async
from app.utils.chat_vector_store import search_chat_history
from app.utils.context_helpers import check_if_requires_context, generate_contextualized_conversation_response

logger = logging.getLogger(__name__)


def check_if_requires_context(query: str) -> bool:
    """질문이 맥락이 필요한지 겄단히 확인"""
    # 사용자 정보나 이전 대화를 참조하는 키워드
    context_keywords = [
        # 이전 대화 참조
        "이전", "아까", "방금", "그때", "다시", "그거", "그것", "저번에", "어제", "지난", "전에",
        # 개인 선호도 참조
        "좋아하", "좋아해", "좋아", "좋은", "선호하", "선호도", "좋아하는", "우리", "우리는", "우리가", "우리의",
        # 소유를 나타내는 표현
        "내", "나의", "나는", "내가", "내가 좋아하는", "내 좋아하는", "내가 선호하는", "내 선호하는",
        # 관심사/취미 관련
        "취미", "취미는", "취미가", "관심사", "관심", "관심있", "여가", "여가는", "여가가",
        # 음식/새우식품 관련
        "음식", "음식은", "음식을", "음식이", "음식은", "먹을", "먹는", "즐겨먹", "즐겨",
    ]
    
    # 사용자 정보나 이전 대화에 대한 질문 여부
    query_lower = query.lower()
    return any(keyword in query_lower for keyword in context_keywords)


async def process_conversation(user_id: str, query: str, timings: Dict) -> Dict:
    """대화형 메시지 처리 - 사용자의 이전 대화 기록 활용"""
    
    # 대화 맥락을 필요로 하는지 분석
    requires_context = check_if_requires_context(query)
    chat_history = []
    
    if requires_context:
        # 이전 대화 기록 검색
        context_start = time.time()
        chat_history = search_chat_history(user_id, query, top_k=5)
        
        # 이전 대화를 시간순으로 정렬
        chat_history.sort(key=lambda x: x.get("timestamp", 0))
        
        timings["context_retrieval"] = time.time() - context_start
        logger.info(f"🔍 이전 대화 검색: {len(chat_history)}개 ({timings['context_retrieval']:.3f}초)")
    
    # 대화형 응답 생성
    response_start = time.time()
    
    if chat_history:
        # 맥락이 있는 경우 - 새로운 함수 사용
        response, used_history = await generate_contextualized_conversation_response(user_id, query, chat_history)
        logger.info(f"💬 맥락 기반 대화 응답 생성 (사용된 기록: {len(used_history)}개)")
    else:
        # 맥락이 없는 경우 - 기존 함수 사용
        response = await generate_conversation_response(user_id, query)
        used_history = []
        logger.info("💬 일반 대화 응답 생성")
        
    timings["conversation_response"] = time.time() - response_start
    logger.info(f"💬 대화 응답 생성 완료 ({timings['conversation_response']:.3f}초)")

    # 결과 구성
    return {
        "type": "conversation",
        "query": query,
        "answer": response,
        "context_used": len(used_history) > 0,  # 맥락 사용 여부
        "photo_ids": [],  # 빈 배열 유지
        "_timings": timings,
        "_debug": {
            "context_count": len(chat_history),
            "history_used": used_history,
        } if chat_history else {},
    }
