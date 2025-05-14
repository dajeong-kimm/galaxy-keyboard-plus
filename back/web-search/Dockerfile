FROM node:18-slim

WORKDIR /app

# 저장소 클론 및 빌드
RUN apt-get update && apt-get install -y git && \
    git clone https://github.com/pskill9/web-search.git . && \
    npm install && \
    npm run build && \
    npm install express body-parser && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# 프록시 서버 스크립트 추가
COPY proxy-server.js .

# 포트 노출
EXPOSE 8100

# 프록시 서버 실행
CMD ["node", "proxy-server.js"]