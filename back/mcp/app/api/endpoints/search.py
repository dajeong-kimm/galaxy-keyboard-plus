from fastapi import APIRouter, HTTPException, Request
from app.models.schemas import SearchRequest, SearchResponse
from app.utils.llm import call_llm, summarize_with_llm
import logging
import time

logger = logging.getLogger(__name__)
router = APIRouter()

@router.post(
    "/",
    response_model=SearchResponse,
    summary="검색",
    description="사용자 질문에 대해 검색을 수행합니다.",
    response_description="검색 결과"
)
async def search_endpoint(
    request: Request,          # FastAPI Request 객체 (app, state, 등 접근)
    body: SearchRequest        # 실제 요청 body는 body에!
    ):
    # 시간 측정
    start = time.perf_counter()  # or time.time()

    # 사용자의 질문
    query = body.query
    logger.info(f"[search_endpoint] 요청: query={query}")

    # mcp 서버의 툴 목록
    mcp_manager = request.app.state.mcp_manager
    tools_info = mcp_manager.get_all_tools()
    logger.debug(f"[search_endpoint] tools_info: {tools_info}")

    # 어떤 mcp 툴을 사용할지 llm이 결정
    llm_response = await call_llm(query, tools_info)
    logger.info(f"[search_endpoint] call_llm 결과: {llm_response}")
    

    # llm으로부터 바로 응답이 온 경우
    if llm_response.get("type") == "text":
        answer = llm_response.get("content", "")
        elapsed = time.perf_counter() - start
        logger.info(f"[search_endpoint] 응답 (text), 소요 시간: {elapsed:.3f}초")
        return SearchResponse(answer=answer)
    # mcp를 사용해야 하는 경우
    elif llm_response.get("type") == "rpc":
        logger.info(f"[search_endpoint] MCP 호출: {llm_response}")

        # mcp 서버의 툴 호출
        params = llm_response.get("params", {})

        for key in ("count", "num"):
            if key in params:
                try:
                    params[key] = max(1, min(int(params[key]), 2))
                except Exception:
                    params[key] = 1

        # MCP 서버 툴 호출
        mcp_result = await mcp_manager.call_tool(
            llm_response["srvId"],
            llm_response["method"],
            params
        )
        logger.info(f"[search_endpoint] MCP 호출 결과: {str(mcp_result)[:200]}")

        # mcp 서버 호출 결과 요약약
        summarized = await summarize_with_llm(mcp_result, prompt=body.query)
        answer = summarized.get("results", [{}])[0].get("description", "")
        elapsed = time.perf_counter() - start
        logger.info(f"[search_endpoint] 응답 (rpc), 소요 시간: {elapsed:.3f}초")
        return SearchResponse(answer=answer)
    else:
        logger.error("Invalid LLM response: %s", llm_response)
        raise HTTPException(status_code=400, detail="Invalid LLM response")