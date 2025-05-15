import logging

logger = logging.getLogger(__name__)

class MCPManager:
    """여러 MCPClient를 관리하는 매니저"""
    def __init__(self, clients: list):
        # clients: [MCPClient, ...]
        self.clients = {client.server_name: client for client in clients}
        self.tools_cache = {}  # {server_name: [tools, ...]}

    async def initialize(self):
        # 모든 클라이언트 initialize (세션+툴 캐싱)
        for name, client in self.clients.items():
            await client.initialize()
            self.tools_cache[name] = client.tools_cache

    async def close(self):
        for client in self.clients.values():
            await client.close()

    def get_all_tools(self) -> dict:
        """서버별 툴 캐시 반환"""
        return self.tools_cache
        """
        {
            "brave": [
                {
                
                    "name": "brave_web_search",
                    "description": "Brave 웹 검색",
                    "parameters": { ... }
                },
                {
                
                    "name": "brave_local_search",
                    "description": "Brave 로컬 검색",
                    "parameters": { ... }
                
                # ... brave MCP에 등록된 모든 툴
            ],
            # ... 추가 MCP 서버도 같은 구조
        }
        """

    async def call_tool(self, server_name: str, tool_name: str, arguments: dict):
        """특정 MCP 서버에 툴 호출 위임"""
        client = self.clients.get(server_name)
        if not client:
            logger.error(f"No such MCP server: {server_name}")
            return {"error": f"No such MCP server: {server_name}"}
        return await client.call_tool(tool_name, arguments)
