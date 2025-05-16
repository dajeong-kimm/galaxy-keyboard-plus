import asyncio
import logging
import time
import json
from typing import List, Dict
from app.utils.ai_utils import expand_info_query, generate_info_answer
from app.utils.semantic_search import search_similar_items_enhanced_optimized
from app.config.settings import MAX_CONTEXT_ITEMS
from concurrent.futures import ThreadPoolExecutor

logger = logging.getLogger(__name__)
executor = ThreadPoolExecutor(max_workers=10)


async def process_info_search(user_id: str, query: str, timings: Dict) -> Dict:
    """정보 검색 처리"""
    # 쿼리 확장
    query_expand_start = time.time()
    expanded_queries = await expand_info_query(query)
    timings["query_expansion"] = time.time() - query_expand_start
    logger.info(f"🔍 확장된 쿼리: {expanded_queries}")

    # 벡터 검색으로 관련 정보 찾기
    vector_search_start = time.time()
    namespace = f"{user_id}_information"
    loop = asyncio.get_event_loop()

    logger.info(
        f"🔍 벡터 검색 시작 - namespace: {namespace}, queries: {expanded_queries}"
    )

    # 검색 로직
    context_info = await perform_vector_search(user_id, expanded_queries, query)

    timings["vector_search"] = time.time() - vector_search_start
    logger.info(
        f"📚 검색된 정보: {len(context_info)}개 ({timings['vector_search']:.3f}초)"
    )

    # 답변 생성
    answer_start = time.time()
    answer = await generate_info_answer(user_id, query, context_info)
    timings["answer_generation"] = time.time() - answer_start
    logger.info(f"✍️ 답변 생성 완료 ({timings['answer_generation']:.3f}초)")

    # 결과 구성
    return {
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


async def perform_vector_search(
    user_id: str, expanded_queries: List[str], original_query: str
) -> List[Dict]:
    """벡터 검색 수행"""
    context_info = []
    loop = asyncio.get_event_loop()

    try:
        # 1. 확장된 쿼리로 검색
        result1 = await loop.run_in_executor(
            executor,
            search_similar_items_enhanced_optimized,
            user_id,
            expanded_queries,
            "information",
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
                [original_query],
                "information",
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

        return unique_results[:MAX_CONTEXT_ITEMS]

    except Exception as e:
        logger.error(f"❌ 벡터 검색 실패: {str(e)}", exc_info=True)
        return []
