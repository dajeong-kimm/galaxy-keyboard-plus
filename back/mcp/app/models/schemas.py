from typing import Dict, Any, List, Optional
from pydantic import BaseModel, Field

class SearchRequest(BaseModel):
    """웹 검색 요청 모델"""
    query: str = Field(..., description="검색 쿼리")
    num_results: int = Field(5, ge=1, le=10, description="반환할 결과 개수")
    
    class Config:
        schema_extra = {
            "example": {
                "query": "인공지능 기술",
                "num_results": 5
            }
        }

class SearchResultItem(BaseModel):
    """단일 검색 결과 항목"""
    title: str = Field(..., description="검색 결과 제목")
    url: str = Field(..., description="검색 결과 URL")
    description: Optional[str] = Field(None, description="검색 결과 설명")

class SearchResponse(BaseModel):
    """웹 검색 응답 모델"""
    results: List[SearchResultItem] = Field(..., description="검색 결과")
    query: str = Field(..., description="검색된 쿼리")