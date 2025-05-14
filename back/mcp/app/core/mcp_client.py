import aiohttp
import logging
import uuid
from typing import Dict, Any, Optional, List

logger = logging.getLogger(__name__)

class MCPClient:
    """MCP 서버와 통신하는 클라이언트"""
    
    def __init__(self, server_name: str, server_url: str):
        self.server_name = server_name
        self.server_url = server_url
        self.session: Optional[aiohttp.ClientSession] = None
    
    async def initialize(self):
        """세션 초기화"""
        self.session = aiohttp.ClientSession()
        return self
    
    async def close(self):
        """세션 종료"""
        if self.session:
            await self.session.close()
    
    async def search(self, query: str, num_results: int = 5) -> Dict[str, Any]:
        """웹 검색 수행 - GitHub 설명에 맞게 수정"""
        # JSON-RPC 2.0 요청 생성
        request_data = {
            "jsonrpc": "2.0",
            "id": str(uuid.uuid4()),
            "method": "search",
            "params": {
                "query": query,
                "limit": num_results  # GitHub 문서에 따라 'limit' 파라미터로 변경
            }
        }
        
        try:
            async with self.session.post(
                self.server_url,
                json=request_data,
                headers={"Content-Type": "application/json"}
            ) as response:
                if response.status != 200:
                    return {"error": f"Server error: {response.status}"}
                
                data = await response.json()
                
                if "error" in data:
                    return {"error": f"RPC error: {data['error']}"}
                
                # GitHub 설명에 따른 결과 포맷 처리
                result = data.get("result", [])
                return {
                    "results": result,
                    "count": len(result)
                }
        except Exception as e:
            logger.error(f"Error with search request: {str(e)}")
            return {"error": str(e)}