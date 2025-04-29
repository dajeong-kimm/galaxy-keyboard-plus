# src/app.py
from datetime import datetime, timedelta
from typing import Optional, List
from fastapi import FastAPI, HTTPException, BackgroundTasks
from pydantic import BaseModel, Field
import sqlite3
import json
import asyncio
import time
from enum import Enum
from functools import lru_cache

from langchain.text_splitter import RecursiveCharacterTextSplitter
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


class MessageRole(str, Enum):
    USER = "user"
    ASSISTANT = "assistant"


class ChatMessageRequest(BaseModel):
    """채팅 메시지 저장 요청"""
    session_id: str
    role: MessageRole
    content: str
    timestamp: Optional[datetime] = None


# ─────────────────────── FastAPI 앱 ──────────────────────
app = FastAPI(
    title="Local RAG Service (OpenAI LLM)",
    version="0.2.0",
    description="ChromaDB + OpenAI Chat 모델 기반 RAG API",
)


# ─────────────────────── SQLite 헬퍼 함수 ────────────────────────
def get_sqlite_connection():
    conn = sqlite3.connect("sqlite.db")
    conn.row_factory = sqlite3.Row
    
    # 메시지 테이블 생성 (없는 경우)
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


# ─────────────────────── 벡터 스토어 함수 ────────────────────────
async def add_to_vectorstore_async(text, metadata):
    # 별도의 스레드에서 실행하여 메인 스레드 차단 방지
    loop = asyncio.get_event_loop()
    await loop.run_in_executor(
        None,
        lambda: app.state.vectorstore.add_texts([text], metadatas=[metadata])
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

    # 5) 텍스트 스플리터 초기화
    app.state.text_splitter = RecursiveCharacterTextSplitter(
        chunk_size=settings.chunk_size,
        chunk_overlap=settings.chunk_overlap,
        separators=["\n\n", "\n", ".", "!", "?", ",", " ", ""]
    )

    # 6) 전역 저장
    app.state.vectorstore = store


# ─────────────────────── 엔드포인트 ──────────────────────
@app.get("/health", tags=["health"])
async def health_check():
    return {"status": "ok"}


@app.post("/v1/add_message", tags=["messages"])
async def add_message(req: MessageRequest, background_tasks: BackgroundTasks):
    try:
        ts = req.timestamp or datetime.utcnow()
        
        # 1. SQLite에 채팅 메시지 저장
        conn = get_sqlite_connection()
        conn.execute(
            "INSERT INTO chat_messages (session_id, role, content, timestamp) VALUES (?, ?, ?, ?)",
            (req.session_id, "user", req.text, ts.isoformat())
        )
        conn.commit()
        conn.close()
        
        # 2. 텍스트 분할
        chunks = app.state.text_splitter.split_text(req.text)
        
        # 3. 각 청크에 대한 메타데이터 준비
        metadatas = [{
            "chat_id": req.session_id,
            "user_id": req.user_id,
            "timestamp": ts.isoformat(),
            "source": "user_message",
            "chunk_index": i,
            "total_chunks": len(chunks)
        } for i in range(len(chunks))]
        
        # 4. 백그라운드 작업으로 벡터 저장소에 텍스트 추가
        background_tasks.add_task(
            lambda: app.state.vectorstore.add_texts(chunks, metadatas=metadatas)
        )
        
        return {"status": "ok", "message_saved": True, "chunks_added": len(chunks)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/v1/add_docs", tags=["docs"])
async def add_docs(req: DocsRequest, background_tasks: BackgroundTasks):
    try:
        all_chunks = []
        all_metadatas = []
        
        # 각 문서를 청크로 분할하고 메타데이터 준비
        for doc_index, doc in enumerate(req.docs):
            chunks = app.state.text_splitter.split_text(doc)
            all_chunks.extend(chunks)
            
            # 각 청크에 대한 메타데이터
            metadatas = [{
                "chat_id": req.session_id,
                "source": "doc",
                "doc_index": doc_index,
                "chunk_index": i,
                "total_chunks": len(chunks)
            } for i in range(len(chunks))]
            all_metadatas.extend(metadatas)
        
        # 백그라운드 작업으로 벡터 저장소에 텍스트 추가
        background_tasks.add_task(
            lambda: app.state.vectorstore.add_texts(all_chunks, metadatas=all_metadatas)
        )
        
        return {"status": "ok", "docs_count": len(req.docs), "chunks_count": len(all_chunks)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/v1/add_item", tags=["items"])
async def add_item(req: ItemRequest, background_tasks: BackgroundTasks):
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
        
        # 텍스트를 청크로 분할
        chunks = app.state.text_splitter.split_text(content)
        
        # 각 청크에 대한 메타데이터 준비
        metadatas = [{
            "chat_id": req.session_id,
            "item_id": req.item_id,
            "source": req.source,
            "uri": req.uri or "",
            "timestamp": ts,
            "chunk_index": i,
            "total_chunks": len(chunks)
        } for i in range(len(chunks))]
        
        # 백그라운드 작업으로 벡터 저장소에 텍스트 추가
        background_tasks.add_task(
            lambda: app.state.vectorstore.add_texts(chunks, metadatas=metadatas)
        )
        
        return {"status": "ok", "item_id": req.item_id, "chunks_added": len(chunks)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"add_item 실패: {e}")


@app.post("/v1/save_assistant_message", tags=["messages"])
async def save_assistant_message(
    session_id: str, 
    content: str, 
    background_tasks: BackgroundTasks,
    timestamp: Optional[datetime] = None
):
    try:
        ts = timestamp or datetime.utcnow()
        
        # 1. SQLite에 어시스턴트 메시지 저장
        conn = get_sqlite_connection()
        conn.execute(
            "INSERT INTO chat_messages (session_id, role, content, timestamp) VALUES (?, ?, ?, ?)",
            (session_id, "assistant", content, ts.isoformat())
        )
        conn.commit()
        conn.close()
        
        # 2. 텍스트 분할
        chunks = app.state.text_splitter.split_text(content)
        
        # 3. 각 청크에 대한 메타데이터 준비
        metadatas = [{
            "chat_id": session_id,
            "source": "assistant_message",
            "timestamp": ts.isoformat(),
            "chunk_index": i,
            "total_chunks": len(chunks)
        } for i in range(len(chunks))]
        
        # 4. 백그라운드 작업으로 벡터 저장소에 텍스트 추가
        background_tasks.add_task(
            lambda: app.state.vectorstore.add_texts(chunks, metadatas=metadatas)
        )
        
        return {"status": "ok", "message_saved": True, "chunks_added": len(chunks)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"assistant_message 저장 실패: {e}")


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
        start_time = time.time()
        
        # 벡터 검색 시간 측정
        search_start = time.time()
        chain = app.state.get_chain(req.session_id, req.topic_id)
        search_time = time.time() - search_start
        
        # LLM 응답 시간 측정
        llm_start = time.time()
        answer = chain.run(req.question)
        llm_time = time.time() - llm_start
        
        total_time = time.time() - start_time
        
        # 응답에 타이밍 정보 포함 (디버깅용, 필요시 제거)
        return {
            "answer": answer,
            "timing": {
                "search_time": round(search_time, 3),
                "llm_time": round(llm_time, 3),
                "total_time": round(total_time, 3)
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/v1/chat_history/{session_id}", tags=["chat"])
async def get_chat_history(session_id: str, limit: int = 50, offset: int = 0):
    """특정 세션의 채팅 내역 조회"""
    try:
        conn = get_sqlite_connection()
        rows = conn.execute(
            "SELECT * FROM chat_messages WHERE session_id = ? ORDER BY timestamp ASC LIMIT ? OFFSET ?",
            (session_id, limit, offset)
        ).fetchall()
        conn.close()
        return {"messages": [dict(row) for row in rows], "count": len(rows)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"채팅 내역 조회 실패: {e}")


@app.delete("/v1/delete_chat/{session_id}", tags=["chat"])
async def delete_chat(session_id: str, background_tasks: BackgroundTasks):
    """
    채팅방 삭제: 특정 세션 ID의 모든 메시지와 벡터 스토어 데이터 삭제
    """
    try:
        # 1. SQLite에서 채팅 메시지 삭제
        conn = get_sqlite_connection()
        deleted_count = conn.execute(
            "DELETE FROM chat_messages WHERE session_id = ?",
            (session_id,)
        ).rowcount
        conn.commit()
        conn.close()
        
        # 2. 벡터 스토어에서 관련 데이터 삭제 (비동기로 처리)
        # 참고: ChromaDB는 필터 기반 삭제를 지원합니다
        background_tasks.add_task(
            lambda: app.state.vectorstore.delete(
                filter={"chat_id": session_id}
            )
        )
        
        return {
            "status": "ok", 
            "message": f"채팅방 ID {session_id}의 모든 데이터가 삭제되었습니다.", 
            "deleted_messages": deleted_count
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"채팅방 삭제 실패: {e}")


@app.post("/v1/cleanup_chat_history", tags=["admin"])
async def cleanup_chat_history(background_tasks: BackgroundTasks, days: int = 30, inactive_only: bool = True):
    """일정 기간 이상 지난 채팅 내역 삭제"""
    try:
        conn = get_sqlite_connection()
        cutoff_date = (datetime.utcnow() - timedelta(days=days)).isoformat()
        
        if inactive_only:
            # 최근 N일 동안 활동이 없는 세션의 메시지만 삭제
            inactive_sessions = conn.execute(
                """
                SELECT DISTINCT session_id FROM chat_messages 
                GROUP BY session_id
                HAVING MAX(timestamp) < ?
                """, 
                (cutoff_date,)
            ).fetchall()
            
            if not inactive_sessions:
                conn.close()
                return {"status": "ok", "message": "삭제할 비활성 세션이 없습니다.", "deleted_count": 0}
            
            inactive_session_ids = [row['session_id'] for row in inactive_sessions]
            placeholders = ','.join(['?'] * len(inactive_session_ids))
            
            # SQLite에서 메시지 삭제
            deleted = conn.execute(
                f"DELETE FROM chat_messages WHERE session_id IN ({placeholders})",
                inactive_session_ids
            ).rowcount
            
            # 벡터 스토어에서도 삭제 (비동기로 처리)
            for session_id in inactive_session_ids:
                background_tasks.add_task(
                    lambda sid=session_id: app.state.vectorstore.delete(
                        filter={"chat_id": sid}
                    )
                )
        else:
            # 단순히 N일 이상 지난 모든 메시지 삭제 (SQLite만 삭제, 벡터 스토어는 유지)
            deleted = conn.execute(
                "DELETE FROM chat_messages WHERE timestamp < ?",
                (cutoff_date,)
            ).rowcount
        
        conn.commit()
        conn.close()
        return {"status": "ok", "deleted_count": deleted}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"채팅 내역 정리 실패: {e}")


# SQLite 초기화 엔드포인트 
@app.post("/v1/reset_sqlite", tags=["admin"])
async def reset_sqlite():
    """SQLite 데이터베이스를 초기화합니다. 모든 데이터가 삭제됩니다."""
    import sqlite3
    import os
    
    try:
        # SQLite 데이터베이스 파일 경로
        db_path = "./sqlite.db"
        
        # 기존 DB 파일이 있으면 삭제
        if os.path.exists(db_path):
            os.remove(db_path)
            
        # 새로운 DB 생성 및 테이블 초기화
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        
        # 메시지 테이블 생성
        cursor.execute('''
        CREATE TABLE IF NOT EXISTS chat_messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id TEXT NOT NULL,
            role TEXT NOT NULL,
            content TEXT NOT NULL,
            timestamp TEXT NOT NULL,
            created_at TEXT DEFAULT CURRENT_TIMESTAMP
        )
        ''')
        
        # 세션 테이블 생성 (선택적)
        cursor.execute('''
        CREATE TABLE IF NOT EXISTS sessions (
            id TEXT PRIMARY KEY,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        ''')
        
        # 변경사항 저장 및 연결 종료
        conn.commit()
        conn.close()
        
        return {"status": "ok", "message": "SQLite database has been reset successfully"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to reset SQLite database: {str(e)}")