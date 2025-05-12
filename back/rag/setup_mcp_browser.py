"""
MCP 브라우저 설정 스크립트
"""
import subprocess
import os
import sys


def setup_chromium():
    """Chromium 브라우저 설정"""
    print("MCP Chromium 브라우저 설정")
    print("=" * 50)
    
    try:
        # 1. Playwright 설치
        print("\n1. Playwright 설치...")
        cmd = ["npm", "install", "playwright"]
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        
        if result.returncode == 0:
            print("✅ Playwright 설치 성공")
        else:
            print(f"❌ Playwright 설치 실패: {result.stderr}")
            return False
        
        # 2. Chromium 브라우저 설치
        print("\n2. Chromium 브라우저 설치...")
        cmd = ["npx", "playwright", "install", "chromium"]
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        
        if result.returncode == 0:
            print("✅ Chromium 설치 성공")
        else:
            print(f"⚠️ Chromium 설치 경고: {result.stderr}")
        
        # 3. 설치 확인
        print("\n3. 설치 확인...")
        import glob
        chromium_path = os.path.expanduser(r"~\AppData\Local\ms-playwright\chromium-*")
        browsers = glob.glob(chromium_path)
        
        if browsers:
            print(f"✅ Chromium 설치 확인: {browsers[0]}")
            
            # Chrome 실행 파일 찾기
            chrome_exe = os.path.join(browsers[0], "chrome-win", "chrome.exe")
            if os.path.exists(chrome_exe):
                print(f"✅ Chrome 실행 파일: {chrome_exe}")
                
                # 환경 변수 설정 제안
                print("\n4. 환경 변수 설정 (선택사항)")
                print("다음 명령을 실행하거나 시스템 환경 변수에 추가하세요:")
                print(f'SET PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH="{chrome_exe}"')
            else:
                print("⚠️ Chrome 실행 파일을 찾을 수 없습니다")
        else:
            print("❌ Chromium이 설치되지 않았습니다")
            return False
        
        print("\n✅ 설정 완료!")
        print("\n다음 단계:")
        print("1. python test_mcp_final.py 실행")
        print("2. 여전히 실패하면 Chrome 브라우저 설치")
        print("3. Windows Defender에서 Node.js와 Chrome 허용")
        
        return True
        
    except Exception as e:
        print(f"\n🚨 오류 발생: {e}")
        import traceback
        traceback.print_exc()
        return False


if __name__ == "__main__":
    setup_chromium()
