## RAG 관련 진입점
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from app.api.search_image_info import router as search_image_info_router
from app.api.search_endpoints import router as search_endpoints_router
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
app.include_router(search_image_info_router, prefix="/search")
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
