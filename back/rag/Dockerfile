FROM python:3.11-slim

# GCP 서비스 계정 키 경로 지정
ENV GOOGLE_APPLICATION_CREDENTIALS=/app/gcp-key.json
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# requirements.txt 는 rag/requirements.txt 로 올라와 있어야 합니다.
COPY requirements.txt .  
RUN pip install --no-cache-dir -r requirements.txt

# GCP 키 파일을 컨테이너 내부로 복사
COPY gcp-key.json /app/gcp-key.json

COPY app/ ./app

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090"]