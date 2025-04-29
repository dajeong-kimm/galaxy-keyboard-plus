#!/usr/bin/env bash
# test_api.sh — 전체 RAG 서비스 통합 + 요청/응답 페이로드 출력
# Requires: bash, curl, jq, python3

set -euo pipefail

BASE_URL="http://127.0.0.1:8000"
SESSION="session-$(date +%s)"
USER="user-42"

echo "🔷 1) Health check"
echo "→ GET ${BASE_URL}/health"
curl -s "${BASE_URL}/health" | jq .
echo

echo "🔷 2) Bulk add user messages (20개)"
for i in {1..20}; do
  hour=$((7 + i % 10))
  minute=$(( (i*3) % 60 ))
  ts=$(printf "2025-04-29T%02d:%02d:00Z" "$hour" "$minute")
  payload=$(cat <<EOF
{
  "session_id": "${SESSION}",
  "user_id": "${USER}",
  "text": "메시지 #${i}: 이건 테스트용 채팅 메시지입니다.",
  "timestamp": "${ts}"
}
EOF
)
  echo "→ POST /v1/add_message (message #${i})"
  echo "$payload" | jq .
  response=$(curl -s -X POST "${BASE_URL}/v1/add_message" \
    -H "Content-Type: application/json" \
    -d "$payload")
  echo "$response" | jq .
  echo
done

echo "🔷 3) Bulk add docs (3개)"
payload=$(cat <<EOF
{
  "session_id": "${SESSION}",
  "docs": [
    "# Doc_A_2025-04-28.md\n\n오늘 데이터 파이프라인 다시 설계했습니다. 주요 변경사항:\n- Kafka → Pub/Sub 전환\n- ETL 스케줄링: Airflow 사용\n- 변환 로직: Spark Batch 처리\n- 저장소: BigQuery 로 이동\n\n기타: 모니터링 Grafana, 알람 Slack 연동",
    "# Doc_B_2025-04-29.md\n\n인증 모듈 개발 가이드:\n- OAuth2 Authorization Code Grant\n- 세션 유지: Redis 세션 스토어\n- 토큰 갱신: Refresh Token 사용\n- 보안: HTTPS 강제, CORS 설정",
    "# Doc_C_2025-04-29.md\n\n프런트엔드 배포 절차:\n1. 빌드: npm run build\n2. 아티팩트 업로드: S3 버킷\n3. CloudFront 무효화\n4. Canary 배포: 10% → 50% → 100%"
  ]
}
EOF
)
echo "→ POST /v1/add_docs"
echo "$payload" | jq .
curl -s -X POST "${BASE_URL}/v1/add_docs" \
  -H "Content-Type: application/json" \
  -d "$payload" | jq .
echo

echo "🔷 4) Context 검색 (벡터 저장 확인)"
payload=$(cat <<EOF
{
  "session_id": "${SESSION}",
  "question": "데이터 파이프라인 변경사항이 무엇인가요?",
  "k": 5
}
EOF
)
echo "→ POST /v1/context"
echo "$payload" | jq .
curl -s -X POST "${BASE_URL}/v1/context" \
  -H "Content-Type: application/json" \
  -d "$payload" | jq .
echo

echo "🔷 5) /v1/query (간단 질의)"
payload=$(cat <<EOF
{
  "session_id": "${SESSION}",
  "question": "Kafka 대신 무엇을 사용하는가요?",
  "topic_id": null
}
EOF
)
echo "→ POST /v1/query"
echo "$payload" | jq .
curl -s -X POST "${BASE_URL}/v1/query" \
  -H "Content-Type: application/json" \
  -d "$payload" | jq .
echo

echo "🔷 6) /v1/universal_query (auto + 오늘)"
payload=$(cat <<EOF
{
  "question": "OAuth2 인증 방식은 무엇인가요?",
  "current_session_id": "${SESSION}",
  "search_mode": "auto",
  "time_info": "오늘",
  "k": 3
}
EOF
)
echo "→ POST /v1/universal_query"
echo "$payload" | jq .
curl -s -X POST "${BASE_URL}/v1/universal_query" \
  -H "Content-Type: application/json" \
  -d "$payload" | jq .
echo

echo "🔷 7) Chat history 확인"
echo "→ GET /v1/chat_history/${SESSION}?limit=10&offset=0"
curl -s "${BASE_URL}/v1/chat_history/${SESSION}?limit=10&offset=0" | jq .
echo

echo "🔷 8) SQLite row count 확인 (python3 사용)"
echo "→ SELECT COUNT(*) FROM chat_messages WHERE session_id='${SESSION}'"
count=$(python3 - <<PYCODE
import sqlite3
conn = sqlite3.connect("sqlite.db")
cur = conn.execute(
    "SELECT COUNT(*) FROM chat_messages WHERE session_id=?", 
    ("${SESSION}",)
)
print(cur.fetchone()[0])
conn.close()
PYCODE
)
jq -n --arg session "$SESSION" --argjson cnt "$count" \
   '{ session_id: $session, chat_messages: $cnt }'
echo

echo "🔷 9) Vector DB 저장 건수 확인 (context length)"
payload=$(cat <<EOF
{
  "session_id": "${SESSION}",
  "question": "배포 절차 단계가 어떻게 되나요?",
  "k": 5
}
EOF
)
echo "→ POST /v1/context"
echo "$payload" | jq .
curl -s -X POST "${BASE_URL}/v1/context" \
  -H "Content-Type: application/json" \
  -d "$payload" | jq .
echo

echo "🔷 10) Cleanup inactive chats older than 1 day"
echo "→ POST /v1/cleanup_chat_history?days=1&inactive_only=true"
curl -s -X POST "${BASE_URL}/v1/cleanup_chat_history?days=1&inactive_only=true" | jq .
echo

echo "🔷 11) Delete this session entirely"
echo "→ DELETE /v1/delete_chat/${SESSION}"
curl -s -X DELETE "${BASE_URL}/v1/delete_chat/${SESSION}" | jq .
echo

echo "✅ All tests completed."

