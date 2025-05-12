"""
브라우저 문제 진단 스크립트
"""
import subprocess
import os
import time


def test_playwright_google():
    """Playwright로 직접 Google 검색 테스트"""
    print("Playwright Google 검색 테스트")
    print("=" * 50)
    
    test_script = """
import asyncio
from playwright.async_api import async_playwright

async def test_google():
    async with async_playwright() as p:
        # 브라우저 옵션
        browser = await p.chromium.launch(
            headless=False,  # GUI로 표시
            args=[
                '--disable-blink-features=AutomationControlled',
                '--disable-dev-shm-usage',
                '--no-sandbox',
                '--disable-setuid-sandbox',
            ]
        )
        
        page = await browser.new_page()
        
        # User-Agent 설정
        await page.set_extra_http_headers({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
        })
        
        try:
            print("Google 접속 중...")
            await page.goto('https://www.google.com', wait_until='networkidle')
            
            print("페이지 제목:", await page.title())
            
            # 쿠키 동의 버튼이 있으면 클릭
            try:
                await page.click('text="Accept all"', timeout=5000)
            except:
                pass
            
            # 검색창 찾기
            search_box = await page.query_selector('input[name="q"]')
            if search_box:
                print("검색창 찾음")
                await search_box.type('hello world')
                await page.keyboard.press('Enter')
                
                # 검색 결과 대기
                await page.wait_for_selector('#search', timeout=10000)
                print("검색 결과 로드됨")
                
                # 결과 확인
                results = await page.query_selector_all('#search .g')
                print(f"검색 결과 수: {len(results)}")
                
                if len(results) == 0:
                    print("검색 결과가 없습니다 - Google이 브라우저를 차단했을 수 있습니다")
                    
                    # 페이지 스크린샷
                    await page.screenshot(path='google_blocked.png')
                    print("스크린샷 저장: google_blocked.png")
            else:
                print("검색창을 찾을 수 없습니다")
                
        except Exception as e:
            print(f"오류: {e}")
            
        await browser.close()

# 실행
asyncio.run(test_google())
"""
    
    # 테스트 스크립트 저장
    with open("playwright_test.py", "w", encoding="utf-8") as f:
        f.write(test_script)
    
    # 실행
    print("\nPlaywright 테스트 실행...")
    result = subprocess.run(
        ["python", "playwright_test.py"],
        capture_output=True,
        text=True
    )
    
    print("\n출력:")
    print(result.stdout)
    
    if result.stderr:
        print("\n오류:")
        print(result.stderr)


def test_alternative_approach():
    """대체 접근 방법 테스트"""
    print("\n\n대체 접근 방법")
    print("=" * 50)
    
    # MCP 설정 수정 제안
    print("1. MCP 서버 설정 수정:")
    print("   - 헤드리스 모드 비활성화")
    print("   - 사용자 에이전트 변경")
    print("   - 프록시 사용")
    
    print("\n2. 대체 검색 엔진 사용:")
    print("   - DuckDuckGo")
    print("   - Bing")
    print("   - Searx")
    
    print("\n3. 캐싱 전략:")
    print("   - 이전 검색 결과 캐싱")
    print("   - 오프라인 폴백")


def check_google_access():
    """Google 접근성 확인"""
    print("\n\nGoogle 접근성 확인")
    print("=" * 50)
    
    # curl로 Google 접근 테스트
    print("1. curl로 Google 접근 테스트...")
    result = subprocess.run(
        ["curl", "-I", "https://www.google.com"],
        capture_output=True,
        text=True,
        shell=True
    )
    
    if result.returncode == 0:
        print("✅ Google 접근 가능")
        print(result.stdout[:200])
    else:
        print("❌ Google 접근 불가")
    
    # Chrome DevTools Protocol 테스트
    print("\n2. Chrome DevTools Protocol 테스트...")
    cdp_test = """
const puppeteer = require('puppeteer');

(async () => {
    const browser = await puppeteer.launch({
        headless: false,
        args: [
            '--disable-blink-features=AutomationControlled',
            '--disable-dev-shm-usage',
            '--no-sandbox',
            '--disable-setuid-sandbox',
            '--disable-web-security',
            '--disable-features=IsolateOrigins,site-per-process'
        ],
        defaultViewport: null
    });
    
    const page = await browser.newPage();
    
    // 자동화 감지 우회
    await page.evaluateOnNewDocument(() => {
        Object.defineProperty(navigator, 'webdriver', {
            get: () => false
        });
    });
    
    await page.goto('https://www.google.com');
    console.log('Title:', await page.title());
    
    await browser.close();
})();
"""
    
    with open("cdp_test.js", "w") as f:
        f.write(cdp_test)
    
    print("Node.js 스크립트 작성됨: cdp_test.js")


def suggest_solutions():
    """해결 방안 제시"""
    print("\n\n해결 방안")
    print("=" * 50)
    
    print("1. 브라우저 프로필 사용:")
    print("   - 실제 사용자 프로필 사용")
    print("   - 쿠키와 세션 유지")
    
    print("\n2. 대체 검색 API:")
    print("   - Google Custom Search API")
    print("   - Bing Search API")
    print("   - SerpAPI")
    
    print("\n3. Stealth 플러그인:")
    print("   - puppeteer-extra-plugin-stealth")
    print("   - playwright-stealth")
    
    print("\n4. 프록시/VPN 우회:")
    print("   - 주거용 프록시 사용")
    print("   - 다양한 IP 로테이션")


def main():
    """메인 실행"""
    print("Google 검색 실패 진단")
    print("=" * 70)
    
    # 1. Playwright 직접 테스트
    test_playwright_google()
    
    # 2. Google 접근성 확인
    check_google_access()
    
    # 3. 대체 접근 방법
    test_alternative_approach()
    
    # 4. 해결 방안 제시
    suggest_solutions()
    
    print("\n" + "=" * 70)
    print("진단 완료")
    
    print("\n권장 해결책:")
    print("1. 일시적으로 헤드리스 모드를 비활성화하여 테스트")
    print("2. Google Custom Search API 사용")
    print("3. DuckDuckGo 같은 대체 검색 엔진 사용")
    print("4. 검색 결과를 미리 캐싱하거나 모킹")


if __name__ == "__main__":
    main()
