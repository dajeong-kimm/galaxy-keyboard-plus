######################## 1) Build Stage ########################
# Gradle 8.7 + JDK 17 이미지 → gradle-wrapper.jar 없어도 동작
FROM gradle:8.7-jdk17 AS build
WORKDIR /workspace

# 프로젝트 전체 복사 (gradle, src, settings 등)
COPY . .

# 의존 캐시 최적화: 먼저 설정 파일만으로 dependencies 다운로드
RUN gradle dependencies --no-daemon

# 전체 빌드
RUN gradle bootJar --no-daemon


######################## 2) Runtime Stage ######################
FROM eclipse-temurin:17-jre
WORKDIR /app

# 빌드 산출물만 복사
COPY --from=build /workspace/build/libs/*.jar app.jar

HEALTHCHECK CMD curl -f http://localhost:8083/actuator/health || exit 1
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "/app/app.jar"]
