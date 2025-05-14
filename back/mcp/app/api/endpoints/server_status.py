from fastapi import APIRouter, Request, HTTPException
from typing import Dict, Any
import asyncio

router = APIRouter()

@router.get(
    "/",
    summary="MCP 서버 상태 확인",
    description="모든 MCP 서버의 현재 상태 정보 반환"
)
async def get_server_status(request: Request) -> Dict[str, Any]:
    """MCP 서버 상태 정보 반환"""
    if not hasattr(request.app.state, "mcp_manager"):
        raise HTTPException(status_code=503, detail="MCP manager not initialized")
    
    mcp_manager = request.app.state.mcp_manager
    server_status = {}
    
    # 서버 상태 수집
    for name, server in mcp_manager.servers.items():
        is_running = server is not None and server.returncode is None
        client_connected = name in mcp_manager.clients
        
        server_status[name] = {
            "name": name,
            "status": "running" if is_running else "stopped",
            "port": mcp_manager.web_search_port if name == "web_search" else None,
            "client_connected": client_connected
        }
    
    return {
        "servers": server_status,
        "total_count": len(server_status)
    }
