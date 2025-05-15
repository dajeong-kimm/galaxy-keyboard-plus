# 1. Node.js 공식 빌드 이미지를 사용 (Next.js 권장)
FROM node:22.13.0-alpine AS builder

# 2. 작업 디렉토리 설정
WORKDIR /app

# 3. 의존성 파일 복사 및 설치
COPY apk-fe/package.json apk-fe/package-lock.json ./
RUN npm install

# 4. 소스 코드 복사 및 Next.js 앱 빌드
COPY apk-fe/ ./
RUN npm run build

# 5. 실행을 위한 경량 Node.js 이미지 사용
FROM node:22.13.0-alpine AS runner

# 6. 작업 디렉토리 설정
WORKDIR /app

# 7. 빌드된 결과물과 필요한 파일만 복사
COPY --from=builder /app/.next /app/.next
COPY --from=builder /app/public /app/public
COPY --from=builder /app/package.json /app/package.json
COPY --from=builder /app/node_modules /app/node_modules

# 8. 서버시간 한국시간 설정
RUN apk add --no-cache tzdata && \
    ln -sf /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone && \
    apk del tzdata

# 9. 환경 변수 및 포트 설정
ENV PORT 3000
EXPOSE 3000

# 10. Next.js 앱 실행
CMD ["npm", "start"]
