from fastapi import APIRouter, HTTPException, Request
from app.models.schemas import SearchRequest, SearchResponse
from app.utils.llm import call_llm, summarize_with_llm
import logging

logger = logging.getLogger(__name__)
router = APIRouter()

@router.post(
    "/",
    response_model=SearchResponse,
    summary="검색",
    description="사용자 질문에 대해 검색을 수행합니다.",
    response_description="검색 결과"
)
async def search_endpoint(request: Request, body: SearchRequest):
    # 사용자의 질문
    query = request.query

    # mcp 서버의 툴 목록
    mcp_manager = request.app.state.mcp_manager
    tools_info = mcp_manager.get_all_tools()

    # 어떤 mcp 툴을 사용할지 llm이 결정
    llm_response = await call_llm(query, tools_info)

    # llm으로부터 바로 응답이 온 경우
    if llm_response.get("type") == "text":
        return SearchResponse(answer=llm_response.get("content", ""))
    # mcp를 사용해야 하는 경우
    elif llm_response.get("type") == "rpc":
        # mcp 서버의 툴 호출출
        mcp_result = await mcp_manager.call_tool(
            llm_response["srvId"],
            llm_response["method"],
            llm_response.get("params", {})
        )

        # mcp 서버 호출 결과 요약약
        summarized = await summarize_with_llm(mcp_result, prompt=query)

        answer = summarized.get("results", [{}])[0].get("description", "")
        return SearchResponse(answer=answer)
    else:
        logger.error("Invalid LLM response: %s", llm_response)
        raise HTTPException(status_code=400, detail="Invalid LLM response")