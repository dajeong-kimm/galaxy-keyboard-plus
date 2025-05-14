# LLM Bridge Server

Electron 클라이언트와 OpenAI API 간의 브리지 역할을 하는 FastAPI 기반 서버입니다.

## 🌟 주요 기능

* **OpenAI GPT-4o-mini 모델 연동:** OpenAI의 최신 모델 중 하나인 gpt-4o-mini를 사용하여 채팅 완료 기능을 제공합니다.
* **다양한 응답 방식 지원:**
    * `/llm/chat`: 단일 API 호출로 전체 응답을 JSON 형식으로 반환합니다.
    * `/llm/chat/stream`: OpenAI API의 네이티브 스트리밍 응답을 SSE(Server-Sent Events)로 실시간 중계합니다.
    * `/llm/chat/stream-buffer`: OpenAI 응답 스트림을 내부적으로 버퍼링하여, 지정된 크기(기본 256바йт)의 텍스트 청크 단위로 SSE를 전송합니다. 네트워크 지연이 있거나 클라이언트 렌더링 부하를 줄이고 싶을 때 유용합니다.
* **유연한 설정:** OpenAI API 키와 스트림 버퍼 크기를 `.env` 파일을 통해 설정할 수 있습니다.
* **오류 처리:** OpenAI API 오류를 적절한 HTTP 상태 코드와 메시지로 변환하여 반환하며, 스트리밍 중 오류 발생 시 SSE 오류 이벤트를 전송합니다.

## 🛠️ 기술 스택

* **백엔드:** Python 3.11+, FastAPI, Uvicorn
* **LLM:** OpenAI GPT-4o-mini (API 사용)
* **라이브러리:** `openai`, `python-dotenv`

## 🚀 시작하기

### 1. 사전 요구 사항

* Python 3.11 이상 설치
* 가상 환경 도구 (예: `venv`)

### 2. 설정

```bash
# 1. 프로젝트 클론 또는 다운로드
# git clone <repository_url>
# cd llm

# 2. 가상 환경 생성 및 활성화
python -m venv venv
# Windows
# venv\Scripts\activate
# macOS/Linux
# source venv/bin/activate

# 3. 필요한 라이브러리 설치
pip install -r requirements.txt

# 4. 환경 변수 설정
# .env.example 파일을 .env 로 복사합니다.
cp .env.example .env

# 5. .env 파일을 열고 실제 OpenAI API 키를 입력합니다.
# OPENAI_API_KEY="sk-your_openai_api_key_here"
# 필요한 경우 STREAM_BUFFER_SIZE 주석을 해제하고 값을 수정합니다.
# STREAM_BUFFER_SIZE=512