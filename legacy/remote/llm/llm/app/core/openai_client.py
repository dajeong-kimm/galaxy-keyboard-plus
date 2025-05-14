# llm/app/core/openai_client.py
import json
from fastapi import HTTPException, status
from openai import AsyncOpenAI, OpenAIError

from app.core.config import get_settings

# 설정 로드 (API 키 포함)
try:
    settings = get_settings()
    # AsyncOpenAI 클라이언트 인스턴스 생성 (설정에서 API 키 사용)
    async_openai_client = AsyncOpenAI(api_key=settings.openai_api_key)
except ValueError as e:
    # 설정 로드 실패 시 (예: API 키 누락) 클라이언트를 None으로 설정
    print(f"OpenAI 클라이언트 초기화 실패: {e}")
    async_openai_client = None
except Exception as e:
    print(f"예상치 못한 오류로 OpenAI 클라이언트 초기화 실패: {e}")
    async_openai_client = None


def get_openai_client() -> AsyncOpenAI:
    """FastAPI 의존성 주입을 통해 AsyncOpenAI 클라이언트를 제공합니다."""
    if async_openai_client is None:
        # 클라이언트 초기화 실패 시 HTTP 오류 발생
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="OpenAI 클라이언트가 초기화되지 않았습니다. API 키 설정을 확인하세요.",
        )
    return async_openai_client

# --- 오류 처리 헬퍼 (서비스 계층이나 컨트롤러에서 사용 가능) ---

def handle_openai_error(e: OpenAIError) -> HTTPException:
    """OpenAI API 오류를 적절한 HTTP 상태 코드와 메시지로 변환합니다."""
    status_code = e.status_code if hasattr(e, 'status_code') and e.status_code else status.HTTP_500_INTERNAL_SERVER_ERROR
    detail = str(e.message) if hasattr(e, 'message') and e.message else str(e)
    return HTTPException(status_code=status_code, detail=detail)

async def generate_error_sse(e: OpenAIError) -> str:
    """OpenAI 오류 발생 시 SSE 형식의 오류 메시지를 생성합니다."""
    status_code = e.status_code if hasattr(e, 'status_code') and e.status_code else 500
    error_detail = str(e.message) if hasattr(e, 'message') and e.message else str(e)
    error_json = json.dumps({"error": {"message": error_detail, "status_code": status_code}})
    return f"event: error\ndata: {error_json}\n\n"