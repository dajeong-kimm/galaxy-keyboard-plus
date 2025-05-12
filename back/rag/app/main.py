## RAG 관련 진입점
from fastapi import FastAPI, Depends, HTTPException
from pydantic import BaseModel
from typing import Optional
import json
from fastapi.responses import JSONResponse
from app.api.pinecone_test import router as pinecone_test_router
from app.api.image_classify import router as image_classify_router
from app.api.embedding_api import router as embedding_api_router
from app.api.text_extractor_api import router as text_extractor_api_router
from app.api.image_caption_api import router as image_caption_api_router
from app.api.schedule_api import router as schedule_api_router
from app.api.save_text import router as save_text_router
from app.api.image_upload import router as image_upload_router
from app.api.search_image_info import router as search_image_info_router
from app.api.test_image_upload import router as test_image_upload_router
from app.api.web_search_api import router as web_search_api_router
import logging

# MCP-WebResearch 모듈 임포트
from app.utils.mcp_web_research import (
    mcp_web_research,
    web_search,
    MCP_WEB_RESEARCH_ENABLED,
)

app = FastAPI()


# 앱 시작/종료 이벤트 핸들러 추가
@app.on_event("startup")
async def startup_event():
    # 애플리케이션 시작 시 MCP 서버 상태 확인
    if (
        MCP_WEB_RESEARCH_ENABLED
        and mcp_web_research
        and not mcp_web_research.initialized
    ):
        logging.info("🔍 MCP 웹 검색 서버 초기화 상태 확인 중...")
        # MCP 서버 초기화 재시도
        if mcp_web_research:
            mcp_web_research.setup_mcp_server()
            if mcp_web_research.initialized:
                logging.info("✅ MCP 웹 검색 서버 초기화 성공")
            else:
                logging.warning("⚠️ MCP 웹 검색 서버 초기화 실패")


@app.on_event("shutdown")
async def shutdown_event():
    # 애플리케이션 종료 시 MCP 리소스 정리
    if MCP_WEB_RESEARCH_ENABLED and mcp_web_research:
        logging.info("🧹 MCP 웹 검색 서버 정리 중...")
        mcp_web_research.cleanup()


# 로깅 기본 설정
logging.basicConfig(
    level=logging.INFO,  # 로그 레벨: DEBUG, INFO, WARNING, ERROR, CRITICAL
    format="[%(asctime)s] %(levelname)s - %(message)s",
)

# 로그 출력 테스트
logging.info("✅ FastAPI 애플리케이션 시작 전 로깅 설정 완료")

# API 라우터 등록
app.include_router(pinecone_test_router, prefix="/rag")
app.include_router(image_classify_router, prefix="/rag")
app.include_router(embedding_api_router, prefix="/rag")
app.include_router(text_extractor_api_router, prefix="/rag")
app.include_router(image_caption_api_router, prefix="/rag")
app.include_router(schedule_api_router, prefix="/rag")
app.include_router(save_text_router, prefix="/rag")
app.include_router(image_upload_router, prefix="/rag")
app.include_router(search_image_info_router, prefix="/rag")
app.include_router(test_image_upload_router, prefix="/test")
app.include_router(web_search_api_router, prefix="/test")

if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8090)

import time
from fastapi import Request


@app.middleware("http")
async def log_request_time(request: Request, call_next):
    start_time = time.time()

    response = await call_next(request)

    duration = time.time() - start_time  # 초 단위
    log_msg = f"⏱️ {request.method} {request.url.path} → {response.status_code} | {duration:.3f}s"
    logging.info(log_msg)

    return response
