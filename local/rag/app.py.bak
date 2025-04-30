from datetime import datetime, timedelta
from typing import Optional, List
from fastapi import FastAPI, HTTPException, BackgroundTasks
from pydantic import BaseModel, Field
import sqlite3
import time

from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain_community.chat_models import ChatOpenAI
from langchain.chains import RetrievalQA
from langchain.prompts import PromptTemplate
from langchain.retrievers.self_query.base import SelfQueryRetriever
from langchain.chains.query_constructor.base import AttributeInfo

from src.config import settings
from src.core.embeddings import get_embedder
from src.core.chroma_store import get_chroma_store
from src.core.rag import get_rag_chain, QUESTION_PROMPT, REFINE_PROMPT

app = FastAPI(
    title="Local RAG Service (OpenAI LLM)",
    version="0.2.0",
    description="ChromaDB + OpenAI Chat 모델 기반 RAG API",
)

# DTOs
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

class UniversalQueryRequest(BaseModel):
    question: str
    current_session_id: str
    search_mode: str = "auto"
    time_info: Optional[str] = None
    k: int = Field(settings.rag_k, description="검색할 컨텍스트 개수")

class ItemRequest(BaseModel):
    session_id: str
    item_id: str
    source: str
    uri: Optional[str] = None
    text: Optional[str] = None
    timestamp: Optional[datetime] = None

class ContextRequest(BaseModel):
    session_id: str
    question: str
    k: int = Field(settings.rag_k, description="검색할 컨텍스트 개수")
    topic_id: Optional[str] = None

# SQLite helper
def get_sqlite_connection():
    conn = sqlite3.connect("sqlite.db")
    conn.row_factory = sqlite3.Row
    conn.execute('''
    CREATE TABLE IF NOT EXISTS chat_messages (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        session_id TEXT NOT NULL,
        role TEXT NOT NULL,
        content TEXT NOT NULL,
        timestamp TEXT NOT NULL,
        created_at TEXT DEFAULT CURRENT_TIMESTAMP
    )
    ''')
    return conn

# Time filter for universal_query
def create_time_filter(time_info: str = None):
    if not time_info:
        return {}
    now = datetime.utcnow()
    if time_info == "어제":
        yesterday = (now - timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0)
        today = now.replace(hour=0, minute=0, second=0, microsecond=0)
        return {"timestamp": {"$gte": yesterday.isoformat(), "$lt": today.isoformat()}}
    elif time_info == "오늘":
        today_start = now.replace(hour=0, minute=0, second=0, microsecond=0)
        return {"timestamp": {"$gte": today_start.isoformat()}}
    elif time_info == "지난주":
        week_start = (now - timedelta(days=now.weekday() + 7)).replace(hour=0, minute=0, second=0, microsecond=0)
        week_end = (now - timedelta(days=now.weekday())).replace(hour=0, minute=0, second=0, microsecond=0)
        return {"timestamp": {"$gte": week_start.isoformat(), "$lt": week_end.isoformat()}}
    elif time_info == "지난달":
        last_month = now.replace(day=1) - timedelta(days=1)
        month_start = last_month.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
        month_end = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
        return {"timestamp": {"$gte": month_start.isoformat(), "$lt": month_end.isoformat()}}
    return {}

# Startup event
@app.on_event("startup")
def startup_event() -> None:
    embedder = get_embedder(settings.embed_model)
    store = get_chroma_store(embedder)
    llm = ChatOpenAI(
        api_key=settings.openai_api_key,
        model_name=settings.openai_model_name,
        temperature=0.0,
    )
    app.state.get_chain = lambda sess_id, topic_id=None: get_rag_chain(
        llm=llm,
        vectorstore=store,
        chat_id=sess_id,
        k=settings.rag_k,
        topic_id=topic_id,
    )
    app.state.text_splitter = RecursiveCharacterTextSplitter(
        chunk_size=settings.chunk_size,
        chunk_overlap=settings.chunk_overlap,
        separators=["\n\n", "\n", ".", "!", "?", ",", " ", ""]
    )
    app.state.vectorstore = store
    app.state.llm = llm
    app.state.embedder = embedder

# Health check
@app.get("/health", tags=["health"])
async def health_check():
    return {"status": "ok"}

# Add user message
@app.post("/v1/add_message", tags=["messages"])
async def add_message(req: MessageRequest, background_tasks: BackgroundTasks):
    ts = req.timestamp or datetime.utcnow()
    conn = get_sqlite_connection()  # Save to SQLite
    conn.execute(
        "INSERT INTO chat_messages (session_id, role, content, timestamp) VALUES (?, ?, ?, ?)",
        (req.session_id, "user", req.text, ts.isoformat())
    )
    conn.commit()
    conn.close()

    chunks = app.state.text_splitter.split_text(req.text)
    metadatas = [{
        "chat_id": req.session_id,
        "user_id": req.user_id,
        "timestamp": ts.isoformat(),
        "source": "user_message",
        "chunk_index": i,
        "total_chunks": len(chunks)
    } for i in range(len(chunks))]

    background_tasks.add_task(app.state.vectorstore.add_texts, chunks, metadatas)
    return {"status": "ok", "message_saved": True, "chunks_added": len(chunks)}

# Add docs
@app.post("/v1/add_docs", tags=["docs"])
async def add_docs(req: DocsRequest, background_tasks: BackgroundTasks):
    all_chunks, all_metadatas = [], []
    for doc_index, doc in enumerate(req.docs):
        chunks = app.state.text_splitter.split_text(doc)
        all_chunks.extend(chunks)
        metadatas = [{
            "chat_id": req.session_id,
            "source": "doc",
            "doc_index": doc_index,
            "chunk_index": i,
            "total_chunks": len(chunks)
        } for i in range(len(chunks))]
        all_metadatas.extend(metadatas)
    background_tasks.add_task(app.state.vectorstore.add_texts, all_chunks, all_metadatas)
    return {"status": "ok", "docs_count": len(req.docs), "chunks_count": len(all_chunks)}

# Add item
@app.post("/v1/add_item", tags=["items"])
async def add_item(req: ItemRequest, background_tasks: BackgroundTasks):
    ts = (req.timestamp or datetime.utcnow()).isoformat()
    if not (req.text or req.uri):
        raise HTTPException(status_code=400, detail="text 또는 uri 중 하나는 필수입니다.")
    if req.uri and not req.text:
        raise HTTPException(status_code=400, detail="URI 전용 항목은 현재 지원되지 않습니다.")

    chunks = app.state.text_splitter.split_text(req.text or "")
    metadatas = [{
        "chat_id": req.session_id,
        "item_id": req.item_id,
        "source": req.source,
        "uri": req.uri or "",
        "timestamp": ts,
        "chunk_index": i,
        "total_chunks": len(chunks)
    } for i in range(len(chunks))]
    background_tasks.add_task(app.state.vectorstore.add_texts, chunks, metadatas)
    return {"status": "ok", "item_id": req.item_id, "chunks_added": len(chunks)}

# Save assistant message
@app.post("/v1/save_assistant_message", tags=["messages"])
async def save_assistant_message(
    session_id: str,
    content: str,
    background_tasks: BackgroundTasks,
    timestamp: Optional[datetime] = None
):
    ts = timestamp or datetime.utcnow()
    conn = get_sqlite_connection()
    conn.execute(
        "INSERT INTO chat_messages (session_id, role, content, timestamp) VALUES (?, ?, ?, ?)",
        (session_id, "assistant", content, ts.isoformat())
    )
    conn.commit()
    conn.close()

    chunks = app.state.text_splitter.split_text(content)
    metadatas = [{
        "chat_id": session_id,
        "source": "assistant_message",
        "timestamp": ts.isoformat(),
        "chunk_index": i,
        "total_chunks": len(chunks)
    } for i in range(len(chunks))]
    background_tasks.add_task(app.state.vectorstore.add_texts, chunks, metadatas)
    return {"status": "ok", "message_saved": True, "chunks_added": len(chunks)}

# Get context
@app.post("/v1/context", tags=["context"])
async def get_context(req: ContextRequest):
    metadata_filter = {"chat_id": req.session_id}
    if req.topic_id:
        metadata_filter["topic_id"] = req.topic_id
    try:
        docs = app.state.vectorstore.similarity_search(req.question, k=req.k, filter=metadata_filter)
        return {"contexts": [d.page_content for d in docs]}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"context 조회 실패: {e}")

# Query
@app.post("/v1/query", tags=["query"])
async def query(req: QueryRequest):
    try:
        chain = app.state.get_chain(req.session_id, req.topic_id)
        answer = chain.run(req.question)
        return {"answer": answer}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

# Universal query
@app.post("/v1/universal_query", tags=["query"])
async def universal_query(req: UniversalQueryRequest):
    """
    랭체인을 활용한 통합 검색+응답 엔드포인트
    """
    try:
        # 1) 시간 필터 생성 (벡터DB 단계에서는 사용하지 않음)
        time_filter = create_time_filter(req.time_info)

        # 2) 검색 모드 분기
        if req.search_mode == "current":
            # 현재 세션만 검색
            metadata_filter = {"chat_id": req.current_session_id}
            retriever = app.state.vectorstore.as_retriever(
                search_type="mmr",
                search_kwargs={
                    "k": req.k,
                    "filter": metadata_filter,
                    "fetch_k": req.k * 2,
                    "lambda_mult": 0.7
                }
            )

        elif req.search_mode == "all":
            # 전체 세션 검색 (시간 필터는 애플리케이션 레벨 후처리로 대체)
            retriever = app.state.vectorstore.as_retriever(
                search_type="mmr",
                search_kwargs={
                    "k": req.k,
                    "filter": None,
                    "fetch_k": req.k * 2,
                    "lambda_mult": 0.7
                }
            )

        else:  # auto
            # SelfQueryRetriever 사용 (lark 필요)
            metadata_field_info = [
                AttributeInfo(name="chat_id", description="Session ID", type="string"),
                AttributeInfo(name="source", description="Source of text", type="string"),
                AttributeInfo(name="timestamp", description="ISO timestamp", type="string"),
            ]
            document_content_description = "User and AI conversation text or document text"
            retriever = SelfQueryRetriever.from_llm(
                llm=app.state.llm,
                vectorstore=app.state.vectorstore,
                document_contents=document_content_description,
                metadata_field_info=metadata_field_info,
                verbose=False
            )
            # 기본 필터 설정 (세션)
            default_filter = {"chat_id": req.current_session_id}
            retriever.default_filter = default_filter

        # 3) RetrievalQA 체인 구성
        qa_prompt = PromptTemplate(
            input_variables=["context_str", "question"],
            template=(
                "당신은 사용자의 로컬 데이터를 관리하는 AI 비서입니다.\n\n"
                "# 검색된 컨텍스트:\n{context_str}\n\n"
                "# 질문:\n{question}\n\n"
                "1. 제공된 컨텍스트만 사용해 답하세요.\n"
                "2. 정보가 없으면 '컨텍스트에 관련 정보가 없습니다'라고 답하세요.\n"
                "답변:"
            )
        )
        qa_chain = RetrievalQA.from_chain_type(
            llm=app.state.llm,
            chain_type="stuff",
            retriever=retriever,
            chain_type_kwargs={
                "prompt": qa_prompt,
                "document_variable_name": "context_str"
            },
            return_source_documents=False
        )

        # 4) 질의 실행
        result = qa_chain.invoke({"query": req.question})
        return {"answer": result["result"]}

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"universal_query 처리 실패: {e}")



# Chat history
@app.get("/v1/chat_history/{session_id}", tags=["chat"])
async def get_chat_history(session_id: str, limit: int = 50, offset: int = 0):
    try:
        conn = get_sqlite_connection()
        rows = conn.execute(
            "SELECT * FROM chat_messages WHERE session_id = ? ORDER BY timestamp ASC LIMIT ? OFFSET ?",
            (session_id, limit, offset)
        ).fetchall()
        conn.close()
        return {"messages": [dict(r) for r in rows]}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"채팅 내역 조회 실패: {e}")

# Delete chat
@app.delete("/v1/delete_chat/{session_id}", tags=["chat"])
async def delete_chat(session_id: str, background_tasks: BackgroundTasks):
    try:
        conn = get_sqlite_connection()
        deleted = conn.execute(
            "DELETE FROM chat_messages WHERE session_id = ?",
            (session_id,)
        ).rowcount
        conn.commit()
        conn.close()
        background_tasks.add_task(
            lambda sid=session_id: app.state.vectorstore.delete(where={"chat_id": sid})
        )
        return {"status": "ok", "deleted_messages": deleted}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"채팅방 삭제 실패: {e}")

# Cleanup chat history
@app.post("/v1/cleanup_chat_history", tags=["admin"])
async def cleanup_chat_history(background_tasks: BackgroundTasks, days: int = 30, inactive_only: bool = True):
    try:
        conn = get_sqlite_connection()
        cutoff = (datetime.utcnow() - timedelta(days=days)).isoformat()
        if inactive_only:
            rows = conn.execute(
                "SELECT DISTINCT session_id FROM chat_messages GROUP BY session_id HAVING MAX(timestamp) < ?", 
                (cutoff,)
            ).fetchall()
            ids = [r['session_id'] for r in rows]
            if ids:
                placeholders = ','.join(['?']*len(ids))
                deleted = conn.execute(
                    f"DELETE FROM chat_messages WHERE session_id IN ({placeholders})", ids
                ).rowcount
                conn.commit()
                conn.close()
                for sid in ids:
                    background_tasks.add_task(
                        lambda s=sid: app.state.vectorstore.delete(where={"chat_id": s})
                    )
                return {"status": "ok", "deleted_count": deleted}
            else:
                conn.close()
                return {"status": "ok", "deleted_count": 0}
        else:
            deleted = conn.execute(
                "DELETE FROM chat_messages WHERE timestamp < ?", (cutoff,)
            ).rowcount
            conn.commit()
            conn.close()
            return {"status": "ok", "deleted_count": deleted}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"채팅 내역 정리 실패: {e}")

# Reset SQLite
@app.post("/v1/reset_sqlite", tags=["admin"])
async def reset_sqlite():
    import os
    try:
        path = "./sqlite.db"
        if os.path.exists(path):
            os.remove(path)
        conn = sqlite3.connect(path)
        conn.execute('''
        CREATE TABLE IF NOT EXISTS chat_messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id TEXT NOT NULL,
            role TEXT NOT NULL,
            content TEXT NOT NULL,
            timestamp TEXT NOT NULL,
            created_at TEXT DEFAULT CURRENT_TIMESTAMP
        )
        ''')
        conn.commit()
        conn.close()
        return {"status": "ok", "message": "SQLite reset 성공"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"SQLite reset 실패: {e}")