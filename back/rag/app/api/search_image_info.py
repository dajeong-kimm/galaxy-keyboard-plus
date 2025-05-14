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

router = APIRouter()
logger = logging.getLogger(__name__)

# ThreadPoolExecutor 워커 수 증가
executor = ThreadPoolExecutor(max_workers=20)  # 기존 5에서 20으로 증가


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


@router.post("/search/")
async def search(
    user_id: str = Form(...),
    query: str = Form(...),
    top_k_photo: Optional[int] = Form(5),
    top_k_info: Optional[int] = Form(5),
):
    # 전체 시작 시간
    total_start = time.time()
    timestamp = int(time.time())
    loop = asyncio.get_event_loop()

    # 각 단계별 시간 기록용 딕셔너리
    timings = {}

    try:
        # 1. 사용자 쿼리 저장 (완전 비동기)
        asyncio.create_task(
            _save_query_async(user_id, "user", query, timestamp)
        )

        # 2. 쿼리 확장
        expand_start = time.time()
        expanded_queries = await loop.run_in_executor(
            executor, enhance_query_with_personal_context_v2, user_id, query
        )
        timings["query_expansion"] = time.time() - expand_start
        logger.info(f"⏱️ 쿼리 확장: {timings['query_expansion']:.3f}초")
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
        
        # 7. 결과 저장 (응답 후 비동기로 처리)
        serialized_result = json.dumps(result, ensure_ascii=False)
        asyncio.create_task(
            _save_result_async(user_id, "assistant", serialized_result, int(time.time()))
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
        raise

    finally:
        pass


# 애플리케이션 종료 시에만 정리
@router.on_event("shutdown")
async def shutdown_event():
    executor.shutdown(wait=True)
