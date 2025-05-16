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


@router.get("/clipboard/latest/")
def get_latest_clipboard_item(user_id: str = Query(...)):
    """
    해당 user_id의 clipboard_items 중 가장 최근 항목 반환
    """
    try:
        conn = psycopg2.connect(**DB_PARAMS)
        cursor = conn.cursor()

        query = """
        SELECT value, type, created_at
        FROM clipboard_items
        WHERE user_id = %s
        ORDER BY created_at DESC
        LIMIT 1;
        """
        cursor.execute(query, (user_id,))
        result = cursor.fetchone()

        cursor.close()
        conn.close()

        if result:
            return {
                "user_id": user_id,
                "value": result[0],
                "type": result[1],
                "created_at": result[2].isoformat() if result[2] else None,
            }
        else:
            return {"user_id": user_id, "message": "최근 클립보드 항목이 없습니다."}

    except Exception as e:
        return {"error": str(e)}
