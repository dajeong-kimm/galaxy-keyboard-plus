import os
import asyncio
import logging
from typing import Dict, Optional
from app.core.mcp_client import MCPClient

logger = logging.getLogger(__name__)

class MCPManager:
    """MCP 서버 관리 클래스"""
    
    def __init__(self):
        self.servers = {}  # 서버 프로세스
        self.clients = {}  # 클라이언트
        self.web_search_port = 8100
        self.web_search_path = os.path.join(
            os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
            "mcp_servers/web_search"
        )
    
    async def start_server(self, server_name: str) -> bool:
        """MCP 서버 시작"""
        if server_name == "web_search":
            # 빌드된 버전의 서버 실행 (GitHub 설명에 따라 수정)
            build_path = os.path.join(self.web_search_path, "build", "index.js")
            cmd = f"cd {self.web_search_path} && node {build_path} --port {self.web_search_port}"
            
            process = await asyncio.create_subprocess_shell(
                cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE
            )
            
            self.servers[server_name] = process
            logger.info(f"Started web_search MCP server on port {self.web_search_port}")
            return True
        
        return False
    
    async def stop_all_servers(self):
        """모든 MCP 서버 종료"""
        # 클라이언트 종료
        for client in list(self.clients.values()):
            await client.close()
        self.clients.clear()
        
        # 서버 프로세스 종료
        for name, process in list(self.servers.items()):
            if process:
                process.terminate()
                try:
                    await asyncio.wait_for(process.wait(), timeout=5.0)
                except asyncio.TimeoutError:
                    process.kill()
                logger.info(f"Stopped {name} MCP server")
        self.servers.clear()
    
    async def initialize_client(self, server_name: str) -> Optional[MCPClient]:
        """MCP 클라이언트 초기화"""
        if server_name == "web_search":
            server_url = f"http://localhost:{self.web_search_port}"
            client = MCPClient(server_name, server_url)
            await client.initialize()
            self.clients[server_name] = client
            logger.info(f"Initialized client for {server_name} MCP server")
            return client
        
        return None
    
    def get_server_status(self, server_name: str = None) -> Dict[str, Any]:
        """서버 상태 정보 반환"""
        if server_name:
            # 특정 서버 상태만 반환
            if server_name not in self.servers:
                return {"error": f"Server not found: {server_name}"}
            
            server = self.servers[server_name]
            status = "running" if server is not None and server.returncode is None else "stopped"
            
            return {
                "name": server_name,
                "status": status,
                "port": self.web_search_port if server_name == "web_search" else None,
                "url": f"http://localhost:{self.web_search_port}" if server_name == "web_search" else None,
                "client_connected": server_name in self.clients
            }
        else:
            # 모든 서버 상태 반환
            result = {}
            for name, server in self.servers.items():
                status = "running" if server is not None and server.returncode is None else "stopped"
                
                result[name] = {
                    "name": name,
                    "status": status,
                    "port": self.web_search_port if name == "web_search" else None,
                    "url": f"http://localhost:{self.web_search_port}" if name == "web_search" else None,
                    "client_connected": name in self.clients
                }
            
            return result
    