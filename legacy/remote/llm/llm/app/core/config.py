# llm/app/core/config.py
import os
from functools import lru_cache

from dotenv import load_dotenv
from pydantic_settings import BaseSettings, SettingsConfigDict

# .env 파일 로드 (애플리케이션 시작 시 한 번)
load_dotenv()

class Settings(BaseSettings):
    """애플리케이션 설정을 관리하는 클래스 (pydantic-settings 사용)"""
    # .env 파일 및 환경 변수에서 값을 읽어옴
    # 필드 이름은 대소문자 구분 없이 환경 변수 이름과 매칭됨
    openai_api_key: str
    stream_buffer_size: int = 256 # 기본값 256

    # --- Kafka 설정 ---
    kafka_bootstrap_servers: str = "http://k12e201.p.ssafy.io:9092" # 기본값 설정 (쉼표로 여러개 지정 가능)
    kafka_log_topic: str = "llm_logs" # 로그 토픽 이름 (예시)
    kafka_usage_topic: str = "token-usage" # 토큰 사용량 토픽 이름 (예시)

    # pydantic-settings 설정
    model_config = SettingsConfigDict(
        env_file='.env', # .env 파일 명시적 지정
        env_file_encoding='utf-8',
        extra='ignore' # .env 파일에 정의되지 않은 추가 필드 무시
    )

@lru_cache
def get_settings() -> Settings:
    """설정 객체를 반환하는 함수 (캐싱 사용)"""
    try:
        return Settings()
    except ValueError as e:
        # pydantic-settings 가 유효성 검사 실패 시 ValueError 발생
        print(f"오류: 설정 로드 실패 - {e}")
        # 필수 환경 변수(OPENAI_API_KEY) 누락 등의 문제일 수 있음
        raise ValueError(f"환경 변수 설정 오류: {e}") from e

# 예: 의존성 주입을 위해 개별 설정값을 가져오는 함수
def get_stream_buffer_size() -> int:
    """스트림 버퍼 크기를 반환하는 의존성 함수"""
    settings = get_settings()
    return settings.stream_buffer_size