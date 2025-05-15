import aiohttp
import logging
import uuid

logger = logging.getLogger(__name__)

class MCPClient:
    """MCP 서버와 통신하는 클라이언트"""
    
    def __init__(self, server_name: str, server_url: str):
        self.server_name = server_name
        self.server_url = server_url
        self.session = None
        self.tools_cache = []
    
    async def initialize(self):
        """세션 초기화"""
        self.session = aiohttp.ClientSession()
        await self.cache_tools()
        return self
    
    async def close(self):
        """세션 종료"""
        if getattr(self, "session", None):
            await self.session.close()
            self.session = None
    
    async def cache_tools(self):
        """툴 목록을 캐시해둠"""
        tools = await self.get_tools_list()
        self.tools_cache = tools.get("tools", [])

        """
        [
            {
                "name": "brave_web_search",
                "description": "Brave 웹 검색",
                "parameters": { ... }
            },
            {
                "name": "brave_local_search",
                "description": "Brave 지역 검색",
                "parameters": { ... }
            }
         # ... 등등
        ]
        """

    async def health_check(self) -> bool:
        try:
            url = self.server_url.rstrip("/") + "/health"
            async with self.session.get(url, timeout=5) as resp:
                return resp.status == 200
        except Exception as e:
            logger.warning(f"[{self.server_name}] Health check failed: {e}")
            return False
    
    async def get_tools_list(self) -> dict:
        """MCP 서버의 툴 리스트 반환"""
        request_data = {
            "jsonrpc": "2.0",
            "id": str(uuid.uuid4()),
            "method": "list_tools",
            "params": {}
        }
        try:
            async with self.session.post(
                self.server_url,
                json=request_data,
                headers={"Content-Type": "application/json"}
            ) as resp:
                data = await resp.json()
                return data.get("result", {}) if "result" in data else {}
        except Exception as e:
            logger.error(f"[{self.server_name}] Failed to get tools: {e}")
            return {}

    async def call_tool(self, tool_name: str, arguments: dict) -> dict:
        """MCP 서버의 특정 툴 실행"""
        request_data = {
            "jsonrpc": "2.0",
            "id": str(uuid.uuid4()),
            "method": "call_tool",
            "params": {
                "name": tool_name,
                "arguments": arguments
            }
        }
        try:
            async with self.session.post(
                self.server_url,
                json=request_data,
                headers={"Content-Type": "application/json"}
            ) as resp:
                data = await resp.json()
                return data
        except Exception as e:
            logger.error(f"[{self.server_name}] call_tool error: {e}")
            return {"error": str(e)}