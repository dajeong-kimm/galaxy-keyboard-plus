from typing import Dict, Any, List, Optional
from pydantic import BaseModel, Field

class SearchRequest(BaseModel):
    """웹 검색 요청 모델"""
    query: str = Field(..., description="검색 쿼리")
    
    class Config:
        schema_extra = {
            "example": {
                "query": "인공지능 기술"
            }
        }

class SearchResponse(BaseModel):
    answer: str = Field(..., description="최종 답변")