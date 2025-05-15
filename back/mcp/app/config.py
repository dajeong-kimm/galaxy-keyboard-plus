import os
from pydantic import BaseSettings

class Settings(BaseSettings):
    """애플리케이션 설정"""
    # WEB_SEARCH_URL: str = os.getenv("WEB_SEARCH_URL", "http://web-search:8100")
    BRAVE_SEARCH_URL: str = os.environ.get("BRAVE_SEARCH_URL", "http://brave-search-mcp:8100")

    class Config:
        env_file = ".env"

settings = Settings()