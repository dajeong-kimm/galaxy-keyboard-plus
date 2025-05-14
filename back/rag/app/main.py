## RAG 관련 진입점
from fastapi import FastAPI
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
import logging

app = FastAPI()

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
