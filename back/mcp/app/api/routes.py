from fastapi import APIRouter
from app.api.endpoints import brave_search  # server_status 제거

api_router = APIRouter()

# Brave 검색 라우터만 등록
api_router.include_router(
    brave_search.router, 
    prefix="/search",
    tags=["Search"]
)

# server_status 라우터 부분 제거