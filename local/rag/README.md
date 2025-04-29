# Local RAG Service 🚀  
FastAPI + **ChromaDB** + **OpenAI Chat Models**로 동작하는 _가벼운_ 로컬 RAG(재검색-생성) 시스템입니다.  
세션(UUID)별로 메시지·문서를 벡터화-저장하고, **유사도 Top-K 검색** 후 OpenAI LLM으로 답변을 생성합니다.  
(※ 토픽 클러스터링은 _선택 기능_ 으로, 필요 시 HDBSCAN 코드만 추가하면 됩니다.)

---

## 주요 기능

| # | 기능              | 설명                                                                     |
|---|-------------------|--------------------------------------------------------------------------|
| 1 | **임베딩 저장**   | `/v1/add_message`, `/v1/add_docs` → HuggingFace 임베딩 → ChromaDB 저장    |
| 2 | **RAG 질의**      | `/v1/query` → 세션(`chat_id`) 범위 Top-K 검색 → OpenAI ChatCompletion 응답 |
| 3 | **헬스 체크**     | `/health` 엔드포인트로 서비스 상태 확인                                   |
| 4 | _(옵션)_ 토픽 클러스터링 | HDBSCAN + `topic_id` 메타 업데이트(필요 시)                                 |

---

## 폴더 구조

```
local/rag
├── .env                # 환경 변수
├── README.md           # (이 파일)
├── requirements.txt    # Python 의존성
├── main.py             # uvicorn 실행용 래퍼
├── app.py              # FastAPI 진입점
└── src
    ├── config.py       # Pydantic Settings (.env 로드)
    └── core
        ├── embeddings.py    # HuggingFaceEmbeddings
        ├── chroma_store.py  # Chroma 래퍼
        ├── llm.py           # ChatOpenAI 래퍼
        ├── rag.py           # RetrievalQA 체인
        └── clustering.py    # (선택) HDBSCAN
```

---

## `.env` 예시

```
# 서버 설정
HOST=127.0.0.1
PORT=8000
DEBUG=true

# ChromaDB 설정
CHROMA_PERSIST_DIR=./chroma_db
CHROMA_COLLECTION_NAME=chatlogs

# 임베딩·RAG
EMBED_MODEL=all-MiniLM-L6-v2
RAG_K=3

# OpenAI ChatCompletion
OPENAI_API_KEY=sk-...
OPENAI_MODEL_NAME=gpt-4o-mini
```

## 빠른 시작

```bash
# 1) 가상환경 & 의존성 설치
python -m venv .venv
source .venv/bin/activate          # Windows: .\.venv\Scripts\activate
pip install -r requirements.txt

# 2) 서버 실행
uvicorn main:app --reload --host 127.0.0.1 --port 8000

# 3) 헬스 체크
curl http://127.0.0.1:8000/health
```

## 주요 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET    | `/health` | 서버 상태 확인 |
| POST   | `/v1/add_message` | 세션 메시지 저장 |
| POST   | `/v1/add_docs` | 세션 문서 저장 |
| POST   | `/v1/query` | 세션 RAG 질의 |

## 예시 (문서 추가 → 질의)

```bash
SESSION=$(uuidgen)             # Windows PowerShell: $SESSION=[guid]::NewGuid()

# 문서 추가
curl -X POST http://127.0.0.1:8000/v1/add_docs \
     -H "Content-Type: application/json" \
     -d "{\"session_id\":\"$SESSION\",\"docs\":[\"Chroma-OpenAI RAG 테스트 문서입니다.\"]}"

# 질의
curl -X POST http://127.0.0.1:8000/v1/query \
     -H "Content-Type: application/json" \
     -d "{\"session_id\":\"$SESSION\",\"question\":\"문서를 한 문장으로 요약해 줘\"}"
```

## 토픽 클러스터링 (선택)
대화·문서가 매우 많아지면 `src/core/clustering.py`의 `cluster_session_topics_chroma()` 함수를 호출해 `topic_id` 메타를 자동 추가해 검색 범위를 좁힐 수 있습니다. 기본 동작에는 필요하지 않습니다.

## 의존성 버전 요약
- FastAPI ≥ 0.95
- ChromaDB ≥ 0.4.2
- langchain-community / langchain-huggingface ≥ 0.0.6
- OpenAI Python ≥ 0.27

자세한 버전은 requirements.txt를 참고하세요.

## 라이선스
MIT | © 2025 