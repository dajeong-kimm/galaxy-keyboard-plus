import asyncio
import psycopg2
import logging
from typing import List, Dict
from concurrent.futures import ThreadPoolExecutor
from app.config.settings import DB_PARAMS, MAX_SEARCH_RESULTS

logger = logging.getLogger(__name__)
executor = ThreadPoolExecutor(max_workers=10)


async def search_photos_by_keywords(user_id: str, keywords: List[str]) -> List[Dict]:
    """키워드로 DB에서 사진 검색 - 매치 점수 포함"""

    def sync_search_photos():
        connection = psycopg2.connect(**DB_PARAMS)
        cursor = connection.cursor()

        try:
            # 개선된 검색 쿼리 - 매치 점수와 정보 포함
            query = """
            WITH keyword_matches AS (
            SELECT 
                i.access_id,
                i.image_time,
                i.caption,
                ik.keyword,
                CASE 
                    WHEN ik.keyword = ANY(%s) THEN 1.0
                    ELSE 0.5
                END as match_score
            FROM images i
            JOIN image_keywords ik 
                ON i.access_id = ik.image_id AND i.user_id = ik.user_id  -- ✅ 핵심 수정
            WHERE i.user_id = %s 
            AND (
                ik.keyword = ANY(%s)
                OR EXISTS (
                    SELECT 1 FROM unnest(%s::text[]) AS search_kw
                    WHERE ik.keyword ILIKE '%%' || search_kw || '%%'
                )
            )
        ),
        aggregated AS (
            SELECT 
                access_id,
                image_time,
                caption,
                COUNT(DISTINCT keyword) as keyword_count,
                SUM(match_score) as total_score,
                STRING_AGG(DISTINCT keyword, ', ') as matched_keywords
            FROM keyword_matches
            GROUP BY access_id, image_time, caption
        )
        SELECT 
            access_id,
            caption,
            keyword_count,
            total_score,
            matched_keywords,
            image_time
        FROM aggregated
        ORDER BY total_score DESC, keyword_count DESC, image_time DESC
        LIMIT %s;

            """

            cursor.execute(
                query, (keywords, user_id, keywords, keywords, MAX_SEARCH_RESULTS)
            )
            results = cursor.fetchall()

            # 결과를 딕셔너리 리스트로 변환
            photo_results = []
            for row in results:
                photo_results.append(
                    {
                        "access_id": row[0],
                        "caption": row[1],
                        "match_count": row[2],
                        "score": float(row[3]),
                        "matched_keywords": row[4],
                        "image_time": row[5].isoformat() if row[5] else None,
                    }
                )

                logger.info(
                    f"📷 사진: {row[0]}, 점수: {row[3]:.1f}, 매치: {row[2]}개, 키워드: {row[4]}"
                )

            return photo_results

        finally:
            cursor.close()
            connection.close()

    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(executor, sync_search_photos)
