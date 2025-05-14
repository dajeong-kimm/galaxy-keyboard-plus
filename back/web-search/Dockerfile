FROM node:18-slim

WORKDIR /app

# 저장소 클론
RUN apt-get update && apt-get install -y git && \
    git clone https://github.com/pskill9/web-search.git . && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# 의존성 설치 및 빌드
RUN npm install && \
    npm run build

# 헬스 체크 엔드포인트를 위한 수정 (선택사항)
RUN echo 'app.get("/health", (req, res) => res.status(200).send("OK"));' >> src/index.ts && \
    npm run build

# 포트 노출
EXPOSE 8100

# 실행
CMD ["node", "build/index.js", "--port", "8100"]