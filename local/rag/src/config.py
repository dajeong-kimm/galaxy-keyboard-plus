from pydantic import AnyHttpUrl
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    # 서버 기본 설정
    host: str = "127.0.0.1"
    port: int = 8000
    debug: bool = True

    # Qdrant 설정
    qdrant_url: AnyHttpUrl
    qdrant_port: int = 6333
    qdrant_prefer_grpc: bool = False
    qdrant_collection: str = "chatlogs"

    # 임베딩 모델 이름
    embed_model: str = "all-MiniLM-L6-v2"

    # 로컬 LLM 모델 경로
    llm_model_path: str  # ex) ./models/llama/gpt4all-lora.bin

    # 외부 LLM (OpenAI) 호출용 설정
    openai_api_key: str            # env: OPENAI_API_KEY
    openai_model_name: str = "gpt-3.5-turbo"  # env: OPENAI_MODEL_NAME

    # HuggingFace Hub 인증 토큰
    # (자동 다운로드 필요 시 사용)
    hf_hub_token: str

    # LLM 동작 설정
    llm_n_ctx: int = 2048
    llm_threads: int = 4

    # RAG 검색 개수
    rag_k: int = 3

    # .env 로딩 설정
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8"
    )

# 전역 인스턴스
settings = Settings()