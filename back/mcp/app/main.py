import asyncio
import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api.routes import api_router
from app.core.mcp_manager import MCPManager

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# FastAPI 앱 생성 - Swagger UI 명시적 설정
app = FastAPI(
    title="MCP Web Search API",
    description="웹 검색을 위한 MCP 서버 통합 서비스",
    version="1.0.0",
    docs_url="/docs",  # Swagger UI URL (기본값)
    redoc_url="/redoc",  # ReDoc URL (기본값)
    openapi_url="/openapi.json"  # OpenAPI 스키마 URL (기본값)
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

# 애플리케이션 시작 시 MCP 서버 시작
@app.on_event("startup")
async def startup_event():
    logger.info("Starting MCP web search service...")
    app.state.mcp_manager = MCPManager()
    
    # Node.js 버전 확인
    try:
        version_cmd = "node --version"
        node_process = await asyncio.create_subprocess_shell(
            version_cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE
        )
        stdout, stderr = await node_process.communicate()
        logger.info(f"Node.js version: {stdout.decode().strip()}")
    except Exception as e:
        logger.error(f"Failed to check Node.js version: {str(e)}")
    
    # 서버 시작
    logger.info("Attempting to start web_search server...")
    success = await app.state.mcp_manager.start_server("web_search")
    
    if not success:
        logger.error("Failed to start web_search server")
        return
    
    logger.info("Waiting for server to stabilize...")
    await asyncio.sleep(5)  # 서버 시작 대기 시간 증가
    
    # 클라이언트 초기화
    try:
        client = await app.state.mcp_manager.initialize_client("web_search")
        if client:
            logger.info("Web search client initialized successfully")
        else:
            logger.error("Failed to initialize web_search client")
    except Exception as e:
        logger.error(f"Error initializing client: {str(e)}")

# 애플리케이션 종료 시 MCP 서버 종료
@app.on_event("shutdown")
async def shutdown_event():
    if hasattr(app.state, "mcp_manager"):
        await app.state.mcp_manager.stop_all_servers()

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=8050, reload=True)