#!/bin/bash

# 0. 기존 설치된 모든 의존성 삭제 (가상 환경 사용 중일 때만 안전합니다)
pip freeze | grep -v "^-e" | xargs pip uninstall -y

# 1. pydantic 1.x 버전 먼저 설치 (FastAPI 0.95.1과 호환)
pip install pydantic==1.10.8

# 2. FastAPI 설치 (pydantic-settings 없이)
pip install fastapi==0.95.1 python-dotenv==1.0.0

# 3. pydantic-settings의 호환 버전 설치
pip install pydantic-settings==1.2.0

# 4. UVICORN 설치
pip install "uvicorn[standard]==0.22.0"

# 5. 기본 유틸리티 설치
pip install python-multipart==0.0.6 requests==2.28.0

# 6. OpenAI API 클라이언트 설치
pip install openai==1.0.0

# 7. 파서 설치
pip install lark-parser==0.12.0

# 8. LangChain 핵심 설치
pip install langchain==0.0.337 langchain-community==0.0.6 langchain-huggingface==0.1.2

# 9. ChromaDB 의존성 개별 설치
pip install hnswlib==0.7.0 sqlite-vss==0.1.0 duckdb==0.8.1 numpy==1.24.3
pip install typing-extensions==4.5.0 overrides==7.3.1 mmh3==4.0.1

# 10. ChromaDB 자체 설치 (의존성 없이)
pip install chromadb==0.4.2 --no-deps

# 11. PyTorch CPU 버전 설치
pip install torch==2.0.1 --index-url https://download.pytorch.org/whl/cpu

# 12. Sentence Transformers 설치 (의존성 없이)
pip install sentence-transformers==2.2.2 --no-deps

# 13. 필요한 추가 의존성 설치
pip install transformers==4.30.0 tokenizers==0.13.3 safetensors==0.3.1