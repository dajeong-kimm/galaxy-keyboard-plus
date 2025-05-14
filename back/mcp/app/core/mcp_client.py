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
        # tools/call 메서드 사용 (프록시 서버에서 지원하는 형식)
        request_data = {
            "jsonrpc": "2.0",
            "id": str(uuid.uuid4()),
            "method": "tools/call",
            "params": {
                "name": "search",
                "arguments": {
                    "query": query,
                    "limit": num_results
                }
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
                if "result" in data and isinstance(data["result"], list):
                    # 직접 결과 배열 반환
                    return {
                        "results": data["result"],
                        "query": query
                    }
                elif "result" in data and "content" in data["result"] and len(data["result"]["content"]) > 0:
                    # MCP 형식 응답 처리 (content[0].text)
                    try:
                        result_text = data["result"]["content"][0]["text"]
                        logger.info(f"Parsing content text: {result_text}")
                        result = json.loads(result_text)
                        return {
                            "results": result,
                            "query": query
                        }
                    except Exception as e:
                        logger.error(f"Error parsing result content: {str(e)}")
                        return {"error": f"Failed to parse search results: {str(e)}"}
                elif "result" in data:
                    # 기타 결과 형식
                    return {
                        "results": data["result"] if isinstance(data["result"], list) else [data["result"]],
                        "query": query
                    }
                
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
        # 기본 도구 목록 반환 (tools/list는 작동하지 않을 가능성이 높음)
        return {
            "tools": [{
                "name": "search",
                "description": "Search the web using Google",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "query": {
                            "type": "string",
                            "description": "Search query"
                        },
                        "limit": {
                            "type": "number",
                            "description": "Maximum number of results to return (default: 5)"
                        }
                    },
                    "required": ["query"]
                }
            }]
        }