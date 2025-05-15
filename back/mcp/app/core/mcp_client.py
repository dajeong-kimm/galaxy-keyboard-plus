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
            "method": "call_tool",  # call_tool을 사용하여 메서드 호출
            "params": {
                "name": "search",  # 호출할 도구 이름
                "arguments": {  # 도구에 전달할 인수들
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
        

    async def brave_web_search(self, query: str, count: int = 10, offset: int = 0) -> Dict[str, Any]:
        """Brave 웹 검색 기능"""
        request_data = {
            "jsonrpc": "2.0",
            "id": str(uuid.uuid4()),
            "method": "call_tool",
            "params": {
                "name": "brave_web_search",
                "arguments": {
                    "query": query,
                    "count": count,
                    "offset": offset
                }
            }
        }
        
        logger.info(f"Brave 웹 검색 요청 전송: {json.dumps(request_data)}")
        
        try:
            async with self.session.post(
                self.server_url,
                json=request_data,
                headers={"Content-Type": "application/json"}
            ) as response:
                if response.status != 200:
                    logger.error(f"서버 오류: {response.status}")
                    return {"error": f"서버 오류: {response.status}"}
                
                data = await response.json()
                logger.info(f"응답 수신: {json.dumps(data)}")
                
                # Rate Limit 오류 처리
                if "result" in data and "content" in data["result"] and data["result"].get("isError", False):
                    error_message = data["result"]["content"][0]["text"]
                    if "Rate limit exceeded" in error_message or "RATE_LIMITED" in error_message:
                        logger.warning(f"Brave API 요청 제한에 도달했습니다: {error_message}")
                        return {
                            "results": [],
                            "error": "API 요청 제한에 도달했습니다. 잠시 후 다시 시도해주세요."
                        }
                    return {"error": error_message}
                
                if "error" in data:
                    logger.error(f"RPC 오류: {data['error']}")
                    return {"error": f"RPC 오류: {data['error']}"}
                
                # 검색 결과 파싱
                if "result" in data and "content" in data["result"]:
                    content = data["result"]["content"]
                    results = []
                    
                    # content[0].text에는 검색 결과가 일반 텍스트로 포함되어 있음
                    if content and len(content) > 0 and "text" in content[0]:
                        text = content[0]["text"]
                        
                        # 텍스트에서 검색 결과 파싱
                        entries = text.split("\n\n")
                        for entry in entries:
                            if entry.strip():
                                lines = entry.split("\n")
                                if len(lines) >= 3:
                                    title = lines[0].replace("Title: ", "")
                                    description = lines[1].replace("Description: ", "")
                                    url = lines[2].replace("URL: ", "")
                                    
                                    results.append({
                                        "title": title,
                                        "url": url,
                                        "description": description
                                    })
                        
                        return {
                            "results": results,
                            "query": query
                        }
                
                # 검색 결과가 없거나 오류인 경우
                return {
                    "results": [],
                    "query": query
                }
                
        except Exception as e:
            logger.error(f"Brave 웹 검색 요청 오류: {str(e)}")
            return {"error": str(e)}

    async def brave_local_search(self, query: str, count: int = 10) -> Dict[str, Any]:
        """Brave 지역 검색 기능"""
        request_data = {
            "jsonrpc": "2.0",
            "id": str(uuid.uuid4()),
            "method": "call_tool",
            "params": {
                "name": "brave_local_search",
                "arguments": {
                    "query": query,
                    "count": count
                }
            }
        }
        
        logger.info(f"Brave 지역 검색 요청 전송: {json.dumps(request_data)}")
        
        try:
            async with self.session.post(
                self.server_url,
                json=request_data,
                headers={"Content-Type": "application/json"}
            ) as response:
                if response.status != 200:
                    logger.error(f"서버 오류: {response.status}")
                    return {"error": f"서버 오류: {response.status}"}
                
                data = await response.json()
                logger.info(f"응답 수신: {json.dumps(data)}")
                
                # Rate Limit 오류 처리
                if "result" in data and "content" in data["result"] and data["result"].get("isError", False):
                    error_message = data["result"]["content"][0]["text"]
                    if "Rate limit exceeded" in error_message or "RATE_LIMITED" in error_message:
                        logger.warning(f"Brave API 요청 제한에 도달했습니다: {error_message}")
                        return {
                            "results": [],
                            "error": "API 요청 제한에 도달했습니다. 잠시 후 다시 시도해주세요."
                        }
                    return {"error": error_message}
                
                if "error" in data:
                    logger.error(f"RPC 오류: {data['error']}")
                    return {"error": f"RPC 오류: {data['error']}"}
                
                # 지역 검색 결과 파싱
                if "result" in data and "content" in data["result"]:
                    content = data["result"]["content"]
                    results = []
                    
                    # content[0].text에는 검색 결과가 일반 텍스트로 포함되어 있음
                    if content and len(content) > 0 and "text" in content[0]:
                        text = content[0]["text"]
                        
                        # 텍스트에서 검색 결과 파싱
                        entries = text.split("\n\n")
                        for entry in entries:
                            if entry.strip():
                                lines = entry.split("\n")
                                if len(lines) >= 3:
                                    title = lines[0].replace("Name: ", "")
                                    address = lines[1].replace("Address: ", "")
                                    rating = ""
                                    phone = ""
                                    
                                    for line in lines[2:]:
                                        if line.startswith("Rating:"):
                                            rating = line.replace("Rating: ", "")
                                        elif line.startswith("Phone:"):
                                            phone = line.replace("Phone: ", "")
                                    
                                    results.append({
                                        "title": title,
                                        "description": f"{address} • {rating} • {phone}".strip(" • "),
                                        "url": ""
                                    })
                        
                        return {
                            "results": results,
                            "query": query
                        }
                
                # 검색 결과가 없거나 오류인 경우
                return {
                    "results": [],
                    "query": query
                }
                
        except Exception as e:
            logger.error(f"Brave 지역 검색 요청 오류: {str(e)}")
            return {"error": str(e)}