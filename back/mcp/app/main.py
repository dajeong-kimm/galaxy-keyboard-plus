import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api.routes import api_router
from app.config import settings
from typing import List
from fastapi.responses import JSONResponse

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# FastAPI 앱 생성
app = FastAPI(
    title="MCP Web Search API",
    description="웹 검색을 위한 API 게이트웨이",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/openapi.json"
)

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# API 라우터 등록
app.include_router(api_router, prefix="/api")

# 애플리케이션 시작 이벤트 핸들러
@app.on_event("startup")
async def startup_event():
    logger.info("애플리케이션 시작")
    # 필요한 초기화 작업 수행

# 애플리케이션 종료 이벤트 핸들러
@app.on_event("shutdown")
async def shutdown_event():
    logger.info("애플리케이션 종료")
    # 클라이언트 세션 정리
    if hasattr(app.state, "brave_search_client") and app.state.brave_search_client:
        await app.state.brave_search_client.close()

# 오류 핸들러
@app.exception_handler(500)
async def internal_error_handler(request, exc):
    return JSONResponse(
        status_code=500,
        content={"detail": str(exc)},
    )

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=8050, reload=True)