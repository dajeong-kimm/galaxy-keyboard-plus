FROM node:18-slim

WORKDIR /app

# 패키지 초기화 및 필요한 의존성 설치
RUN npm init -y && \
    npm install express body-parser && \
    npm install -g @modelcontextprotocol/server-brave-search

# 프록시 서버 스크립트 추가
COPY proxy-server.js .

# package.json 수정 - 타입을 module로 설정
RUN node -e "const pkg=require('./package.json'); pkg.type='module'; require('fs').writeFileSync('./package.json', JSON.stringify(pkg, null, 2))"

# 포트 노출
EXPOSE 8100

# 헬스체크 추가
HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:8100/health || exit 1

# 프록시 서버 실행
CMD ["node", "proxy-server.js"]