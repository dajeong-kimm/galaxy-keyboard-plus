from pydantic import BaseModel
import os
from dotenv import load_dotenv

# .env 파일 로드
load_dotenv()

class Settings(BaseModel):
    # ── 서버 설정 ─────────────────────────────────────────────
    host: str = os.getenv("HOST", "127.0.0.1")
    port: int = int(os.getenv("PORT", "8000"))
    debug: bool = os.getenv("DEBUG", "True").lower() in ("true", "1", "t")

    # ── ChromaDB ──────────────────────────────────────────────
    chroma_persist_dir: str = os.getenv("CHROMA_PERSIST_DIR", "./chroma_db")
    chroma_collection_name: str = os.getenv("CHROMA_COLLECTION_NAME", "chatlogs")

    # ── HuggingFace ────────────────────────────────────────────
    hf_hub_token: str | None = os.getenv("HF_HUB_TOKEN")

    # ── 모델 & 임베딩 ─────────────────────────────────────────
    embed_model: str = os.getenv("EMBED_MODEL", "all-MiniLM-L6-v2")
    chunk_size: int = int(os.getenv("CHUNK_SIZE", "300"))
    chunk_overlap: int = int(os.getenv("CHUNK_OVERLAP", "50"))

    # ── RAG / 검색 ────────────────────────────────────────────
    rag_k: int = int(os.getenv("RAG_K", "3"))

    # ── 외부 LLM(OpenAI) ─────────────────────────────────────
    openai_api_key: str | None = os.getenv("OPENAI_API_KEY")
    openai_model_name: str = os.getenv("OPENAI_MODEL_NAME", "gpt-4o-mini")
    
    # ── 임시 저장소 설정 ─────────────────────────────────────
    temp_storage_path: str = os.getenv("TEMP_STORAGE_PATH", "./chroma_db/temp_storage")

# 설정 인스턴스 생성
settings = Settings()