from fastapi import APIRouter, Query
import psycopg2
import os
from dotenv import load_dotenv

load_dotenv()
router = APIRouter()

DB_PARAMS = {
    "host": "3.38.95.110",
    "port": "5434",
    "database": os.getenv("POSTGRES_RAG_DB_NAME"),
    "user": os.getenv("POSTGRES_RAG_USER"),
    "password": os.getenv("POSTGRES_RAG_PASSWORD"),
}


@router.get("/keyword/images/")
def get_images_by_keyword(
    user_id: str = Query(...),
    keyword: str = Query(...),
    page: int = Query(1, ge=1),
    page_size: int = Query(10, ge=1, le=100),
):
    """
    특정 user_id가 등록한 이미지 중, keyword에 해당하는 image_id (access_id) 리스트 반환
    """
    try:
        offset = (page - 1) * page_size
        conn = psycopg2.connect(**DB_PARAMS)
        cursor = conn.cursor()

        query = """
        SELECT ik.image_id
        FROM image_keywords ik
        JOIN images i ON ik.image_id = i.access_id
        WHERE i.user_id = %s AND ik.keyword = %s
        ORDER BY ik.created_at DESC
        LIMIT %s OFFSET %s;
        """
        cursor.execute(query, (user_id, keyword, page_size, offset))
        results = cursor.fetchall()
        image_ids = [row[0] for row in results]

        cursor.close()
        conn.close()

        return {
            "user_id": user_id,
            "keyword": keyword,
            "page": page,
            "page_size": page_size,
            "image_ids": image_ids,
        }

    except Exception as e:
        return {"error": str(e)}
