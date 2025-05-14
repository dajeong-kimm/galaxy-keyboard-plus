from fastapi import APIRouter
from fastapi.responses import JSONResponse
import os
from dotenv import load_dotenv
from pinecone import Pinecone

load_dotenv()  # .env 파일 로드

router = APIRouter()


@router.get("/test-pinecone")
def test_pinecone_connection():
    try:
        api_key = os.getenv("PINECONE_API_KEY")
        index_name = os.getenv("PINECONE_INDEX_NAME")

        if not api_key:
            raise ValueError("PINECONE_API_KEY is missing")

        # Pinecone 인스턴스 생성
        pc = Pinecone(api_key=api_key)

        # 인덱스 리스트 조회
        indexes = pc.list_indexes().names()

        if index_name in indexes:
            return JSONResponse(
                status_code=200, content={"status": "connected", "indexes": indexes}
            )
        else:
            return JSONResponse(
                status_code=404,
                content={"status": "index_not_found", "indexes": indexes},
            )

    except Exception as e:
        return JSONResponse(
            status_code=500, content={"status": "error", "message": str(e)}
        )
