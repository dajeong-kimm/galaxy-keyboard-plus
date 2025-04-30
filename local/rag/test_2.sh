#!/usr/bin/env bash
# 테스트 스크립트: test_fixed.sh

# 색상 정의
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 실행 모드 선택
MODE=${1:-"partial"}

# 세션 ID와 기타 식별자 생성
SESSION_ID=$(uuidgen)
DOCUMENT_ID="doc_$(uuidgen | cut -c1-8)"
WORKFLOW_ID="wf_$(uuidgen | cut -c1-8)"
TASK_NAME="일일보고서_자동화"
DATE_TODAY=$(date +"%Y-%m-%d")

echo -e "${GREEN}===== RAG 시스템 엔드포인트 테스트 (모드: $MODE) =====${NC}"
echo "SESSION_ID: $SESSION_ID"
echo "DOCUMENT_ID: $DOCUMENT_ID"
echo "WORKFLOW_ID: $WORKFLOW_ID"
echo ""

# 1. 헬스 체크 엔드포인트
echo -e "${BLUE}1. 헬스 체크 테스트...${NC}"
curl -s http://127.0.0.1:8000/health | jq .
echo ""

# 2. 컬렉션 목록 조회
echo -e "${BLUE}2. 컬렉션 목록 조회...${NC}"
curl -s http://127.0.0.1:8000/collections/ | jq .
echo ""

# full 모드에서만 실행 
if [ "$MODE" = "full" ]; then
    # 3. 대화 저장 - 불리언을 문자열로 변경
    echo -e "${BLUE}3. 대화 저장 테스트...${NC}"
    curl -s -X POST http://127.0.0.1:8000/conversations/ \
      -H "Content-Type: application/json" \
      -d "{
        \"content\": \"사용자: 안녕하세요, 오늘 일정 알려줘\\n시스템: 오늘 일정은 오전 10시 팀 미팅, 오후 2시 고객 미팅, 오후 4시 주간 리포트 작성입니다.\\n사용자: 고객 미팅 자료는 어디 있어?\\n시스템: 고객 미팅 자료는 공유 드라이브의 '4월 미팅 자료' 폴더에 있습니다. 가장 최신 버전은 '클라이언트A_제안서_v3.pptx'입니다.\",
        \"chat_id\": \"$SESSION_ID\",
        \"metadata\": {
          \"user\": \"jjin4363\",
          \"chat_title\": \"일정 문의\",
          \"application\": \"자동비서\",
          \"is_important\": \"true\",
          \"is_archived\": \"false\"
        }
      }" | jq .
    echo ""

    # 4. 문서 저장 - 불리언을 문자열로 변경
    echo -e "${BLUE}4. 문서 저장 테스트...${NC}"
    curl -s -X POST http://127.0.0.1:8000/documents/ \
      -H "Content-Type: application/json" \
      -d "{
        \"content\": \"# 주간 업무 보고서\\n\\n## 1. 프로젝트 진행 상황\\n- 자동화 스크립트 구현 완료 (진행률: 90%)\\n- 테스트 케이스 작성 및 실행 (진행률: 75%)\\n- 사용자 인터페이스 디자인 완료\\n\\n## 2. 이슈 사항\\n- API 응답 시간 개선 필요 (평균 2초 → 목표 0.5초)\\n- 메모리 사용량 최적화 작업 진행 중\\n\\n## 3. 다음 주 계획\\n- 성능 테스트 및 최적화\\n- 사용자 매뉴얼 작성\\n- 최종 배포 준비\",
        \"document_name\": \"$DOCUMENT_ID\",
        \"document_path\": \"reports/weekly/2025-04\",
        \"metadata\": {
          \"author\": \"jjin4363\",
          \"department\": \"개발팀\",
          \"category\": \"주간보고서\",
          \"version\": \"1.0\",
          \"is_reviewed\": \"true\",
          \"is_final\": \"false\"
        }
      }" | jq .
    echo ""

    # 5. 파일 업로드 테스트 - 메타데이터 간소화 및 불리언 제거
    echo -e "${BLUE}5. 파일 업로드 테스트...${NC}"
    echo -e "# 테스트 파일 내용\n이것은 업로드 테스트를 위한 임시 파일입니다." > temp_upload_test.txt

    curl -s -X POST http://127.0.0.1:8000/documents/upload/ \
      -F "file=@temp_upload_test.txt" \
      -F "document_name=테스트파일_$(date +%Y%m%d)" \
      -F "metadata={\"source\":\"테스트\",\"importance\":\"높음\",\"is_temp\":\"true\"}" | jq .

    rm temp_upload_test.txt
    echo ""
fi

# 6. 워크플로우 저장 - 정상 작동
echo -e "${BLUE}6. 워크플로우 저장 테스트...${NC}"
curl -s -X POST http://127.0.0.1:8000/workflows/ \
  -H "Content-Type: application/json" \
  -d "{
    \"steps\": [
      {
        \"name\": \"데이터 수집\",
        \"description\": \"DB에서 필요 데이터 조회\", 
        \"code\": \"SELECT * FROM sales WHERE date > '2025-04-01'\"
      },
      {
        \"name\": \"데이터 가공\",
        \"description\": \"필요 필드 추출 및 집계\",
        \"code\": \"df = df.groupby('product').sum().reset_index()\"
      },
      {
        \"name\": \"시각화\",
        \"description\": \"그래프 생성\",
        \"code\": \"plt.plot(df['product'], df['sales_amount'])\"
      },
      {
        \"name\": \"보고서 생성\",
        \"description\": \"PDF 형식으로 저장\"
      }
    ],
    \"mcp_name\": \"자동_보고서_생성\",
    \"metadata\": {
      \"created_by\": \"jjin4363\",
      \"department\": \"데이터분석팀\",
      \"priority\": \"중간\"
    }
  }" | jq .
echo ""

# 7. 요약 저장 - 정상 작동
echo -e "${BLUE}7. 요약 저장 테스트...${NC}"
curl -s -X POST http://127.0.0.1:8000/summaries/ \
  -H "Content-Type: application/json" \
  -d "{
    \"content\": \"오늘은 프로젝트 자동화 작업을 주로 진행했습니다. 데이터 수집과 처리 스크립트를 개선했고, CI/CD 파이프라인 설정을 완료했습니다. 다음 단계로 사용자 인터페이스 개선 작업을 진행할 예정입니다.\",
    \"date\": \"$DATE_TODAY\",
    \"task_name\": \"$TASK_NAME\",
    \"metadata\": {
      \"author\": \"jjin4363\",
      \"project\": \"데이터 자동화\",
      \"priority\": \"높음\"
    }
  }" | jq .
echo ""

# 8. 통합 검색 - 정상 작동
echo -e "${BLUE}8. 통합 검색 테스트...${NC}"
curl -s -X POST http://127.0.0.1:8000/search/ \
  -H "Content-Type: application/json" \
  -d "{
    \"query\": \"자동화 프로젝트\",
    \"collections\": [\"documents\", \"workflows\", \"summaries\"],
    \"limit\": 5
  }" | jq .
echo ""

# 잠시 기다려 인덱싱이 완료되도록 함
echo "인덱싱이 완료될 때까지 3초 대기..."
sleep 3

# 11. 특정 날짜 작업 요약 조회 - 정상 작동
echo -e "${BLUE}11. 특정 날짜 작업 요약 조회 테스트...${NC}"
curl -s http://127.0.0.1:8000/summaries/date/$DATE_TODAY | jq .
echo ""

# 13. 요약 생성 - 정상 작동
echo -e "${BLUE}13. 요약 생성 테스트...${NC}"
curl -s -X POST http://127.0.0.1:8000/generate-summary/ \
  -H "Content-Type: application/json" \
  -d "{
    \"content\": \"자연어 처리(NLP)는 컴퓨터가 인간의 언어를 이해하고 처리하는 인공지능의 한 분야입니다. 최근 몇 년간 언어 모델의 발전으로 기계 번역, 감정 분석, 문서 요약 등 다양한 응용 분야에서 큰 발전이 있었습니다. 특히 트랜스포머 기반 모델의 등장으로 문맥 이해 능력이 크게 향상되었으며, 이는 다양한 비즈니스 솔루션에 적용되고 있습니다. 자연어 처리 기술은 고객 서비스 자동화, 내부 지식 관리, 데이터 분석 등에 활용되어 업무 효율성을 높이는데 기여하고 있습니다.\",
    \"content_type\": \"document\",
    \"max_length\": 200
  }" | jq .
echo ""

echo -e "${GREEN}===== 테스트 완료 =====${NC}"
echo ""
if [ "$MODE" = "partial" ]; then
    echo -e "${RED}참고: 현재 일부 엔드포인트만 테스트되었습니다. 모든 엔드포인트를 테스트하려면 'full' 모드를 사용하세요.${NC}"
    echo -e "사용법: ./test_fixed.sh full"
fi