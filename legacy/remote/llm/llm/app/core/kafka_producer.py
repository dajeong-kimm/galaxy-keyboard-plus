# llm/app/core/kafka_producer.py
import asyncio
from contextlib import asynccontextmanager
from aiokafka import AIOKafkaProducer
from fastapi import HTTPException, status
from fastapi import FastAPI

from app.core.config import get_settings

# Producer 인스턴스를 저장할 변수 (애플리케이션 상태)
_kafka_producer: AIOKafkaProducer | None = None

async def start_kafka_producer():
    """애플리케이션 시작 시 Kafka Producer를 생성하고 시작합니다."""
    global _kafka_producer
    if _kafka_producer is not None:
        print("Kafka producer already started.")
        return

    settings = get_settings()
    print(f"Connecting to Kafka bootstrap servers: {settings.kafka_bootstrap_servers}")
    try:
        producer = AIOKafkaProducer(
            bootstrap_servers=settings.kafka_bootstrap_servers,
            # value_serializer=lambda v: json.dumps(v).encode('utf-8'), # 필요시 직렬화 방식 지정
            # acks='all', # 필요시 전송 확인 방식 지정
            # 기타 필요한 Producer 설정 추가 가능
        )
        await producer.start()
        _kafka_producer = producer
        print("Kafka producer started successfully.")
    except Exception as e:
        print(f"!!! Failed to start Kafka producer: {e}")
        # Kafka 연결 실패 시 애플리케이션 시작을 막거나,
        # producer를 None으로 두어 의존성 주입 시 에러가 나도록 할 수 있습니다.
        _kafka_producer = None # 시작 실패 시 None 유지

async def stop_kafka_producer():
    """애플리케이션 종료 시 Kafka Producer를 중지합니다."""
    global _kafka_producer
    if _kafka_producer:
        print("Stopping Kafka producer...")
        try:
            await _kafka_producer.stop()
            print("Kafka producer stopped successfully.")
        except Exception as e:
            print(f"!!! Failed to stop Kafka producer gracefully: {e}")
        finally:
            _kafka_producer = None
    else:
        print("Kafka producer was not running.")


def get_kafka_producer() -> AIOKafkaProducer:
    """FastAPI 의존성 주입을 통해 Kafka Producer 인스턴스를 제공합니다."""
    if _kafka_producer is None:
        # Producer가 시작되지 않았거나 실패한 경우
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Kafka producer is not available.",
        )
    return _kafka_producer

# FastAPI lifespan 에서 사용할 컨텍스트 매니저 (선택적 - main.py 에서 직접 구현도 가능)
@asynccontextmanager
async def kafka_lifespan(app: FastAPI): # <--- app: FastAPI 매개변수 추가
    """Kafka Producer 시작/중지를 위한 lifespan 컨텍스트 매니저"""
    print("--- Application lifespan start: Starting Kafka producer ---") # 시작 로그 추가
    await start_kafka_producer()
    try:
        yield # 애플리케이션 실행
    finally:
        print("--- Application lifespan end: Stopping Kafka producer ---") # 종료 로그 추가
        await stop_kafka_producer()