FROM python:3.10-slim

WORKDIR /app

# 의존성 파일 복사 및 설치
COPY fastapi/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# .env 파일 복사
COPY .env .

# 애플리케이션 코드 복사
COPY fastapi/ ./

# 서버 실행 명령
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8080"]