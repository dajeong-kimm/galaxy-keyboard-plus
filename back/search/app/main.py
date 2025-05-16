## RAG 관련 진입점
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from app.api.search_image_info import router as search_image_info_router
from app.api.search_endpoints import router as search_endpoints_router
from app.api.db_connection_test import router as db_connection_test_router
from app.api.get_image import router as get_image_router
from app.api.keyword_exists import router as keyword_exists_router
from app.api.keyword_images import router as keyword_images_router
from app.api.clipboard_latest import router as clipboard_latest_router
import logging

app = FastAPI()

# CORS 설정
# app.add_middleware(
#     CORSMiddleware,
#     allow_origins=[
#         "https://k12e201.p.ssafy.io",
#         "http://k12e201.p.ssafy.io",
#         "http://localhost:3000",
#     ],
#     allow_credentials=True,
#     allow_methods=["*"],
#     allow_headers=["*"],
# )

# 로깅 기본 설정
logging.basicConfig(
    level=logging.INFO,  # 로그 레벨: DEBUG, INFO, WARNING, ERROR, CRITICAL
    format="[%(asctime)s] %(levelname)s - %(message)s",
)

# 로그 출력 테스트
logging.info("✅ FastAPI 애플리케이션 시작 전 로깅 설정 완료")

# API 라우터 등록
app.include_router(search_image_info_router, prefix="/search")
app.include_router(db_connection_test_router, prefix="/search")
app.include_router(get_image_router, prefix="/search")
app.include_router(keyword_exists_router, prefix="/search")
app.include_router(keyword_images_router, prefix="/search")
app.include_router(clipboard_latest_router, prefix="/search")
app.include_router(search_endpoints_router)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8091)

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
