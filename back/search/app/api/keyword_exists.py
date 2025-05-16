# app/api/keyword_exists.py

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
    주어진 user_id에 대해 keyword가 존재하는지 여부를 반환
    """
    try:
        conn = psycopg2.connect(**DB_PARAMS)
        cursor = conn.cursor()

        query = """
        SELECT EXISTS (
            SELECT 1
            FROM image_keywords ik
            JOIN images i ON ik.image_id = i.id
            WHERE i.user_id = %s AND ik.keyword = %s
        );
        """
        cursor.execute(query, (user_id, keyword))
        exists = cursor.fetchone()[0]

        cursor.close()
        conn.close()

        return {"user_id": user_id, "keyword": keyword, "exists": exists}

    except Exception as e:
        return {"error": str(e)}
