# src/app.py

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from src.config import settings
from src.core.embeddings import get_embedder
from src.core.qdrant_store import get_qdrant_store
from src.core.llm import get_llm
from src.core.rag import get_rag_chain
from qdrant_client.models import Distance, VectorParams

class QueryRequest(BaseModel):
    chat_id: str = Field(..., description="채팅 UUID")
    question: str = Field(..., description="사용자 질문")

class DocsRequest(BaseModel):
    chat_id: str = Field(..., description="채팅 UUID")
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

    # 2-a) 컬렉션이 이미 있으면 삭제하고, 새로 생성
    dim = len(embedder.embed_query("__init__"))
    store.client.recreate_collection(
        collection_name=settings.qdrant_collection,
        vectors_config=VectorParams(size=dim, distance=Distance.COSINE),
    )

    # 3) 외부 LLM 초기화 (OpenAI ChatCompletion)
    llm = get_llm()

    # 4) RAG 체인 등록
    app.state.get_chain = lambda chat_id: get_rag_chain(
        llm=llm,
        vectorstore=store,
        chat_id=chat_id,
        k=settings.rag_k,
    )

    # 5) 벡터스토어 전역 저장
    app.state.vectorstore = store

@app.get("/health", tags=["health"])
async def health_check():
    return {"status": "ok"}

@app.post("/v1/add_docs", tags=["docs"])
async def add_docs_endpoint(req: DocsRequest):
    try:
        app.state.vectorstore.add_texts(
            req.docs,
            metadatas=[{"chat_id": req.chat_id, "source": "doc"} for _ in req.docs]
        )
        return {"status": "ok", "count": len(req.docs)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/v1/query", tags=["query"])
async def query_endpoint(req: QueryRequest):
    try:
        answer = app.state.get_chain(req.chat_id).run(req.question)
        return {"answer": answer}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
