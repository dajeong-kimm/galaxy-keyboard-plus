# src/app.py

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime

from src.config import settings
from src.core.embeddings import get_embedder
from src.core.qdrant_store import get_qdrant_store
from src.core.llm import get_llm
from src.core.rag import get_rag_chain
from qdrant_client.models import Distance, VectorParams

class MessageRequest(BaseModel):
    session_id: str = Field(..., description="세션 UUID")
    user_id: Optional[str] = Field(None, description="사용자 ID")
    text: str = Field(..., description="메시지 내용")
    timestamp: Optional[datetime] = Field(
        None, description="ISO8601 형식의 타임스탬프, 없으면 서버 시간이 사용됨"
    )

class QueryRequest(BaseModel):
    session_id: str = Field(..., description="세션 UUID")
    question: str = Field(..., description="사용자 질문")
    topic_id: Optional[str] = Field(None, description="토픽 필터 ID (선택)")

class DocsRequest(BaseModel):
    session_id: str = Field(..., description="세션 UUID")
    docs: list[str] = Field(..., description="추가할 문서 리스트")

app = FastAPI(
    title="Local RAG Service",
    version="0.1.0",
    description="UUID 기반 Qdrant + 외부 LLM RAG API",
)

@app.on_event("startup")
def startup_event():
    # 1) 임베딩 모델 초기화
    embedder = get_embedder(settings.embed_model)

    # 2) Qdrant 벡터스토어 초기화
    store = get_qdrant_store(settings, embedder)

    # 3) 외부 LLM 초기화
    llm = get_llm()

    # 4) RAG 체인 등록
    app.state.get_chain = lambda session_id, topic_id=None: get_rag_chain(
        llm=llm,
        vectorstore=store,
        session_id=session_id,
        k=settings.rag_k,
        topic_id=topic_id,
    )

    # 5) 벡터스토어 전역 저장
    app.state.vectorstore = store

@app.get("/health", tags=["health"])
async def health_check():
    return {"status": "ok"}

@app.post("/v1/add_message", tags=["messages"])
async def add_message(req: MessageRequest):
    try:
        ts = req.timestamp or datetime.utcnow()
        app.state.vectorstore.add_texts(
            [req.text],
            metadatas=[{
                "session_id": req.session_id,
                "user_id": req.user_id,
                "timestamp": ts.isoformat(),
                "source": "claude-desktop"
            }]
        )
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/v1/add_docs", tags=["docs"])
async def add_docs_endpoint(req: DocsRequest):
    try:
        app.state.vectorstore.add_texts(
            req.docs,
            metadatas=[
                {"session_id": req.session_id, "source": "doc"}
                for _ in req.docs
            ]
        )
        return {"status": "ok", "count": len(req.docs)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/v1/query", tags=["query"])
async def query_endpoint(req: QueryRequest):
    try:
        answer = app.state.get_chain(req.session_id, req.topic_id).run(req.question)
        return {"answer": answer}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))