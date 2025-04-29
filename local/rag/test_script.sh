#!/bin/bash

# 0. 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 1. 환경 설정
BASE_URL="http://127.0.0.1:8000"

echo -e "${BLUE}====================== RAG 시스템 전체 테스트 ======================${NC}"
echo "테스트 시작 시간: $(date)"

# 2. SQLite 초기화 (API 사용)
echo -e "${BLUE}\n====================== SQLite DB 초기화 ======================${NC}"
SQLITE_RESULT=$(curl -s -X POST $BASE_URL/v1/reset_sqlite)
echo "SQLite 초기화 결과: $SQLITE_RESULT"

# 3. 서버 상태 확인
echo -e "${BLUE}\n====================== 서버 상태 확인 ======================${NC}"
HEALTH_CHECK=$(curl -s -X GET $BASE_URL/health)
echo "서버 상태: $HEALTH_CHECK"

if [[ "$HEALTH_CHECK" != *"ok"* ]]; then
  echo -e "${RED}서버가 응답하지 않습니다. 테스트를 중단합니다.${NC}"
  exit 1
fi

# 4. 세션 ID 생성
MEETING_SESSION="meeting-$(date +%Y%m%d-%H%M%S)"
DOCS_SESSION="docs-$(date +%Y%m%d-%H%M%S)"
FILES_SESSION="files-$(date +%Y%m%d-%H%M%S)"
TECH_SESSION="tech-$(date +%Y%m%d-%H%M%S)"

echo -e "${BLUE}\n====================== 테스트 세션 정보 ======================${NC}"
echo "회의 세션 ID: $MEETING_SESSION"
echo "문서 세션 ID: $DOCS_SESSION"
echo "파일 세션 ID: $FILES_SESSION"
echo "기술문서 세션 ID: $TECH_SESSION"

# 5. 회의 정보 추가
echo -e "${BLUE}\n====================== 회의 정보 테스트 ======================${NC}"
echo "5.1 회의 내용 메시지 추가"
MEETING_RESULT=$(curl -s -X POST $BASE_URL/v1/add_message \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$MEETING_SESSION'",
    "user_id": "kim.developer",
    "text": "오늘 백엔드 개발팀 회의에서는 RAG 시스템의 성능 최적화 방안에 대해 논의했습니다. 특히 임베딩 모델 선택과 청크 사이즈 조정이 중요한 이슈로 떠올랐습니다. HuggingFace 모델과 OpenAI 모델 중 한국어 처리에 더 적합한 것을 선택해야 합니다. 또한, 파일 경로 처리 시 메타데이터 활용 방안에 대해서도 이야기했습니다. 다음 회의는 5월 2일 오전 10시에 진행될 예정입니다."
  }')
echo "$MEETING_RESULT"
echo

echo "5.2 회의록 문서 추가"
DOCS_RESULT=$(curl -s -X POST $BASE_URL/v1/add_docs \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$MEETING_SESSION'",
    "docs": [
      "# 2025년 4월 25일 회의록\n\n참석자: 김개발, 이데이터, 박AI, 최머신\n\n## 논의 사항\n1. 임베딩 모델 선택\n   - HuggingFace 모델: 오픈소스, 경량화 가능\n   - OpenAI 모델: 성능 우수, 비용 고려 필요\n   - 한국어 처리를 위해 Ko-SRoBERTa 추천됨\n\n2. 청크 크기 최적화\n   - 문서: 500-800자 권장\n   - 대화: 300-500자 권장\n   - 코드: 100-300자 권장\n\n## 결정 사항\n- 한국어 처리용 HuggingFace 모델 사용 결정\n- 다음 회의에서 최종 선택 예정",
      "# 2025년 4월 27일 이메일\n\n제목: 회의 후속 조치\n보낸이: 김팀장\n받는이: 개발팀 전체\n\n안녕하세요 여러분,\n\n지난 회의에서 논의된 사항에 대한 후속 조치를 안내드립니다.\n\n1. 임베딩 모델 테스트 담당\n   - 이데이터: HuggingFace 모델 3종\n   - 박AI: OpenAI 모델 2종\n\n2. 청크 크기 실험\n   - 최머신: 다양한 문서 유형별 최적 청크 크기 테스트\n\n각자 5월 1일까지 결과를 공유해주세요.\n다음 회의는 5월 2일 오전 10시입니다.\n\n감사합니다."
    ]
  }')
echo "$DOCS_RESULT"
echo

# 6. 회의 관련 질의 응답
echo "6.1 회의 컨텍스트 조회 - 다음 회의 일정"
curl -s -X POST $BASE_URL/v1/context \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$MEETING_SESSION'",
    "question": "다음 회의 일정은 언제인가요?",
    "k": 3
  }' | jq .
echo

echo "6.2 회의 질의 응답 - 임베딩 모델"
curl -s -X POST $BASE_URL/v1/query \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$MEETING_SESSION'",
    "question": "한국어 처리에 어떤 임베딩 모델이 추천되었나요?"
  }' | jq .
echo

# 7. 문서 관련 정보 추가
echo -e "${BLUE}\n====================== 문서 관련 테스트 ======================${NC}"
echo "7.1 제품 문서 추가"
curl -s -X POST $BASE_URL/v1/add_docs \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$DOCS_SESSION'",
    "docs": [
      "# RAG 시스템 사용자 매뉴얼\n\n본 시스템은 기업 내부 문서를 효율적으로 검색하고 질의응답할 수 있는 지능형 시스템입니다. 주요 기능으로는 문서 업로드, 대화형 검색, 파일 경로 관리 등이 있습니다. 사용자는 자연어로 질문을 입력하면 관련 정보를 즉시 얻을 수 있습니다.",
      "# 시스템 요구사항\n\n- 운영체제: Windows 10/11, macOS, Linux\n- 브라우저: Chrome 90+, Firefox 88+, Safari 14+\n- 네트워크: 최소 10Mbps 인터넷 연결\n- 저장 공간: 최소 2GB 여유 공간 (문서 양에 따라 증가)\n- RAM: 최소 8GB 권장",
      "# 개발환경 설정 가이드\n\n1. Python 3.9+ 설치\n2. 가상환경 생성: `python -m venv venv`\n3. 가상환경 활성화: `source venv/bin/activate` (Windows: `venv\\Scripts\\activate`)\n4. 의존성 설치: `pip install -r requirements.txt`\n5. 환경변수 설정: `.env` 파일 생성 및 설정\n6. 서버 실행: `python main.py`"
    ]
  }'
echo

# 8. 파일 경로 관련 정보 추가
echo "8.1 파일 경로 정보 추가"
curl -s -X POST $BASE_URL/v1/add_item \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$FILES_SESSION'",
    "item_id": "embeddings-py",
    "source": "file_path",
    "uri": "/home/ssafy/project/src/core/embeddings.py",
    "text": "임베딩 모듈 코드 파일입니다. 텍스트를 벡터로 변환하는 기능을 담당하며, HuggingFace 모델과 OpenAI 모델을 지원합니다. 주요 함수로는 get_embedder()와 embed_text()가 있습니다."
  }'
echo

curl -s -X POST $BASE_URL/v1/add_item \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$FILES_SESSION'",
    "item_id": "config-yaml",
    "source": "file_path",
    "uri": "/home/ssafy/project/config/settings.yaml",
    "text": "시스템 설정 파일로, 청크 크기, 임베딩 모델, 서버 설정 등 다양한 환경 변수가 정의되어 있습니다. 개발, 테스트, 프로덕션 환경별 설정값이 포함되어 있습니다."
  }'
echo

curl -s -X POST $BASE_URL/v1/add_item \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$FILES_SESSION'",
    "item_id": "project-plan-docx",
    "source": "file_path",
    "uri": "/home/ssafy/documents/project_plan_2025.docx",
    "text": "2025년 프로젝트 계획서 문서입니다. 주요 목표, 일정, 인력 배정, 예산 등이 상세히 기술되어 있습니다. 1분기부터 4분기까지의 마일스톤과 담당자 정보가 포함되어 있습니다."
  }'
echo

# 9. 문서 및 파일 질의 응답
echo "9.1 문서 질의 응답 - 개발 환경 설정"
curl -s -X POST $BASE_URL/v1/query \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$DOCS_SESSION'",
    "question": "개발 환경을 어떻게 설정해야 하나요?"
  }' | jq .
echo

echo "9.2 파일 경로 질의 응답 - 설정 파일 위치"
curl -s -X POST $BASE_URL/v1/query \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$FILES_SESSION'",
    "question": "시스템 설정 파일의 위치를 알려주세요."
  }' | jq .
echo

# 10. 기술 문서 추가
echo -e "${BLUE}\n====================== 기술 문서 테스트 ======================${NC}"
echo "10.1 벡터 데이터베이스 가이드 추가"
curl -s -X POST $BASE_URL/v1/add_item \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$TECH_SESSION'",
    "item_id": "vector-db-guide",
    "source": "technical_document",
    "text": "# 벡터 데이터베이스 가이드\n\n벡터 데이터베이스는 임베딩된 벡터를 효율적으로 저장하고 검색하기 위한 특수 데이터베이스입니다. RAG 시스템에서는 ChromaDB를 사용합니다.\n\n## 주요 기능\n1. 벡터 저장 및 인덱싱\n2. 유사도 기반 검색\n3. 메타데이터 필터링\n4. 스케일링\n\n## 최적 설정\n- 청크 크기: 문서 500-1000자, 대화 300-500자\n- 임베딩 모델: all-MiniLM-L6-v2 (다국어 지원)\n- k값: 일반적으로 3-5\n\n## 성능 최적화\n- 메타데이터 인덱싱으로 빠른 필터링\n- 벡터 정규화로 정확도 향상\n- 주기적 데이터 정리로 중복 제거"
  }'
echo

echo "10.2 RAG 프롬프트 가이드 추가"
curl -s -X POST $BASE_URL/v1/add_item \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$TECH_SESSION'",
    "item_id": "rag-prompts",
    "source": "technical_document",
    "text": "# RAG 시스템 프롬프트 가이드\n\n효과적인 RAG 응답을 위한 프롬프트 디자인 방법입니다.\n\n## 기본 템플릿\n\n```\n당신은 사용자의 로컬 데이터를 기반으로 도움을 제공하는 비서입니다.\n\n컨텍스트:\n{context_str}\n\n질문: {question}\n\n컨텍스트에 근거하여 간결하고 정확하게 답변하세요. 관련 정보가 없으면 솔직히 모른다고 말하세요.\n```\n\n## 정제 템플릿\n\n```\n이전 답변: {existing_answer}\n\n추가 컨텍스트:\n{context_str}\n\n질문: {question}\n\n추가 컨텍스트를 활용해 답변을 보완하거나 수정하세요.\n```\n\n## 모범 사례\n- 명확한 답변 지시\n- 출처 인용 요청\n- 특정 형식 지정\n- 단계별 설명 요청\n"
  }'
echo

echo "10.3 청킹 가이드 추가"
curl -s -X POST $BASE_URL/v1/add_item \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$TECH_SESSION'",
    "item_id": "chunking-guide",
    "source": "technical_document",
    "text": "# 텍스트 청킹 가이드\n\n## 왜 청킹이 중요한가?\n텍스트 청킹은 RAG 시스템의 성능에 직접적인 영향을 미칩니다. 적절한 크기의 청크는 관련성 높은 검색 결과를 제공하고, 컨텍스트 창 내에서 최대한 많은 정보를 활용할 수 있게 합니다.\n\n## 최적의 청크 크기\n- **일반 문서**: 500-800자\n- **기술 문서**: 400-600자\n- **대화/메시지**: 300-500자\n- **코드**: 100-300자 또는 함수/클래스 단위\n\n## 청크 오버랩\n청크 간 연속성을 유지하기 위해 일정 부분 오버랩을 설정하는 것이 좋습니다. 일반적으로 청크 크기의 10-20%를 오버랩으로 설정합니다.\n\n## 분할 전략\n1. **RecursiveCharacterTextSplitter**: 문단, 문장, 단어 등 계층적으로 분할\n2. **MarkdownHeaderTextSplitter**: 마크다운 헤더를 기준으로 의미적 분할\n3. **코드 전용 분할기**: 함수나 클래스 단위로 분할\n\n## 메타데이터 활용\n각 청크에 원본 문서 정보, 청크 인덱스, 헤더 정보 등의 메타데이터를 추가하면 검색 및 필터링 성능을 높일 수 있습니다."
  }'
echo

# 11. 기술 문서 질의 응답
echo "11.1 기술 문서 질의 응답 - 청크 크기"
curl -s -X POST $BASE_URL/v1/query \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$TECH_SESSION'",
    "question": "벡터 데이터베이스에서 권장하는 청크 크기는 어떻게 되나요?"
  }' | jq .
echo

echo "11.2 기술 문서 질의 응답 - RAG 프롬프트"
curl -s -X POST $BASE_URL/v1/query \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$TECH_SESSION'",
    "question": "RAG 시스템에서 사용하는 기본 프롬프트 템플릿은 무엇인가요?"
  }' | jq .
echo

echo "11.3 기술 문서 질의 응답 - 오버랩 비율"
curl -s -X POST $BASE_URL/v1/query \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$TECH_SESSION'",
    "question": "텍스트 청킹에서 최적의 오버랩 비율은 얼마인가요?"
  }' | jq .
echo

# 12. 모든 테스트 완료
echo -e "${GREEN}\n====================== 모든 테스트 완료 ======================${NC}"
echo "테스트 완료 시간: $(date)"
echo "테스트된 세션 ID:"
echo "  회의 세션: $MEETING_SESSION"
echo "  문서 세션: $DOCS_SESSION"
echo "  파일 세션: $FILES_SESSION"
echo "  기술 세션: $TECH_SESSION"
echo
echo "모든 테스트가 완료되었습니다. 결과를 확인하세요."
