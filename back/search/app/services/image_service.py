import asyncio
import logging
import time
import json
from typing import List, Dict
from app.utils.ai_utils import determine_image_query_intent, extract_photo_keywords
from app.utils.db_utils import search_photos_by_keywords
from app.utils.async_utils import save_query_async, save_result_async
from app.config.settings import MAX_DISPLAY_RESULTS

logger = logging.getLogger(__name__)


async def process_photo_search(user_id: str, query: str, timings: Dict) -> Dict:
    """사진 검색 처리"""
    # 키워드 추출
    keyword_start = time.time()
    keywords = await extract_photo_keywords(query)
    timings["keyword_extraction"] = time.time() - keyword_start
    logger.info(
        f"🔍 추출된 키워드 ({len(keywords)}개): {keywords[:10]} ({timings['keyword_extraction']:.3f}초)"
    )

    # DB에서 사진 검색
    db_search_start = time.time()
    photo_results = await search_photos_by_keywords(user_id, keywords)
    timings["db_search"] = time.time() - db_search_start

    # photo_ids만 추출
    photo_ids = [photo["access_id"] for photo in photo_results]

    logger.info(f"📷 검색된 사진: {len(photo_ids)}개 ({timings['db_search']:.3f}초)")

    # 결과 구성
    return {
        "type": "photo_search",
        "query": query,
        "keywords": keywords,
        "photo_ids": photo_ids,
        "photo_details": photo_results[:MAX_DISPLAY_RESULTS],  # 상위 N개 상세 정보
        "answer": "",
        "count": len(photo_ids),
        "_timings": timings,
    }
