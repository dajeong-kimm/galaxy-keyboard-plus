from fastapi import FastAPI, File, UploadFile, HTTPException, Body, Query, Path, BackgroundTasks
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
import uvicorn
import datetime
import json
import os
from src.config import settings
from src.core.vector_db_handler import VectorDBHandler

# Pydantic 모델 정의
class ConversationInput(BaseModel):
    content: str
    chat_id: str
    timestamp: Optional[datetime.datetime] = None
    metadata: Optional[Dict[str, Any]] = None

class DocumentInput(BaseModel):
    content: str
    document_name: str
    document_path: Optional[str] = None
    metadata: Optional[Dict[str, Any]] = None

class WorkflowInput(BaseModel):
    steps: List[Dict[str, Any]]
    mcp_name: str
    timestamp: Optional[datetime.datetime] = None
    metadata: Optional[Dict[str, Any]] = None

class SummaryInput(BaseModel):
    content: str
    date: str
    task_name: str
    metadata: Optional[Dict[str, Any]] = None

class SearchQuery(BaseModel):
    query: str
    collections: Optional[List[str]] = None
    limit: Optional[int] = 5
    filters: Optional[Dict[str, Any]] = None

class ChunkUpdateInput(BaseModel):
    document_id: str
    chunk_id: str
    new_content: str

app = FastAPI(
    title="로컬 RAG 시스템",
    description="LLM 대화 및 MCP 작업 내용 관리 시스템",
    version="1.0.0"
)

# 벡터 DB 핸들러 초기화
db_handler = VectorDBHandler(db_path=settings.chroma_persist_dir)

# 헬스 체크 엔드포인트
@app.get("/health", tags=["health"])
async def health_check():
    return {"status": "ok", "version": "1.0.0"}

# 엔드포인트: 대화 저장
@app.post("/conversations/", status_code=201, tags=["conversations"])
async def store_conversation(conversation: ConversationInput):
    try:
        result = db_handler.store_conversation(
            conversation.content,
            conversation.chat_id,
            conversation.timestamp or datetime.datetime.now(),
            conversation.metadata
        )
        return {"status": "success", "stored_chunks": result["stored_chunks"]}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"저장 실패: {str(e)}")

# 엔드포인트: 문서 저장
@app.post("/documents/", status_code=201, tags=["documents"])
async def store_document(document: DocumentInput):
    try:
        result = db_handler.store_document(
            document.content,
            document.document_path,
            document.document_name,
            document.metadata
        )
        return {
            "status": "success", 
            "stored_chunks": result["stored_chunks"],
            "summary": result["summary"]
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"저장 실패: {str(e)}")

# 엔드포인트: 파일 업로드 및 저장
@app.post("/documents/upload/", status_code=201, tags=["documents"])
async def upload_document(
    file: UploadFile = File(...),
    document_name: Optional[str] = None,
    store_path: Optional[str] = None,
    metadata: Optional[str] = None
):
    try:
        # 파일 저장 및 처리
        content = await file.read()
        document_name = document_name or file.filename
        metadata_dict = json.loads(metadata) if metadata else {}
        
        # 텍스트 파일 처리
        if file.content_type.startswith('text/') or file.filename.endswith(('.txt', '.md', '.py', '.json')):
            text_content = content.decode('utf-8')
        else:
            # 바이너리 파일은 경로만 저장하고 요약 처리
            text_content = f"Binary file: {file.filename}"
            # 여기서 필요하면 파일 저장 로직 추가
        
        result = db_handler.store_document(
            text_content,
            store_path or f"uploads/{file.filename}",
            document_name,
            metadata_dict
        )
        
        return {
            "status": "success",
            "filename": file.filename,
            "stored_chunks": result["stored_chunks"],
            "summary": result["summary"]
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"파일 업로드 실패: {str(e)}")

# 엔드포인트: 워크플로우 저장
@app.post("/workflows/", status_code=201, tags=["workflows"])
async def store_workflow(workflow: WorkflowInput):
    try:
        result = db_handler.store_workflow(
            workflow.steps,
            workflow.mcp_name,
            workflow.timestamp or datetime.datetime.now(),
            workflow.metadata
        )
        return {"status": "success", "workflow_id": result["workflow_id"]}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"저장 실패: {str(e)}")

# 엔드포인트: 요약 저장
@app.post("/summaries/", status_code=201, tags=["summaries"])
async def store_summary(summary: SummaryInput):
    try:
        result = db_handler.store_summary(
            summary.content,
            summary.date,
            summary.task_name,
            summary.metadata
        )
        return {"status": "success", "summary_id": result["summary_id"]}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"저장 실패: {str(e)}")

# 엔드포인트: 통합 검색
@app.post("/search/", tags=["search"])
async def search(query: SearchQuery):
    try:
        results = db_handler.enhanced_search(
            query.query,
            collections=query.collections,
            limit=query.limit,
            filters=query.filters
        )
        return {"results": results}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"검색 실패: {str(e)}")

# 엔드포인트: 특정 대화 전체 조회
@app.get("/conversations/{chat_id}", tags=["conversations"])
async def get_full_conversation(chat_id: str):
    try:
        result = db_handler.retrieve_full_content(chat_id, content_type="conversation")
        return {"chat_id": chat_id, "content": result}
    except Exception as e:
        raise HTTPException(status_code=404, detail=f"대화를 찾을 수 없습니다: {str(e)}")

# 엔드포인트: 특정 문서 전체 조회
@app.get("/documents/{document_name}", tags=["documents"])
async def get_full_document(document_name: str):
    try:
        result = db_handler.retrieve_full_content(document_name, content_type="document")
        return {"document_name": document_name, "content": result}
    except Exception as e:
        raise HTTPException(status_code=404, detail=f"문서를 찾을 수 없습니다: {str(e)}")

# 엔드포인트: 특정 워크플로우 조회
@app.get("/workflows/{workflow_id}", tags=["workflows"])
async def get_workflow(workflow_id: str):
    try:
        result = db_handler.retrieve_full_content(workflow_id, content_type="workflow")
        return {"workflow_id": workflow_id, "content": result}
    except Exception as e:
        raise HTTPException(status_code=404, detail=f"워크플로우를 찾을 수 없습니다: {str(e)}")

# 엔드포인트: 청크 업데이트
@app.put("/chunks/update/", tags=["admin"])
async def update_chunk(update_data: ChunkUpdateInput):
    try:
        result = db_handler.update_chunk(
            update_data.document_id,
            update_data.chunk_id,
            update_data.new_content
        )
        return {"status": "success", "updated": result["updated"]}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"청크 업데이트 실패: {str(e)}")

# 엔드포인트: 컬렉션 목록 조회
@app.get("/collections/", tags=["admin"])
async def list_collections():
    try:
        collections = db_handler.list_collections()
        return {"collections": collections}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"컬렉션 조회 실패: {str(e)}")

# 엔드포인트: 요약 생성
@app.post("/generate-summary/", tags=["utils"])
async def generate_summary(
    content: str = Body(...),
    content_type: str = Body(...),
    max_length: Optional[int] = Body(500)
):
    try:
        summary = db_handler.generate_summary(content, content_type, max_length)
        return {"summary": summary}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"요약 생성 실패: {str(e)}")

# 엔드포인트: MCP 작업 목록 조회
@app.get("/workflows/mcp/{mcp_name}", tags=["workflows"])
async def get_mcp_workflows(mcp_name: str):
    try:
        # 특정 MCP 관련 워크플로우 검색
        results = db_handler.enhanced_search(
            query=mcp_name,
            collections=["workflows"],
            filters={"mcp_name": mcp_name}
        )
        return {"mcp_name": mcp_name, "workflows": results}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"MCP 작업 조회 실패: {str(e)}")

# 엔드포인트: 특정 날짜 작업 요약 조회
@app.get("/summaries/date/{date}", tags=["summaries"])
async def get_date_summaries(date: str):
    try:
        # 특정 날짜 요약 검색
        results = db_handler.enhanced_search(
            query="",  # 빈 쿼리로 필터링만 수행
            collections=["summaries"],
            filters={"date": date}
        )
        return {"date": date, "summaries": results}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"날짜별 요약 조회 실패: {str(e)}")

if __name__ == "__main__":
    uvicorn.run(
        app,
        host=settings.host,
        port=settings.port,
        reload=settings.debug
    )