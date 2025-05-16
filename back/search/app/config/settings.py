import os
from dotenv import load_dotenv

load_dotenv()

# DB 연결 설정
DB_PARAMS = {
    "host": "3.38.95.110",
    "port": "5434",
    "database": os.getenv("POSTGRES_RAG_DB_NAME"),
    "user": os.getenv("POSTGRES_RAG_USER"),
    "password": os.getenv("POSTGRES_RAG_PASSWORD"),
}

# OpenAI API 설정
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY_2")

# 캐시 설정
CACHE_TTL_SECONDS = 3600
MAX_CACHE_SIZE = 500

# 스레드풀 설정
MAX_WORKERS = 10

# AI 모델 설정
INTENT_MODEL = "gpt-4o-mini"
KEYWORD_EXTRACTION_MODEL = "gpt-4o-mini"
QUERY_EXPANSION_MODEL = "gpt-4o-mini"
ANSWER_GENERATION_MODEL = "gpt-4o-mini"

# 검색 설정
MAX_SEARCH_RESULTS = 30
MAX_DISPLAY_RESULTS = 10
MAX_CONTEXT_ITEMS = 5
