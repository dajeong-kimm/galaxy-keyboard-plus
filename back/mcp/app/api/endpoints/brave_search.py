from fastapi import APIRouter, Request, HTTPException, Depends
from app.models.schemas import SearchRequest, SearchResponse
from app.core.mcp_client import MCPClient
from app.config import settings
import logging

logger = logging.getLogger(__name__)
router = APIRouter()

async def get_brave_search_client(request: Request) -> MCPClient:
    """Brave 검색 클라이언트 초기화"""
    if not hasattr(request.app.state, "brave_search_client"):
        logger.info("Brave 검색 클라이언트 초기화")
        client = MCPClient("brave_search", settings.BRAVE_SEARCH_URL)
        await client.initialize()
        request.app.state.brave_search_client = client
    
    return request.app.state.brave_search_client

@router.post(
    "/web", 
    response_model=SearchResponse,
    summary="Brave 웹 검색",
    description="Brave Search API를 사용하여 웹 검색 수행",
    response_description="검색 결과"
)
async def brave_web_search(
    search_request: SearchRequest, 
    client: MCPClient = Depends(get_brave_search_client)
):
    """Brave 웹 검색 수행"""
    try:
        result = await client.brave_web_search(
            query=search_request.query,
            count=search_request.num_results,
            offset=search_request.offset if hasattr(search_request, 'offset') else 0
        )
        
        if "error" in result:
            logger.error(f"검색 오류: {result['error']}")
            raise HTTPException(status_code=500, detail=result.get("error"))
        
        # 결과가 비어있는 경우 빈 결과 반환
        if not result.get("results"):
            return SearchResponse(
                results=[],
                query=search_request.query
            )
        
        return SearchResponse(
            results=result.get("results", []),
            query=search_request.query
        )
    except Exception as e:
        logger.exception(f"웹 검색 중 예외 발생: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@router.post(
    "/local", 
    response_model=SearchResponse,
    summary="Brave 지역 검색",
    description="Brave Search API를 사용하여 비즈니스 및 서비스에 대한 지역 검색 수행",
    response_description="지역 검색 결과"
)
async def brave_local_search(
    search_request: SearchRequest, 
    client: MCPClient = Depends(get_brave_search_client)
):
    """Brave 지역 검색 수행"""
    try:
        result = await client.brave_local_search(
            query=search_request.query,
            count=search_request.num_results
        )
        
        if "error" in result:
            logger.error(f"검색 오류: {result['error']}")
            raise HTTPException(status_code=500, detail=result.get("error"))
        
        # 결과가 비어있는 경우 빈 결과 반환
        if not result.get("results"):
            return SearchResponse(
                results=[],
                query=search_request.query
            )
        
        return SearchResponse(
            results=result.get("results", []),
            query=search_request.query
        )
    except Exception as e:
        logger.exception(f"지역 검색 중 예외 발생: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

# 기존 web_search와 호환성 유지를 위한 엔드포인트
@router.post(
    "/", 
    response_model=SearchResponse,
    summary="웹 검색",
    description="Brave Search API를 사용하여 웹 검색을 수행합니다. (기존 web_search와 호환)",
    response_description="검색 결과"
)
async def search_web_compatible(
    search_request: SearchRequest, 
    client: MCPClient = Depends(get_brave_search_client)
):
    """기존 웹 검색 API와의 호환성을 위한 엔드포인트"""
    return await brave_web_search(search_request, client)