@echo on
setlocal

echo 현재 작업 디렉토리 확인:
cd

:: 가상환경 생성
if not exist "venv" (
    echo [🔧] 가상환경 생성 중...
    python -m venv venv
)

:: 가상환경 활성화
echo [🚀] 가상환경 활성화...
call venv\Scripts\activate

:: 의존성 설치
echo [📦] requirements 설치 중...
pip install -r requirements.txt

:: 서버 실행
echo [🔥] FastAPI 서버 실행 중...
python -m uvicorn app.main:app --reload --port 8090

endlocal
pause
