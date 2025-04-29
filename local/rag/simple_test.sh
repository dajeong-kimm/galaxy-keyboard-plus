#!/bin/bash

# 기본 설정
BASE_URL="http://127.0.0.1:8000"
TEST_SESSION="test-$(date +%s)"

echo "===== 간단 테스트 시작 ====="
echo "테스트 세션: $TEST_SESSION"

# 서버 상태 확인
echo -e "\n1. 서버 상태 확인"
curl -s -X GET $BASE_URL/health

# 간단한 메시지 추가
echo -e "\n\n2. 간단한 메시지 추가"
curl -s -X POST $BASE_URL/v1/add_message \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$TEST_SESSION'",
    "text": "테스트 메시지입니다."
  }'

# 간단한 문서 추가
echo -e "\n\n3. 간단한 문서 추가" 
curl -s -X POST $BASE_URL/v1/add_docs \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$TEST_SESSION'",
    "docs": [
      "이것은 테스트 문서입니다."
    ]
  }'

# 컨텍스트 조회
echo -e "\n\n4. 컨텍스트 조회"
curl -s -X POST $BASE_URL/v1/context \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "'$TEST_SESSION'",
    "question": "테스트",
    "k": 1
  }' | jq .

echo -e "\n\n===== 간단 테스트 완료 ====="
