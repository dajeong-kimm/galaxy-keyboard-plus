# 1. 기본 의존성 설치
pip install -r requirements.txt

# 2. 특별 관리가 필요한 패키지 개별 설치
pip install chromadb==0.4.2 --no-deps
pip install sentence-transformers>=2.6.0
pip install langchain-core==0.1.0 --no-deps
pip install langchain==0.0.337 --no-deps
pip install langchain-community==0.0.6 --no-deps
pip install langchain-huggingface==0.1.2 --no-deps
pip install openai==1.0.0 --no-deps