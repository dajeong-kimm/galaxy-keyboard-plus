from fastapi import APIRouter
from app.api.endpoints import web_search, server_status

api_router = APIRouter()

# 웹 검색 라우터 등록
api_router.include_router(
    web_search.router, 
    prefix="/search",
    tags=["Web Search"]
)

# 서버 상태 라우터 등록
api_router.include_router(
    server_status.router,
    prefix="/status",
    tags=["Server Status"]
)