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


@router.get("/keyword/exists/")
def keyword_exists(user_id: str = Query(...), keyword: str = Query(...)):
    """
    주어진 user_id가 등록한 이미지들 중 keyword가 존재하는지 여부 반환
    """
    try:
        conn = psycopg2.connect(**DB_PARAMS)
        cursor = conn.cursor()

        query = """
        SELECT EXISTS (
        SELECT 1
        FROM image_keywords
        WHERE user_id = %s AND keyword = %s
        );
        """
        cursor.execute(query, (user_id, keyword))
        exists = cursor.fetchone()[0]

        cursor.close()
        conn.close()

        return {"user_id": user_id, "keyword": keyword, "exists": exists}

    except Exception as e:
        return {"error": str(e)}
