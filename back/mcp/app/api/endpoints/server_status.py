from fastapi import APIRouter, Request, HTTPException, Depends
from typing import Dict, Any
import logging
from app.core.mcp_client import MCPClient
from app.config import settings

logger = logging.getLogger(__name__)
router = APIRouter()

async def get_web_search_client(request: Request) -> MCPClient:
    """웹 검색 클라이언트 가져오기"""
    if not hasattr(request.app.state, "web_search_client"):
        logger.info("Initializing web search client")
        client = MCPClient("web_search", settings.WEB_SEARCH_URL)
        await client.initialize()
        request.app.state.web_search_client = client
    
    return request.app.state.web_search_client

@router.get(
    "/",
    summary="서비스 상태 확인",
    description="모든 MCP 서비스의 현재 상태 정보 반환"
)
async def get_service_status(
    request: Request, 
    client: MCPClient = Depends(get_web_search_client)
) -> Dict[str, Any]:
    """서비스 상태 정보 반환"""
    # 건강 상태 확인
    is_healthy = await client.health_check()
    
    server_status = {
        "web_search": {
            "name": "web_search",
            "status": "running" if is_healthy else "stopped",
            "url": settings.WEB_SEARCH_URL,
            "client_connected": is_healthy
        }
    }
    
    return {
        "servers": server_status,
        "total_count": len(server_status)
    }