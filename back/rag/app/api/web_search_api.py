from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Dict, Any, Optional

from app.utils.mcp_web_research import (
    mcp_web_research,
    web_search,
    MCP_WEB_RESEARCH_ENABLED,
)
import logging

router = APIRouter()


class WebSearchRequest(BaseModel):
    query: str


class WebSearchResponse(BaseModel):
    result: str


@router.post("/web-search", response_model=WebSearchResponse)
async def api_web_search(request: WebSearchRequest):
    """웹 검색 API 엔드포인트"""
    if (
        not MCP_WEB_RESEARCH_ENABLED
        or not mcp_web_research
        or not mcp_web_research.initialized
    ):
        raise HTTPException(
            status_code=503, detail="웹 검색 기능이 활성화되지 않았습니다."
        )

    logging.info(f"🔍 웹 검색 요청: {request.query}")

    try:
        # 웹 검색 수행 및 결과 반환
        result = web_search(request.query)
        return {"result": result}
    except Exception as e:
        logging.error(f"❌ 웹 검색 중 오류 발생: {str(e)}")
        raise HTTPException(status_code=500, detail=f"웹 검색 중 오류 발생: {str(e)}")
