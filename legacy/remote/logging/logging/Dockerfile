# 1. 빌드 단계
FROM eclipse-temurin:17-jdk as builder
WORKDIR /app
COPY . /app
RUN ./gradlew clean bootJar -x test

# 2. 실행 단계
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
