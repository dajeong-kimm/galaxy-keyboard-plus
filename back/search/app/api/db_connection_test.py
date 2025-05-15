from fastapi import APIRouter
from fastapi.responses import JSONResponse
import psycopg2
from psycopg2 import OperationalError
import os
from dotenv import load_dotenv

load_dotenv()  # .env 파일 로드

router = APIRouter()


@router.get("/test-db-connection")
def test_db_connection():
    """PostgreSQL 데이터베이스 연결 테스트 엔드포인트"""

    # 연결 정보 (환경 변수에서 불러오기)
    connection_params = {
        "host": "3.38.95.110",
        "port": "5434",
        "database": os.getenv("POSTGRES_RAG_DB_NAME"),
        "user": os.getenv("POSTGRES_RAG_USER"),
        "password": os.getenv("POSTGRES_RAG_PASSWORD"),
    }

    try:
        # 데이터베이스 연결 시도
        connection = psycopg2.connect(
            host=connection_params["host"],
            port=connection_params["port"],
            database=connection_params["database"],
            user=connection_params["user"],
            password=connection_params["password"],
        )

        # 연결 성공 시 커서 생성 및 테스트 쿼리 실행
        cursor = connection.cursor()
        cursor.execute("SELECT version();")
        db_version = cursor.fetchone()

        # 리소스 정리
        cursor.close()
        connection.close()

        return JSONResponse(
            status_code=200,
            content={
                "status": "connected",
                "database": connection_params["database"],
                "host": f"{connection_params['host']}:{connection_params['port']}",
                "postgresql_version": db_version[0] if db_version else "Unknown",
            },
        )

    except OperationalError as e:
        return JSONResponse(
            status_code=500,
            content={
                "status": "connection_failed",
                "error": str(e),
                "database": connection_params["database"],
                "host": f"{connection_params['host']}:{connection_params['port']}",
            },
        )
    except Exception as e:
        return JSONResponse(
            status_code=500, content={"status": "error", "message": str(e)}
        )
