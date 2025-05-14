# Dockerfile

# 1. 베이스 이미지 선택
# Python 3.11 공식 이미지를 사용합니다. slim 버전을 사용하여 이미지 크기를 줄입니다.
FROM python:3.11-slim

# 2. 환경 변수 설정
# Python 로그 및 출력이 버퍼링 없이 즉시 터미널에 표시되도록 설정
ENV PYTHONUNBUFFERED=1
# Python이 .pyc 파일을 생성하지 않도록 설정 (컨테이너 환경에서는 불필요)
ENV PYTHONDONTWRITEBYTECODE=1
# 애플리케이션이 실행될 기본 포트 및 호스트 설정
ENV PORT=8092
ENV HOST=0.0.0.0

# 3. 작업 디렉토리 설정
# 컨테이너 내에서 명령어가 실행될 기본 경로 설정
WORKDIR /app

# 4. 시스템 의존성 설치 (필요한 경우)
# 이 프로젝트는 특별한 시스템 라이브러리가 필요하지 않으므로 이 단계는 생략합니다.
# 예: RUN apt-get update && apt-get install -y --no-install-recommends gcc

# 5. Python 의존성 설치
# pip를 최신 버전으로 업그레이드
RUN pip install --no-cache-dir --upgrade pip
# requirements.txt 파일을 먼저 복사하여 Docker 레이어 캐시 활용
COPY requirements.txt .
# 의존성 설치 (--no-cache-dir 옵션으로 불필요한 캐시 제외하여 이미지 크기 감소)
RUN pip install --no-cache-dir -r requirements.txt

# 6. 애플리케이션 코드 복사
# 현재 디렉토리(빌드 컨텍스트)의 app 폴더를 컨테이너의 /app/app 폴더로 복사
# 이렇게 해야 Python이 app.main, app.core 등을 올바르게 임포트할 수 있습니다.
COPY ./app /app/app

# 7. 포트 노출
# 컨테이너가 ${PORT} 환경 변수에 지정된 포트(기본값 8092)에서 수신 대기함을 명시
EXPOSE ${PORT}

# 8. 기본 실행 명령어 정의
# 컨테이너가 시작될 때 실행될 명령어 정의 (Uvicorn 서버 실행)
# 0.0.0.0 호스트를 사용하여 컨테이너 외부에서도 접근 가능하도록 설정
# exec 형식을 사용하여 불필요한 쉘 프로세스 생성 방지
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8092"]