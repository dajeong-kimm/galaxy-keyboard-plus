FROM python:3.9-slim

# 필수 패키지 설치
RUN apt-get update && apt-get install -y \
    git \
    curl \
    gnupg \
    && curl -fsSL https://deb.nodesource.com/setup_16.x | bash - \
    && apt-get install -y nodejs \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Node.js 버전 확인
RUN node --version && npm --version

WORKDIR /app

# Python 의존성 설치
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 애플리케이션 코드 복사
COPY . .

# MCP 서버 저장소 클론 및 설정
RUN mkdir -p mcp_servers/web_search && \
    git clone https://github.com/pskill9/web-search.git mcp_servers/web_search && \
    cd mcp_servers/web_search && \
    npm install && \
    npm run build

# 포트 노출
EXPOSE 8050

# 실행
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8050"]