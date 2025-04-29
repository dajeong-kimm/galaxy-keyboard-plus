# src/config.py
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    # ── ChromaDB ──────────────────────────────────────────────
    chroma_persist_dir: str = "./chroma_db"
    chroma_collection_name: str = "chatlogs"     # 여기를 다시 추가

    # ── 모델 & 임베딩 ─────────────────────────────────────────
    embed_model: str = "all-MiniLM-L6-v2"
    chunk_size: int = 300
    chunk_overlap: int = 50

    # ── RAG / 검색 ────────────────────────────────────────────
    rag_k: int = 3

    # ── 외부 LLM(OpenAI) ─────────────────────────────────────
    openai_api_key: str | None = None
    openai_model_name: str = "gpt-4o-mini"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",   # .env 에 남아 있는 과거 키는 무시
    )

settings = Settings()
