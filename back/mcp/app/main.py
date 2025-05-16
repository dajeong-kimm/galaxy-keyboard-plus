import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api.routes import api_router
from app.core.mcp_client import MCPClient
from app.core.mcp_manager import MCPManager
from fastapi.responses import JSONResponse
import os

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
    # candidates에 사용할 MCP 서버 추가
    candidates = [
        {"name": "google", "url": os.getenv("GOOGLE_WEB_SEARCH_URL")},
        {"name": "brave", "url": os.getenv("WEB_SEARCH_URL")},
        # ... 필요한 만큼 추가
    ]

    valid_clients = []
    for conf in candidates:
        client = MCPClient(conf["name"], conf["url"])
        await client.initialize()
        if await client.health_check():
            valid_clients.append(client)
            logger.info(f"{conf['name']} MCP 연결 성공")
        else:
            logger.warning(f"{conf['name']} MCP 연결 실패")
            await client.close() 

    # 정상 클라이언트만 매니저에 등록해서 싱글턴처럼 보관
    app.state.mcp_manager = MCPManager(valid_clients)
    await app.state.mcp_manager.initialize()  # 세션/툴리스트 캐싱 등


# 애플리케이션 종료 이벤트 핸들러
@app.on_event("shutdown")
async def shutdown_event():
    logger.info("애플리케이션 종료")
    # 매니저/모든 MCP 클라이언트 세션 정리
    mcp_manager = getattr(app.state, "mcp_manager", None)
    if mcp_manager:
        await mcp_manager.close()

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