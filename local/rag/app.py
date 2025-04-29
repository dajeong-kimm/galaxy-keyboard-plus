# src/app.py
from datetime import datetime
from typing import Optional, List
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from langchain_community.chat_models import ChatOpenAI   # 외부 LLM
from src.config import settings
from src.core.embeddings import get_embedder
from src.core.chroma_store import get_chroma_store
from src.core.rag import get_rag_chain

# ────────────────────────── DTO ──────────────────────────
class MessageRequest(BaseModel):
    session_id: str
    user_id: Optional[str] = None
    text: str
    timestamp: Optional[datetime] = None


class DocsRequest(BaseModel):
    session_id: str
    docs: List[str]


class QueryRequest(BaseModel):
    session_id: str
    question: str
    topic_id: Optional[str] = None


class ItemRequest(BaseModel):
    """
    모든 아이템을 한 번에 저장하는 DTO
    - text 또는 uri 중 하나 이상이 있어야 함
    """
    session_id: str
    item_id: str
    source: str                                 # e.g. "user_message", "llm_response", "doc", "sheet"
    uri: Optional[str] = None                   # MCP 서버를 통해 로드할 리소스 URI
    text: Optional[str] = None                  # 직접 전달할 텍스트
    timestamp: Optional[datetime] = None


class ContextRequest(BaseModel):
    session_id: str
    question: str
    k: int = Field(settings.rag_k, description="검색할 컨텍스트 개수")
    topic_id: Optional[str] = None


# ─────────────────────── FastAPI 앱 ──────────────────────
app = FastAPI(
    title="Local RAG Service (OpenAI LLM)",
    version="0.2.0",
    description="ChromaDB + OpenAI Chat 모델 기반 RAG API",
)


# ─────────────────────── 스타트업 ────────────────────────
@app.on_event("startup")
def startup_event() -> None:
    # 1) 임베딩 인스턴스
    embedder = get_embedder(settings.embed_model)

    # 2) Chroma 벡터 스토어
    store = get_chroma_store(embedder)

    # 3) 외부 LLM: OpenAI Chat
    llm = ChatOpenAI(
        api_key=settings.openai_api_key,
        model_name=settings.openai_model_name,
        temperature=0.0,
    )

    # 4) 세션별 RAG 체인 팩토리
    app.state.get_chain = lambda sess_id, topic_id=None: get_rag_chain(
        llm=llm,
        vectorstore=store,
        chat_id=sess_id,
        k=settings.rag_k,
        topic_id=topic_id,
    )

    # 5) 전역 저장
    app.state.vectorstore = store


# ─────────────────────── 엔드포인트 ──────────────────────
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
                "chat_id": req.session_id,
                "user_id": req.user_id,
                "timestamp": ts.isoformat(),
                "source": "user_message",
            }],
        )
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/v1/add_docs", tags=["docs"])
async def add_docs(req: DocsRequest):
    try:
        app.state.vectorstore.add_texts(
            req.docs,
            metadatas=[{
                "chat_id": req.session_id,
                "source": "doc",
            } for _ in req.docs],
        )
        return {"status": "ok", "count": len(req.docs)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/v1/add_item", tags=["items"])
async def add_item(req: ItemRequest):
    """
    모든 유형의 아이템(user_message, llm_response, doc, sheet, ppt 등)을 저장
    text가 있으면 text로, uri만 있으면 MCP로 로드한 후 chunk → 임베딩
    """
    ts = (req.timestamp or datetime.utcnow()).isoformat()
    if not (req.text or req.uri):
        raise HTTPException(status_code=400, detail="text 또는 uri 중 하나는 필수입니다.")
    try:
        # 1) 메타데이터로만 임베딩 추가
        content = req.text or ""
        # 만약 uri만 있고 text가 없다면, 서버 사이드에서 MCP를 호출해 파일 읽어오는 로직을 여기에 추가하세요.
        app.state.vectorstore.add_texts(
            [content],
            metadatas=[{
                "chat_id": req.session_id,
                "item_id": req.item_id,
                "source": req.source,
                "uri": req.uri or "",
                "timestamp": ts,
            }],
        )
        return {"status": "ok", "item_id": req.item_id}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"add_item 실패: {e}")


@app.post("/v1/context", tags=["context"])
async def get_context(req: ContextRequest):
    """
    session_id와 question을 받아서,
    벡터 DB에서 k개의 관련 컨텍스트(텍스트 chunk)만 돌려줍니다.
    """
    metadata_filter = {"chat_id": req.session_id}
    if req.topic_id:
        metadata_filter["topic_id"] = req.topic_id

    try:
        docs = app.state.vectorstore.similarity_search(
            req.question,
            k=req.k,
            filter=metadata_filter
        )
        return {"contexts": [d.page_content for d in docs]}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"context 조회 실패: {e}")


@app.post("/v1/query", tags=["query"])
async def query(req: QueryRequest):
    try:
        chain = app.state.get_chain(req.session_id, req.topic_id)
        answer = chain.run(req.question)
        return {"answer": answer}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
