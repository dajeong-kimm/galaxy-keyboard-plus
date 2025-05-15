from fastapi import APIRouter, Form, HTTPException
from typing import Optional, List
from app.utils.semantic_search import (
    enhance_query_with_personal_context_v2,
    search_similar_items_enhanced_optimized,
    filter_relevant_items_with_context,
)
import time
import asyncio
import logging
from concurrent.futures import ThreadPoolExecutor
from pydantic import BaseModel

router = APIRouter(prefix="/search", tags=["search"])
logger = logging.getLogger(__name__)

# ThreadPoolExecutor 워커 수
executor = ThreadPoolExecutor(max_workers=10)


# Response models
class PhotoSearchResult(BaseModel):
    id: str
    text: str
    score: float


class InfoSearchResult(BaseModel):
    id: str
    text: str
    score: float
    related_photos: List[PhotoSearchResult] = []


class PhotoSearchResponse(BaseModel):
    photos: List[PhotoSearchResult]
    query: str
    expanded_queries: List[str]
    total_time: float


class InfoSearchResponse(BaseModel):
    info: List[InfoSearchResult]
    query: str
    expanded_queries: List[str]
    total_time: float


@router.post("/photo", response_model=PhotoSearchResponse)
async def search_photos(
    user_id: str = Form(...),
    query: str = Form(...),
    top_k: Optional[int] = Form(5),
):
    """
    사진 벡터에서 검색하여 관련 사진들을 반환
    - 사진 벡터에서만 검색
    - 쿼리 확장을 통한 정확도 향상
    """
    start_time = time.time()
    loop = asyncio.get_event_loop()

    try:
        # 1. 쿼리 확장 (색상 및 객체 분석 포함)
        expand_start = time.time()
        expanded_queries = await loop.run_in_executor(
            executor, enhance_query_with_personal_context_v2, user_id, query
        )
        logger.info(f"⏱️ 쿼리 확장: {time.time() - expand_start:.3f}초")
        logger.info(f"🔍 확장된 쿼리: {expanded_queries[:3]}")

        # 2. 사진 벡터에서 검색
        search_start = time.time()
        raw_photo_results = await loop.run_in_executor(
            executor,
            search_similar_items_enhanced_optimized,
            user_id,
            expanded_queries,
            "photo",  # photo namespace만 검색
            top_k * 2,  # 필터링을 위해 더 많이 가져오기
        )
        logger.info(f"⏱️ 벡터 검색: {time.time() - search_start:.3f}초")
        logger.info(f"📷 검색된 사진 수: {len(raw_photo_results)}")

        # 3. 결과 필터링 및 정렬
        filter_start = time.time()
        filtered_results = await loop.run_in_executor(
            executor,
            filter_relevant_items_with_context,
            query,
            "",  # expanded_query는 사용하지 않음
            raw_photo_results,
            "사진",
        )
        logger.info(f"⏱️ 결과 필터링: {time.time() - filter_start:.3f}초")

        # 4. 결과 정리
        photo_results = []
        for item in filtered_results[:top_k]:
            photo_results.append(
                PhotoSearchResult(
                    id=item.get("id", "unknown"),
                    text=item.get("text", ""),
                    score=item.get("adjusted_score", item.get("score", 0)),
                )
            )

        total_time = time.time() - start_time
        logger.info(f"⏱️ 전체 검색 시간: {total_time:.3f}초")

        return PhotoSearchResponse(
            photos=photo_results,
            query=query,
            expanded_queries=expanded_queries[:5],
            total_time=total_time,
        )

    except Exception as e:
        logger.error(f"Photo search error: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/info", response_model=InfoSearchResponse)
async def search_info(
    user_id: str = Form(...),
    query: str = Form(...),
    top_k: Optional[int] = Form(5),
    include_related_photos: Optional[bool] = Form(True),
):
    """
    정보 벡터에서 검색하여 관련 정보와 근거 사진 반환
    - 정보 벡터에서 검색
    - 각 정보에 대한 관련 사진도 함께 반환 (옵션)
    """
    start_time = time.time()
    loop = asyncio.get_event_loop()

    try:
        # 1. 쿼리 확장
        expand_start = time.time()
        expanded_queries = await loop.run_in_executor(
            executor, enhance_query_with_personal_context_v2, user_id, query
        )
        logger.info(f"⏱️ 쿼리 확장: {time.time() - expand_start:.3f}초")
        logger.info(f"🔍 확장된 쿼리: {expanded_queries[:3]}")

        # 2. 정보 벡터에서 검색
        search_start = time.time()
        raw_info_results = await loop.run_in_executor(
            executor,
            search_similar_items_enhanced_optimized,
            user_id,
            expanded_queries,
            "info",  # info namespace만 검색
            top_k * 2,
        )
        logger.info(f"⏱️ 정보 검색: {time.time() - search_start:.3f}초")
        logger.info(f"📄 검색된 정보 수: {len(raw_info_results)}")

        # 3. 결과 필터링
        filter_start = time.time()
        filtered_info = await loop.run_in_executor(
            executor,
            filter_relevant_items_with_context,
            query,
            "",
            raw_info_results,
            "정보",
        )
        logger.info(f"⏱️ 정보 필터링: {time.time() - filter_start:.3f}초")

        # 4. 각 정보에 대한 관련 사진 찾기 (옵션)
        info_results = []

        if include_related_photos:
            # 병렬로 각 정보에 대한 관련 사진 검색
            photo_search_tasks = []
            for info_item in filtered_info[:top_k]:
                # 정보 텍스트를 기반으로 관련 사진 검색
                task = loop.run_in_executor(
                    executor,
                    search_similar_items_enhanced_optimized,
                    user_id,
                    [info_item.get("text", "")[:100]],  # 정보 텍스트의 일부를 쿼리로
                    "photo",
                    3,  # 각 정보당 3개의 관련 사진
                )
                photo_search_tasks.append(task)

            # 모든 사진 검색 결과 수집
            if photo_search_tasks:
                related_photos_results = await asyncio.gather(*photo_search_tasks)
            else:
                related_photos_results = []

            # 결과 정리
            for i, info_item in enumerate(filtered_info[:top_k]):
                related_photos = []
                if i < len(related_photos_results):
                    for photo in related_photos_results[i][:3]:
                        related_photos.append(
                            PhotoSearchResult(
                                id=photo.get("id", "unknown"),
                                text=photo.get("text", ""),
                                score=photo.get("score", 0),
                            )
                        )

                info_results.append(
                    InfoSearchResult(
                        id=info_item.get("id", "unknown"),
                        text=info_item.get("text", ""),
                        score=info_item.get(
                            "adjusted_score", info_item.get("score", 0)
                        ),
                        related_photos=related_photos,
                    )
                )
        else:
            # 관련 사진 없이 정보만 반환
            for info_item in filtered_info[:top_k]:
                info_results.append(
                    InfoSearchResult(
                        id=info_item.get("id", "unknown"),
                        text=info_item.get("text", ""),
                        score=info_item.get(
                            "adjusted_score", info_item.get("score", 0)
                        ),
                        related_photos=[],
                    )
                )

        total_time = time.time() - start_time
        logger.info(f"⏱️ 전체 검색 시간: {total_time:.3f}초")

        return InfoSearchResponse(
            info=info_results,
            query=query,
            expanded_queries=expanded_queries[:5],
            total_time=total_time,
        )

    except Exception as e:
        logger.error(f"Info search error: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


# 애플리케이션 종료 시에만 정리
@router.on_event("shutdown")
async def shutdown_event():
    executor.shutdown(wait=True)
