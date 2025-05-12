"""
MCP 웹 검색 통합 테스트
"""
import sys
import os
import time
import logging

# 프로젝트 경로 추가
sys.path.append(os.path.join(os.path.dirname(__file__)))

# 로깅 설정
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)

from app.utils.mcp_web_research import mcp_web_research, web_search, determine_query_type, perform_web_search


def test_mcp_initialization():
    """MCP 초기화 테스트"""
    print("\n1. MCP 초기화 테스트")
    print("=" * 50)
    
    if not mcp_web_research:
        print("❌ MCP가 비활성화되어 있습니다")
        return False
    
    if mcp_web_research.initialized:
        print("✅ MCP 서버가 초기화되었습니다")
        
        # 프로세스 정보
        info = mcp_web_research.get_process_info()
        print(f"프로세스 상태: {info}")
        
        # 사용 가능한 도구
        print(f"사용 가능한 도구: {mcp_web_research.available_tools}")
        
        return True
    else:
        print("❌ MCP 서버 초기화 실패")
        return False


def test_query_type_detection():
    """쿼리 타입 감지 테스트"""
    print("\n2. 쿼리 타입 감지 테스트")
    print("=" * 50)
    
    test_queries = {
        "Python 프로그래밍": "info",
        "고양이 사진": "photo",
        "오늘 날씨": "web",
        "최신 뉴스": "web",
        "안녕하세요": "ambiguous"
    }
    
    for query, expected in test_queries.items():
        result = determine_query_type(query)
        status = "✅" if result == expected else "❌"
        print(f"{status} '{query}' → {result} (예상: {expected})")


def test_google_search():
    """Google 검색 테스트"""
    print("\n3. Google 검색 테스트")
    print("=" * 50)
    
    if not mcp_web_research or not mcp_web_research.initialized:
        print("⚠️ MCP가 초기화되지 않아 검색을 건너뜁니다")
        return
    
    test_queries = [
        "Python",
        "테스트",
        "hello world"
    ]
    
    for query in test_queries:
        print(f"\n검색: {query}")
        result = mcp_web_research.search_google(query)
        
        if "error" in result:
            print(f"❌ 검색 실패: {result.get('message', result['error'])}")
            if 'suggestion' in result:
                print(f"💡 제안: {result['suggestion']}")
        else:
            print(f"✅ 검색 성공: {len(result)} 결과" if isinstance(result, list) else "✅ 검색 성공")


def test_page_visit():
    """웹페이지 방문 테스트"""
    print("\n4. 웹페이지 방문 테스트")
    print("=" * 50)
    
    if not mcp_web_research or not mcp_web_research.initialized:
        print("⚠️ MCP가 초기화되지 않아 페이지 방문을 건너뜁니다")
        return
    
    test_urls = [
        "https://www.python.org",
        "https://example.com"
    ]
    
    for url in test_urls:
        print(f"\n방문: {url}")
        result = mcp_web_research.visit_page(url)
        
        if "error" in result:
            print(f"❌ 방문 실패: {result['error']}")
        else:
            content = result.get("content", "")
            print(f"✅ 방문 성공: {len(content)} 문자")


def test_web_search_integration():
    """통합 웹 검색 테스트"""
    print("\n5. 통합 웹 검색 테스트")
    print("=" * 50)
    
    test_queries = [
        "Python 최신 버전",
        "2024년 기술 트렌드",
        "날씨 정보"
    ]
    
    for query in test_queries:
        print(f"\n질문: {query}")
        print("-" * 40)
        
        # 전체 웹 검색 프로세스
        answer = web_search(query)
        print(f"답변:\n{answer[:500]}...")  # 첫 500자만 출력


def test_error_handling():
    """오류 처리 테스트"""
    print("\n6. 오류 처리 테스트")
    print("=" * 50)
    
    if not mcp_web_research:
        print("⚠️ MCP가 비활성화되어 있어 오류 처리를 테스트할 수 없습니다")
        return
    
    # 잘못된 쿼리
    print("\n잘못된 쿼리 테스트:")
    result = mcp_web_research.search_google("")
    print(f"빈 쿼리 결과: {result}")
    
    # 잘못된 URL
    print("\n잘못된 URL 테스트:")
    result = mcp_web_research.visit_page("invalid-url")
    print(f"잘못된 URL 결과: {result}")


def main():
    """메인 실행 함수"""
    print("🚀 MCP 웹 검색 통합 테스트")
    print("=" * 70)
    
    print("\n환경 정보:")
    print(f"Python 버전: {sys.version}")
    print(f"작업 디렉토리: {os.getcwd()}")
    
    # 각 테스트 실행
    tests = [
        test_mcp_initialization,
        test_query_type_detection,
        test_google_search,
        test_page_visit,
        test_web_search_integration,
        test_error_handling
    ]
    
    passed = 0
    failed = 0
    
    for test in tests:
        try:
            test()
            passed += 1
        except Exception as e:
            failed += 1
            print(f"\n🚨 테스트 실패: {test.__name__}")
            print(f"오류: {e}")
            import traceback
            traceback.print_exc()
    
    # 최종 결과
    print("\n" + "=" * 70)
    print(f"테스트 완료: {passed}개 통과, {failed}개 실패")
    
    # 정리
    print("\n리소스 정리 중...")
    if mcp_web_research:
        mcp_web_research.cleanup()
    print("✅ 테스트 종료")


if __name__ == "__main__":
    main()
