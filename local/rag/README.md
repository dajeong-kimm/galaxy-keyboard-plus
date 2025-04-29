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


# 추가된 코드 내용 요약

벡터 DB 핸들러 (VectorDBHandler)

목적: LLM 대화 및 MCP 작업 내용을 효율적으로 저장하고 검색하는 통합 시스템
주요 기능:

- 다양한 데이터 타입(대화, 문서, 워크플로우, 요약) 관리
- 텍스트 청크화 및 메타데이터 관리
- GPT-4o-mini를 활용한 자동 요약 생성
- 전체 내용 백업 및 복원
- 시맨틱 검색 기능




# FastAPI 엔드포인트 (new_app.py)

- 목적: RAG 시스템에 REST API 인터페이스 제공
## 주요 엔드포인트:

- /conversations/: 대화 내역 저장 및 조회
- /documents/: 문서 내용 저장 및 조회
- /workflows/: MCP 작업 방법/순서 저장 및 조회
- /summaries/: 작업 요약 저장 및 조회
- /search/: 통합 검색 기능
- /generate-summary/: 텍스트 내용 요약 생성




# 설정 관리

목적: 환경 변수를 통한 시스템 설정 관리
주요 설정:

- 서버 설정 (호스트, 포트)
- 벡터 DB 설정 (ChromaDB, 컬렉션)
- 임베딩 모델 설정
- OpenAI API 설정 (GPT-4o-mini)

---

데이터 처리 전략

- 청크화: 긴 텍스트를 의미 단위로 분할하여 저장
- 메타데이터: 각 청크에 풍부한 메타데이터 부여
- 요약: 각 컨텐츠의 자동 요약 생성 및 첫 청크에 포함
- 재구성: 검색 시 청크 인덱스 기반 원본 내용 재구성
- 중복 제거: 검색 결과에서 동일 소스의 중복 제거

---

효율성 최적화

- 메모리 관리: 청크와 메타데이터는 벡터 DB에, 원본은 별도 저장
- 검색 최적화: 관련성 점수 기반 결과 정렬
- 오류 처리: 요약 생성 실패 시 대체 방법 제공


## 라이선스
MIT | © 2025 