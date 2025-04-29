#!/bin/bash
# RAG 시스템 전체 테스트 스크립트

# 서버가 실행 중인지 확인
if ! curl -s http://localhost:8000/health > /dev/null; then
  echo "서버가 실행되고 있지 않습니다. 먼저 './run.sh'로 서버를 실행해주세요."
  exit 1
fi

# 색상 설정
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
GRAY='\033[0;37m'
NC='\033[0m' # No Color

# 세션 ID 생성
SESSION_ID=$(uuidgen)
echo -e "${CYAN}테스트 세션 ID: $SESSION_ID${NC}"

# 1. 메시지 추가 테스트
echo -e "\n${YELLOW}1. 메시지 추가 테스트...${NC}"
MESSAGE_BODY='{"session_id":"'$SESSION_ID'","text":"안녕하세요, RAG 시스템 테스트 중입니다."}'

RESULT=$(curl -s -X POST -H "Content-Type: application/json" -d "$MESSAGE_BODY" http://localhost:8000/v1/add_message)
CHUNKS_ADDED=$(echo $RESULT | grep -o '"chunks_added":[0-9]*' | cut -d':' -f2)

if [ ! -z "$CHUNKS_ADDED" ]; then
  echo -e "${GREEN}사용자 메시지 추가 성공: $CHUNKS_ADDED 청크 추가됨${NC}"
else
  echo -e "${RED}메시지 추가 실패: $RESULT${NC}"
fi

# 2. 어시스턴트 메시지 추가
echo -e "\n${YELLOW}2. 어시스턴트 메시지 추가 테스트...${NC}"
ASSISTANT_BODY='{"session_id":"'$SESSION_ID'","content":"안녕하세요! RAG 시스템 테스트를 진행 중이시군요. 어떤 도움이 필요하신가요?"}'

RESULT=$(curl -s -X POST -H "Content-Type: application/json" -d "$ASSISTANT_BODY" http://localhost:8000/v1/save_assistant_message)
CHUNKS_ADDED=$(echo $RESULT | grep -o '"chunks_added":[0-9]*' | cut -d':' -f2)

if [ ! -z "$CHUNKS_ADDED" ]; then
  echo -e "${GREEN}어시스턴트 메시지 추가 성공: $CHUNKS_ADDED 청크 추가됨${NC}"
else
  echo -e "${RED}어시스턴트 메시지 추가 실패: $RESULT${NC}"
fi

# 3. 문서 추가 테스트
echo -e "\n${YELLOW}3. 문서 추가 테스트...${NC}"
DOCS_BODY='{
  "session_id":"'$SESSION_ID'",
  "docs":[
    "RAG(Retrieval-Augmented Generation)는 대규모 언어 모델(LLM)의 성능을 향상시키는 방법으로, 외부 데이터 소스에서 관련 정보를 검색하여 LLM의 응답 생성에 활용합니다.",
    "벡터 데이터베이스는 임베딩된 텍스트를 저장하고 효율적으로 유사도 검색을 수행하기 위한 특수 데이터베이스입니다. ChromaDB, Pinecone, Qdrant 등이 있습니다."
  ]
}'

RESULT=$(curl -s -X POST -H "Content-Type: application/json" -d "$DOCS_BODY" http://localhost:8000/v1/add_docs)
CHUNKS_COUNT=$(echo $RESULT | grep -o '"chunks_count":[0-9]*' | cut -d':' -f2)

if [ ! -z "$CHUNKS_COUNT" ]; then
  echo -e "${GREEN}문서 추가 성공: $CHUNKS_COUNT 청크 추가됨${NC}"
else
  echo -e "${RED}문서 추가 실패: $RESULT${NC}"
fi

# 4. 아이템 추가 테스트
echo -e "\n${YELLOW}4. 아이템 추가 테스트...${NC}"
ITEM_BODY='{
  "session_id":"'$SESSION_ID'",
  "item_id":"test_item_001",
  "source":"doc",
  "text":"랭체인(LangChain)은 LLM 애플리케이션 개발을 위한 프레임워크로, RAG 파이프라인 구축에 유용한 다양한 컴포넌트를 제공합니다."
}'

RESULT=$(curl -s -X POST -H "Content-Type: application/json" -d "$ITEM_BODY" http://localhost:8000/v1/add_item)
CHUNKS_ADDED=$(echo $RESULT | grep -o '"chunks_added":[0-9]*' | cut -d':' -f2)

if [ ! -z "$CHUNKS_ADDED" ]; then
  echo -e "${GREEN}아이템 추가 성공: $CHUNKS_ADDED 청크 추가됨${NC}"
else
  echo -e "${RED}아이템 추가 실패: $RESULT${NC}"
fi

# 5. 컨텍스트 검색 테스트
echo -e "\n${YELLOW}5. 컨텍스트 검색 테스트...${NC}"
CONTEXT_BODY='{
  "session_id":"'$SESSION_ID'",
  "question":"RAG란 무엇인가요?",
  "k":2
}'

RESULT=$(curl -s -X POST -H "Content-Type: application/json" -d "$CONTEXT_BODY" http://localhost:8000/v1/context)
CONTEXTS_COUNT=$(echo $RESULT | grep -o '"contexts":\[' | wc -l)

if [ "$CONTEXTS_COUNT" -gt 0 ]; then
  echo -e "${GREEN}컨텍스트 검색 성공${NC}"
  echo -e "${GRAY}첫 번째 컨텍스트 미리보기:${NC}"
  echo $RESULT | grep -o '"contexts":\[\([^]]*\)' | head -c 150
  echo "..."
else
  echo -e "${RED}컨텍스트 검색 실패: $RESULT${NC}"
fi

# 6. 기본 쿼리 테스트
echo -e "\n${YELLOW}6. 기본 쿼리 테스트...${NC}"
QUERY_BODY='{
  "session_id":"'$SESSION_ID'",
  "question":"랭체인에 대해 알려주세요."
}'

RESULT=$(curl -s -X POST -H "Content-Type: application/json" -d "$QUERY_BODY" http://localhost:8000/v1/query)

if echo $RESULT | grep -q '"answer"'; then
  echo -e "${GREEN}쿼리 성공!${NC}"
  echo -e "${GRAY}응답:${NC}" 
  echo $RESULT | grep -o '"answer":"[^"]*"' | sed 's/"answer":"//g' | sed 's/"$//g'
  echo -e "${GRAY}처리 시간:${NC}" 
  echo $RESULT | grep -o '"total_time":[0-9.]*' | cut -d':' -f2
else
  echo -e "${RED}쿼리 실패: $RESULT${NC}"
fi

# 7. Universal 쿼리 테스트
echo -e "\n${YELLOW}7. Universal 쿼리 테스트...${NC}"
UNIVERSAL_BODY='{
  "current_session_id":"'$SESSION_ID'",
  "question":"벡터 데이터베이스란 무엇인가요?",
  "search_mode":"auto"
}'

RESULT=$(curl -s -X POST -H "Content-Type: application/json" -d "$UNIVERSAL_BODY" http://localhost:8000/v1/universal_query)

if echo $RESULT | grep -q '"answer"'; then
  echo -e "${GREEN}Universal 쿼리 성공!${NC}"
  echo -e "${GRAY}응답:${NC}" 
  echo $RESULT | grep -o '"answer":"[^"]*"' | sed 's/"answer":"//g' | sed 's/"$//g'
  echo -e "${GRAY}검색 모드:${NC}" 
  echo $RESULT | grep -o '"search_mode":"[^"]*"' | cut -d':' -f2
  echo -e "${GRAY}처리 시간:${NC}" 
  echo $RESULT | grep -o '"processing_time":[0-9.]*' | cut -d':' -f2
else
  echo -e "${RED}Universal 쿼리 실패: $RESULT${NC}"
fi

# 8. 채팅 내역 조회 테스트
echo -e "\n${YELLOW}8. 채팅 내역 조회 테스트...${NC}"
RESULT=$(curl -s -X GET "http://localhost:8000/v1/chat_history/$SESSION_ID")
MESSAGES_COUNT=$(echo $RESULT | grep -o '"count":[0-9]*' | cut -d':' -f2)

if [ ! -z "$MESSAGES_COUNT" ]; then
  echo -e "${GREEN}채팅 내역 조회 성공: $MESSAGES_COUNT 메시지 찾음${NC}"
  echo -e "${GRAY}메시지 미리보기:${NC}"
  echo $RESULT | grep -o '"content":"[^"]*"' | head -3 | sed 's/"content":"//g' | sed 's/"$//g' | cut -c 1-50
else
  echo -e "${RED}채팅 내역 조회 실패: $RESULT${NC}"
fi

# 테스트 정리
echo -e "\n${YELLOW}테스트 세션 삭제 중...${NC}"
RESULT=$(curl -s -X DELETE "http://localhost:8000/v1/delete_chat/$SESSION_ID")
DELETED_MESSAGES=$(echo $RESULT | grep -o '"deleted_messages":[0-9]*' | cut -d':' -f2)

if [ ! -z "$DELETED_MESSAGES" ]; then
  echo -e "${GREEN}테스트 세션 삭제 성공: $DELETED_MESSAGES 메시지 삭제됨${NC}"
else
  echo -e "${RED}테스트 세션 삭제 실패: $RESULT${NC}"
fi

echo -e "\n${CYAN}모든 테스트가 완료되었습니다!${NC}"
