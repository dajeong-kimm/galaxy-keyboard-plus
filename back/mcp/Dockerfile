FROM python:3.9-slim

WORKDIR /app

# 의존성 설치
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 애플리케이션 코드 복사
COPY app app/

# 환경 변수 설정 (대신 .env 파일 복사는 제거)
ENV BRAVE_SEARCH_URL="http://web-search:8100"
ENV GOOGLE_WEB_SEARCH_URL="http://google-web-search:8150"

# 포트 노출
EXPOSE 8050

# 실행
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8050"]