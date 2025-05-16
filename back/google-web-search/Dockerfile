FROM node:18-slim

WORKDIR /app

# Google Search MCP 서버 설치
RUN apt-get update && \
    apt-get install -y git python3 build-essential && \
    git clone https://github.com/mixelpixx/Google-Search-MCP-Server.git . && \
    npm install && \
    npm run build && \
    rm -rf /var/lib/apt/lists/*

# 프록시 서버 스크립트 복사
COPY proxy-server.js .

# 포트 노출
EXPOSE 8150

# 실행 명령어
CMD ["node", "proxy-server.js"]