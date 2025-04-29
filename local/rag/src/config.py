# src/config.py
from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import AnyHttpUrl


class Settings(BaseSettings):
    # ── FastAPI ───────────────────────────────────────────────
    host: str = "127.0.0.1"
    port: int = 8000
    debug: bool = True

    # ── ChromaDB ──────────────────────────────────────────────
    chroma_persist_dir: str = "./chroma_db"
    chroma_collection_name: str = "chatlogs"

    # ── 모델 & 임베딩 ─────────────────────────────────────────
    embed_model: str = "all-MiniLM-L6-v2"
    llm_n_ctx: int = 2048
    llm_threads: int = 4
    chunk_size: int = 1000
    chunk_overlap: int = 100


    # ── RAG / 검색 ────────────────────────────────────────────
    rag_k: int = 3

    # ── HDBSCAN 클러스터링 ───────────────────────────────────
    # clustering_min_size: int = 5

    # ── 외부 LLM(OpenAI) ─────────────────────────────────────
    openai_api_key: str | None = None
    openai_model_name: str = "gpt-4o-mini"

    # .env 로딩
    model_config = SettingsConfigDict(
        env_file=".env", 
        env_file_encoding="utf-8"
        )


# 전역 싱글톤
settings = Settings()
