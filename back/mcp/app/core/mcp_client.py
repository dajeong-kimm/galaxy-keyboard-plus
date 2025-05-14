import aiohttp
import logging
import uuid
import json
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
        """웹 검색 수행"""
        # JSON-RPC 2.0 요청 생성
        request_data = {
        "jsonrpc": "2.0",
        "id": str(uuid.uuid4()),
        "method": "search",  # 도구 이름을 직접 메서드로 사용
        "params": {      # 인수를 직접 params에 포함
            "query": query,
            "limit": num_results
        }
    }
        
        logger.info(f"Sending search request: {json.dumps(request_data)}")
        
        try:
            async with self.session.post(
                self.server_url,
                json=request_data,
                headers={"Content-Type": "application/json"}
            ) as response:
                if response.status != 200:
                    logger.error(f"Server error: {response.status}")
                    return {"error": f"Server error: {response.status}"}
                
                data = await response.json()
                logger.info(f"Received response: {json.dumps(data)}")
                
                if "error" in data:
                    logger.error(f"RPC error: {data['error']}")
                    return {"error": f"RPC error: {data['error']}"}
                
                # 응답 형식 처리
                if "result" in data and "content" in data["result"] and len(data["result"]["content"]) > 0:
                    try:
                        # content[0].text에서 JSON 결과 추출
                        result_text = data["result"]["content"][0]["text"]
                        logger.info(f"Parsing content text: {result_text}")
                        result = json.loads(result_text)
                        return {
                            "results": result,
                            "count": len(result)
                        }
                    except Exception as e:
                        logger.error(f"Error parsing result content: {str(e)}")
                        return {"error": f"Failed to parse search results: {str(e)}"}
                
                logger.error("Invalid response format")
                return {"error": "Invalid response format"}
        except Exception as e:
            logger.error(f"Error with search request: {str(e)}")
            return {"error": str(e)}
    
    async def health_check(self) -> bool:
        """서비스 상태 확인"""
        try:
            # 헬스 체크 엔드포인트 호출
            async with self.session.get(f"{self.server_url}/health", timeout=5) as response:
                return response.status == 200
        except Exception as e:
            logger.error(f"Health check failed: {str(e)}")
            return False
            
    async def get_tools_list(self) -> Dict[str, Any]:
        """사용 가능한 도구 목록 조회"""
        request_data = {
            "jsonrpc": "2.0",
            "id": str(uuid.uuid4()),
            "method": "listTools",
            "params": {}
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
                
                return {"tools": data.get("result", {}).get("tools", [])}
        except Exception as e:
            logger.error(f"Error getting tools list: {str(e)}")
            return {"error": str(e)}