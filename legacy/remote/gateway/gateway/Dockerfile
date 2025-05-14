# 1단계: 빌드 단계
FROM gradle:7.6.0-jdk17-alpine AS builder

# 작업 디렉토리 설정
WORKDIR /gateway

# 모든 파일 복사 (build.gradle, settings.gradle, src, etc.)
COPY . .

# Gradle Wrapper 실행 권한 부여
RUN chmod +x ./gradlew

# Gradle Wrapper를 사용해 jar 빌드 (테스트 생략)
RUN ./gradlew clean build -x test

# 2단계: 실행 단계
FROM openjdk:17-alpine

# 작업 디렉토리 설정
WORKDIR /gateway

# 타임존 설정 (옵션)
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
    && echo "Asia/Seoul" > /etc/timezone \
    && apk del tzdata

# 빌드 단계에서 생성된 jar 복사
COPY --from=builder /gateway/build/libs/*.jar app.jar

# jar 실행
ENTRYPOINT ["java", "-jar", "/gateway/app.jar"]

# 포트 오픈
EXPOSE 8090
