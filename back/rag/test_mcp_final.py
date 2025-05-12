"""
MCP 웹 검색 최종 테스트
"""
import sys
import os
import logging

# 프로젝트 경로 추가
sys.path.append(os.path.join(os.path.dirname(__file__)))

# 로깅 설정
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)

from app.utils.mcp_web_research import mcp_web_research, web_search


def diagnose_issue():
    """문제 진단"""
    print("\n문제 진단")
    print("=" * 50)
    
    # 1. 브라우저 경로 확인
    chromium_path = mcp_web_research._find_chromium_path()
    print(f"Chromium 경로: {chromium_path}")
    
    if not chromium_path:
        print("⚠️ Chromium을 찾을 수 없습니다.")
        print("\n해결 방법:")
        print("1. npx playwright install chromium 실행")
        print("2. Chrome 브라우저 설치")
        print("3. Windows Defender/방화벽 확인")
    else:
        print("✅ 브라우저 찾기 성공")
    
    # 2. 프로세스 상태
    info = mcp_web_research.get_process_info()
    print(f"\nMCP 프로세스 상태: {info}")
    
    # 3. 사용 가능한 도구
    print(f"\n사용 가능한 도구: {mcp_web_research.available_tools}")


def test_search():
    """검색 테스트"""
    print("\n검색 테스트")
    print("=" * 50)
    
    test_queries = [
        "Python programming",
        "hello world",
        "test search"
    ]
    
    for query in test_queries:
        print(f"\n검색: {query}")
        result = mcp_web_research.search_google(query)
        
        if isinstance(result, dict) and "error" in result:
            print(f"❌ 검색 실패: {result['error']}")
            if 'browser_found' in result:
                print(f"브라우저 발견: {result['browser_found']}")
                print(f"브라우저 경로: {result.get('browser_path', 'N/A')}")
            if 'raw_error' in result:
                print(f"원본 오류: {result['raw_error'][:200]}...")
        elif isinstance(result, list):
            print(f"✅ 검색 성공: {len(result)}개 결과")
        else:
            print(f"예상치 못한 결과: {type(result)}")


def test_web_search_integration():
    """통합 웹 검색 테스트"""
    print("\n통합 웹 검색 테스트")
    print("=" * 50)
    
    query = "Python 최신 버전"
    print(f"질문: {query}")
    
    answer = web_search(query)
    print(f"\n답변:\n{answer}")


def main():
    """메인 실행"""
    print("MCP 웹 검색 최종 테스트 및 진단")
    print("=" * 70)
    
    if not mcp_web_research:
        print("❌ MCP가 비활성화되어 있습니다")
        return
    
    if not mcp_web_research.initialized:
        print("❌ MCP 초기화 실패")
        diagnose_issue()
        return
    
    print("✅ MCP 초기화 성공")
    
    # 진단
    diagnose_issue()
    
    # 검색 테스트
    test_search()
    
    # 통합 테스트
    test_web_search_integration()
    
    print("\n" + "=" * 70)
    print("테스트 완료")


if __name__ == "__main__":
    main()
