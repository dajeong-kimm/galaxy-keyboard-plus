import aiohttp
import logging
import uuid
from typing import Dict, Any, Optional, List

logger = logging.getLogger(__name__)

class MCPClient:
    """MCP 서버와 통신하는 클라이언트"""
    
    def __init__(self, service_name: str, service_url: str):
        self.service_name = service_name
        self.service_url = service_url
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
        """웹 검색 수행"""
        if self.service_name != "web_search":
            return {"error": "Invalid service for search operation"}
        
        # JSON-RPC 2.0 요청 생성
        request_data = {
            "jsonrpc": "2.0",
            "id": str(uuid.uuid4()),
            "method": "search",
            "params": {
                "query": query,
                "limit": num_results
            }
        }
        
        try:
            async with self.session.post(
                self.service_url,
                json=request_data,
                headers={"Content-Type": "application/json"}
            ) as response:
                if response.status != 200:
                    return {"error": f"Server error: {response.status}"}
                
                data = await response.json()
                
                if "error" in data:
                    return {"error": f"RPC error: {data['error']}"}
                
                result = data.get("result", [])
                return {
                    "results": result,
                    "count": len(result)
                }
        except Exception as e:
            logger.error(f"Error with search request: {str(e)}")
            return {"error": str(e)}
    
    async def health_check(self) -> bool:
        """서비스 상태 확인"""
        try:
            # 일반 연결 테스트 시도
            async with self.session.post(
                self.service_url,
                json={
                    "jsonrpc": "2.0",
                    "id": str(uuid.uuid4()),
                    "method": "ping",  # 간단한 ping 메서드 사용
                    "params": {}
                },
                headers={"Content-Type": "application/json"},
                timeout=3
            ) as response:
                # 상태 코드가 200이면 서비스가 실행 중인 것으로 간주
                return response.status == 200
        except Exception as e:
            logger.error(f"Health check failed: {str(e)}")
            return False