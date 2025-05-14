from fastapi import APIRouter, Form
from typing import Optional
from app.utils.semantic_search import (
    enhance_query_with_personal_context_v2,
    determine_query_intent,
    search_similar_items_enhanced,
    filter_relevant_items_with_context,
    generate_answer_by_intent,
)
from app.utils.chat_vector_store import save_chat_vector_to_pinecone
import json, time
import asyncio
from concurrent.futures import ThreadPoolExecutor
import logging

router = APIRouter()
logger = logging.getLogger(__name__)


@router.post("/search/")
async def search(
    user_id: str = Form(...),
    query: str = Form(...),
    top_k_photo: Optional[int] = Form(7),
    top_k_info: Optional[int] = Form(7),
):
    timestamp = int(time.time())

    loop = asyncio.get_event_loop()
    executor = ThreadPoolExecutor(max_workers=5)

    try:
        # 1. 사용자 쿼리 저장 (비차단)
        save_task = loop.run_in_executor(
            executor, save_chat_vector_to_pinecone, user_id, "user", query, timestamp
        )

        # 2. 쿼리 확장 및 맥락 추가 → List[str] 리턴
        expanded_queries = await loop.run_in_executor(
            executor, enhance_query_with_personal_context_v2, user_id, query
        )

        logger.info(f"🔍 의미 기반 확장 쿼리 (Top 3): {expanded_queries[:3]}")

        # 3. 질문 의도 파악
        query_intent = determine_query_intent(query)

        # 4. 벡터 검색 (확장된 쿼리 사용)
        info_search_task = loop.run_in_executor(
            executor,
            search_similar_items_enhanced,
            user_id,
            expanded_queries,
            "info",
            top_k_info,
        )

        photo_search_task = loop.run_in_executor(
            executor,
            search_similar_items_enhanced,
            user_id,
            expanded_queries,
            "photo",
            top_k_photo,
        )

        raw_info_results, raw_photo_results = await asyncio.gather(
            info_search_task, photo_search_task
        )

        # 5. LLM 필터링 (원본 질문 사용)
        info_filter_task = loop.run_in_executor(
            executor,
            filter_relevant_items_with_context,
            query,
            "",  # enhanced_query 대신 생략 또는 빈 문자열
            raw_info_results,
            "정보",
        )

        photo_filter_task = loop.run_in_executor(
            executor,
            filter_relevant_items_with_context,
            query,
            "",  # enhanced_query 대신 생략 또는 빈 문자열
            raw_photo_results,
            "사진",
        )

        info_results, photo_results = await asyncio.gather(
            info_filter_task, photo_filter_task
        )

        # 6. 의도에 따른 답변 생성
        result = await loop.run_in_executor(
            executor,
            generate_answer_by_intent,
            user_id,
            query,
            info_results,
            photo_results,
            query_intent,
        )

        # 7. 결과 저장 (비차단)
        serialized_result = json.dumps(result, ensure_ascii=False)
        await loop.run_in_executor(
            executor,
            save_chat_vector_to_pinecone,
            user_id,
            "assistant",
            serialized_result,
            int(time.time()),
        )

        return result

    finally:
        executor.shutdown(wait=False)
