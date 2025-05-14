from fastapi import APIRouter, Request, HTTPException
from typing import Dict, Any
import asyncio
import logging
import os

# 로거 설정
logger = logging.getLogger(__name__)

router = APIRouter()

@router.get(
    "/",
    summary="MCP 서버 상태 확인",
    description="모든 MCP 서버의 현재 상태 정보 반환"
)
async def get_server_status(request: Request) -> Dict[str, Any]:
    """MCP 서버 상태 정보 반환"""
    if not hasattr(request.app.state, "mcp_manager"):
        logger.error("MCP manager not initialized")
        raise HTTPException(status_code=503, detail="MCP manager not initialized")
    
    mcp_manager = request.app.state.mcp_manager
    logger.info("Retrieving server status information")
    
    # MCP 매니저에서 서버 상태 정보 가져오기
    server_status = mcp_manager.get_server_status()
    
    # 서버 목록 및 상태 로깅
    for name, info in server_status.items():
        logger.info(f"Server {name} status: {info['status']}, client connected: {info['client_connected']}")
    
    return {
        "servers": server_status,
        "total_count": len(server_status)
    }