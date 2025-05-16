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
        logger.info(f"[MCPManager] Initializing {len(self.clients)} MCP clients...")
        for name, client in self.clients.items():
            await client.initialize()
            self.tools_cache[name] = client.tools_cache
        logger.info(f"[MCPManager] MCP clients initialized. Tool cache: {self.tools_cache}")

    async def close(self):
        logger.info(f"[MCPManager] Closing all MCP clients...")
        for client in self.clients.values():
            await client.close()
        logger.info(f"[MCPManager] All MCP clients closed.")

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
        logger.info(f"[MCPManager] call_tool: server={server_name}, tool={tool_name}, arguments={arguments}")
        client = self.clients.get(server_name)
        if not client:
            logger.error(f"[MCPManager] No such MCP server: {server_name}")
            return {"error": f"No such MCP server: {server_name}"}
        try:
            result = await client.call_tool(tool_name, arguments)
            logger.info(f"[MCPManager] call_tool result: server={server_name}, tool={tool_name}, result={str(result)[:300]}")  # 너무 길면 잘라서
            return result
        except Exception as e:
            logger.error(f"[MCPManager] Exception in call_tool: {e}")
            return {"error": str(e)}
