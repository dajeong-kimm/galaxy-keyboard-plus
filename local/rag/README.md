# Local RAG Service

FastAPI 기반의 로컬 RAG 시스템입니다. Qdrant에 임베딩 벡터를 저장하고, OpenAI ChatCompletion API를 통해 RAG(재검색) 방식으로 대화형 응답을 제공합니다. 클러스터링 기능을 통해 세션과 토픽 단위로 컨텍스트를 분리·관리할 수 있습니다.

---

## 주요 기능

1. **임베딩 저장**: 사용자 메시지(`add_message`)와 문서(`add_docs`)를 Qdrant에 벡터화하여 저장
2. **토픽 클러스터링**: HDBSCAN을 이용해 세션별 대화, 문서 토픽을 자동으로 군집화
3. **RAG 질의**: 세션 전체 혹은 선택 토픽 범위에서 벡터 검색 후, OpenAI 모델로 답변 생성
4. **헬스체크**: `/health` 엔드포인트로 서비스 상태 확인

---

## 폴더 구조

```
local/rag
├── .env                   # 환경 변수 (Qdrant URL, OpenAI 키 등)
├── README.md              # 프로젝트 설명 (이 파일)
├── requirements.txt       # Python 의존성 목록
├── app.py                 # FastAPI 애플리케이션 진입점
├── main.py                # uvicorn 실행용 래퍼 (선택)
├── src
│   ├── config.py          # Pydantic 기반 설정 로드
│   ├── core
│   │   ├── embeddings.py  # HuggingFace 임베딩 생성
│   │   ├── llm.py         # OpenAI ChatCompletion 초기화
│   │   ├── qdrant_store.py# QdrantClient 및 래퍼 초기화
│   │   ├── rag.py         # RetrievalQA 체인 정의 (refine 모드)
│   │   └── clustering.py  # HDBSCAN 기반 토픽 클러스터링
│   ├── tasks
│   │   └── cluster_topics.py  # 클러스터링 배치 스크립트
│   └── utils
│       └── logger.py      # 로깅 유틸리티
└── models/                # (Optional) 로컬 모델 디렉터리
```

---

## 환경 설정

루트에 `.env` 파일을 생성하여 아래 항목을 설정하세요:

```dotenv
# Qdrant
QDRANT_URL=http://localhost:6333
QDRANT_COLLECTION=chatlogs

# OpenAI
OPENAI_API_KEY=your_openai_api_key
OPENAI_MODEL_NAME=gpt-4o-mini

# 임베딩 모델
EMBED_MODEL=all-MiniLM-L6-v2

# 클러스터링
CLUSTERING_MIN_SIZE=5
```

---

## 시작 가이드

1. **Qdrant 실행**
   ```bash
docker run -d --name qdrant -p 6333:6333 qdrant/qdrant:latest
   ```

2. **파이썬 가상환경 & 의존성 설치**
   ```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
   ```

3. **컬렉션 초기화**
   ```bash
curl -X DELETE http://localhost:6333/collections/chatlogs
curl -X PUT http://localhost:6333/collections/chatlogs \
  -H "Content-Type: application/json" \
  -d '{"vectors":{"size":384,"distance":"Cosine"}}'
   ```

4. **서버 실행**
   ```bash
uvicorn app:app --reload --host 127.0.0.1 --port 8000
   ```

---

## 주요 엔드포인트

### Health Check
```
GET /health -> {"status": "ok"}
```

### 메시지 저장
```
POST /v1/add_message
Content-Type: application/json
{
  "session_id": "<세션 UUID>",
  "user_id": "<사용자 ID>",
  "text": "<메시지 내용>",
  "timestamp": "<ISO8601 타임스탬프>"
}
```

### 문서 저장
```
POST /v1/add_docs
Content-Type: application/json
{
  "session_id": "<세션 UUID>",
  "docs": ["문서1", "문서2", ...]
}
```

### RAG 질의
```
POST /v1/query
Content-Type: application/json
{
  "session_id": "<세션 UUID>",
  "topic_id": "<선택 토픽 ID>",   # Optional
  "question": "<질문 내용>"
}
```

---

## 토픽 클러스터링

배치로 토픽을 클러스터링하려면:
```bash
python -m src.tasks.cluster_topics <session_id>
```

성공 시 `[clustered] session <session_id>` 로그를 확인할 수 있습니다.

---

## 테스트 예시

```bash
# 메시지 저장
curl -X POST http://127.0.0.1:8000/v1/add_message \
  -H "Content-Type: application/json" \
  -d '{"session_id":"test-session","user_id":"u1","text":"테스트 메시지","timestamp":"2025-04-28T12:00:00Z"}'

# 문서 저장
curl -X POST http://127.0.0.1:8000/v1/add_docs \
  -H "Content-Type: application/json" \
  -d '{"session_id":"test-session","docs":["문서 A","문서 B"]}'

# 클러스터링
python -m src.tasks.cluster_topics test-session

# RAG 질의
curl -X POST http://127.0.0.1:8000/v1/query \
  -H "Content-Type: application/json" \
  -d '{"session_id":"test-session","question":"요약해줘"}'
```

---