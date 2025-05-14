import os
from typing import Dict, Any
import logging
from pydantic import BaseSettings

# 로깅 설정
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)

class Settings(BaseSettings):
    # 기본 설정
    PROJECT_NAME: str = "MCP Integration Service"
    DEBUG: bool = os.getenv("DEBUG", "False").lower() == "true"
    
    # MCP 서버 기본 포트
    WEB_SEARCH_PORT: int = int(os.getenv("WEB_SEARCH_PORT", "8100"))


    logger.info("Settings servers..." + DEBUG)
    logger.info("Settings servers..." + WEB_SEARCH_PORT)

    # MCP 서버 설정
    MCP_SERVERS: Dict[str, Dict[str, Any]] = {
        "web_search": {
            "port": WEB_SEARCH_PORT,
            "description": "Web search MCP server",
            "enabled": True
        },
    }
    
    class Config:
        env_file = ".env"

settings = Settings()