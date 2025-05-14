from fastapi import APIRouter, Request, HTTPException
from app.models.schemas import SearchRequest, SearchResponse

router = APIRouter()

@router.post(
    "/", 
    response_model=SearchResponse,
    summary="웹 검색 실행",
    description="제공된 쿼리를 사용하여 Google 검색을 수행하고 결과를 반환합니다.",
    response_description="검색 결과 목록"
)
async def search_web(request: Request, search_request: SearchRequest):
    """
    웹 검색을 수행합니다
    """
    # MCP 클라이언트 가져오기
    if not hasattr(request.app.state, "mcp_manager") or \
       "web_search" not in request.app.state.mcp_manager.clients:
        raise HTTPException(status_code=503, detail="Search service unavailable")
    
    client = request.app.state.mcp_manager.clients["web_search"]
    
    # 검색 실행
    result = await client.search(
        query=search_request.query,
        num_results=search_request.num_results
    )
    
    # 에러 처리
    if "error" in result:
        raise HTTPException(status_code=500, detail=result.get("error"))
    
    return {
        "results": result.get("results", []),
        "query": search_request.query
    }