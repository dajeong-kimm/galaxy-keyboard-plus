import os
from pydantic import BaseSettings

class Settings(BaseSettings):
    """애플리케이션 설정"""
    OPENAI_API_KEY: str
    WEB_SEARCH_URL: str

    class Config:
        env_file = ".env"

settings = Settings()