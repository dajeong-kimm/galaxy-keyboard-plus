from fastapi import APIRouter, Request, HTTPException, Depends
from app.models.schemas import SearchRequest, SearchResponse
from app.core.mcp_client import MCPClient
from app.config import settings
import logging

logger = logging.getLogger(__name__)
router = APIRouter()

async def get_web_search_client(request: Request) -> MCPClient:
    """웹 검색 클라이언트 가져오기"""
    # 앱 상태에 클라이언트가 없는 경우 생성
    if not hasattr(request.app.state, "web_search_client"):
        logger.info("Initializing web search client")
        client = MCPClient("web_search", settings.WEB_SEARCH_URL)
        await client.initialize()
        request.app.state.web_search_client = client
    
    return request.app.state.web_search_client

@router.post(
    "/", 
    response_model=SearchResponse,
    summary="웹 검색 실행",
    description="제공된 쿼리를 사용하여 검색을 수행하고 결과를 반환합니다.",
    response_description="검색 결과 목록"
)
async def search_web(
    search_request: SearchRequest, 
    client: MCPClient = Depends(get_web_search_client)
):
    """웹 검색을 수행합니다"""
    # 검색 실행
    result = await client.search(
        query=search_request.query,
        num_results=search_request.num_results
    )
    
    # 에러 처리
    if "error" in result:
        logger.error(f"Search error: {result['error']}")
        raise HTTPException(status_code=500, detail=result.get("error"))
    
    return {
        "results": result.get("results", []),
        "query": search_request.query
    }