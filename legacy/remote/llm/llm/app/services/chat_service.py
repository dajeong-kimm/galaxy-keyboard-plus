# llm/app/services/chat_service.py
import json
import time
from typing import AsyncGenerator, Dict, Any
from aiokafka import AIOKafkaProducer

from fastapi import Depends

from openai import AsyncOpenAI, OpenAIError

from openai.types.chat import ChatCompletionChunk

from app.models.chat import ChatCompletionInput
from app.core.openai_client import generate_error_sse # SSE 오류 생성 헬퍼 사용
from app.core.kafka_producer import get_kafka_producer

from app.core.config import get_settings, Settings

class ChatService:
    """OpenAI 채팅 관련 비즈니스 로직을 처리하는 서비스 클래스"""

    MODEL_NAME = "gpt-4o-mini" # 사용할 모델 고정 또는 설정에서 가져오기

    @staticmethod
    async def create_completion(
        payload: ChatCompletionInput,
        client: AsyncOpenAI,
        producer: AIOKafkaProducer,
        settings: Settings
    ) -> Dict[str, Any]:
        """OpenAI API를 호출하고 응답 반환 + 토큰 사용량 Kafka 전송"""
        try:
            request_data = payload.model_dump(exclude_none=True)
            completion = await client.chat.completions.create(
                model=ChatService.MODEL_NAME,
                **request_data,
                stream=False
            )
            result_dict = completion.model_dump()
        # --- Kafka로 토큰 사용량 정보 전송 ---
            if (usage_info := result_dict.get("usage")) and producer:
                try:
                    usage_message = {
                        "request_id": result_dict.get("id"), # 요청 ID
                        "model": result_dict.get("model"),    # 사용 모델
                        "usage": usage_info,                # 토큰 사용량 상세
                        "api_timestamp_ms": result_dict.get("created", 0) * 1000, # OpenAI 타임스탬프 (ms)
                        "processed_timestamp_ms": int(time.time() * 1000), # 처리 시점 타임스탬프 (ms)
                        "stream": False
                    }
                    message_bytes = json.dumps(usage_message).encode('utf-8')

                    # Kafka 토픽으로 메시지 전송 (send_and_wait는 전송 완료 대기)
                    print(f"--- [Service] Sending usage info to Kafka topic '{settings.kafka_usage_topic}' ---")
                    await producer.send_and_wait(settings.kafka_usage_topic, message_bytes)
                    print(f"--- [Service] Usage info sent successfully ---")

                except Exception as kafka_e:
                    # Kafka 전송 실패는 로깅만 하고 API 응답에는 영향 주지 않음
                    print(f"!!! [Service] Failed to send usage info to Kafka: {kafka_e}")
                    import traceback
                    traceback.print_exc() # 에러 상세 로깅

            return result_dict
        except OpenAIError as e:
            # 서비스 레벨에서 오류를 잡아서 다시 발생시켜 컨트롤러에서 처리하도록 함
            # 또는 여기서 직접 HTTP 예외를 발생시킬 수도 있음 (설계에 따라 다름)
            print(f"OpenAI API Error in create_completion: {e}")
            raise e # 컨트롤러나 전역 핸들러에서 처리하도록 다시 발생

    @staticmethod
    async def stream_completion(
        payload: ChatCompletionInput,
        client: AsyncOpenAI,
        producer: AIOKafkaProducer,
        settings: Settings
    ) -> AsyncGenerator[str, None]:
        """OpenAI API 응답 스트림을 SSE 형식으로 생성 + 완료 후 토큰 사용량 Kafka 전송"""
        usage_info = None
        request_id = None
        model_name = None
        processed_timestamp_ms = int(time.time() * 1000) # 처리 시작 시간 기록

        try:
            request_data = payload.model_dump(exclude_none=True)
            stream = await client.chat.completions.create(
                model=ChatService.MODEL_NAME,
                **request_data,
                stream=True,
                stream_options={"include_usage": True} # Usage 정보 요청
            )
            async for chunk in stream:
                # 스트림 처리 중 ID, 모델, Usage 정보 캡처
                if chunk.id: request_id = chunk.id
                if chunk.model: model_name = chunk.model
                if chunk.usage: usage_info = chunk.usage.model_dump() # 마지막 청크에서 캡처될 것으로 예상

                chunk_json = chunk.model_dump_json()
                yield f"data: {chunk_json}\n\n"

        except OpenAIError as e:
            print(f"OpenAI API Error during stream_completion: {e}")
            error_sse = await generate_error_sse(e)
            yield error_sse
        except Exception as e:
            # 스트리밍 중 예상치 못한 오류 처리
            print(f"Unexpected error during streaming completion: {e}")
            error_json = json.dumps({"error": {"message": "스트리밍 중 서버 내부 오류 발생", "status_code": 500}})
            yield f"event: error\ndata: {error_json}\n\n"
        finally:
            # --- 스트림 종료 후 Kafka 메시지 전송 ---
            if usage_info and request_id and model_name and producer:
                try:
                    usage_message = {
                        "request_id": request_id,
                        "model": model_name,
                        "usage": usage_info,
                        "api_timestamp_ms": None, # 스트림에서는 created 타임스탬프가 청크마다 같지 않을 수 있음
                        "processed_timestamp_ms": processed_timestamp_ms, # 시작 시점 기록
                        "stream": True # 스트림 여부 필드 추가
                    }
                    message_bytes = json.dumps(usage_message).encode('utf-8')
                    print(f"--- [Service Stream] Sending usage info to Kafka topic '{settings.kafka_usage_topic}' ---")
                    # 스트림 응답은 이미 종료되었으므로, send() (fire-and-forget) 사용 고려 가능
                    # send_and_wait 사용 시 Kafka 전송이 느리면 클라이언트 연결 종료 후에도 서버가 잠시 대기할 수 있음
                    await producer.send(settings.kafka_usage_topic, message_bytes)
                    # await producer.send_and_wait(settings.kafka_usage_topic, message_bytes) # 전송 보장 필요시
                    print(f"--- [Service Stream] Usage info sent (fire-and-forget) ---")
                except Exception as kafka_e:
                    print(f"!!! [Service Stream] Failed to send usage info to Kafka: {kafka_e}")
                    import traceback
                    traceback.print_exc()

    @staticmethod
    async def stream_buffered_completion(
        payload: ChatCompletionInput,
        client: AsyncOpenAI,
        buffer_size: int,
        producer: AIOKafkaProducer,
        settings: Settings
    ) -> AsyncGenerator[str, None]:
        """OpenAI 응답 스트림을 버퍼링하여 SSE 형식으로 생성 + 완료 후 토큰 사용량 Kafka 전송"""
        buffer = bytearray()
        usage_info = None
        request_id = None
        model_name = None
        processed_timestamp_ms = int(time.time() * 1000) # 처리 시작 시간 기록

        try:
            request_data = payload.model_dump(exclude_none=True)
            stream = await client.chat.completions.create(
                model=ChatService.MODEL_NAME,
                **request_data,
                stream=True,
                stream_options={"include_usage": True} # Usage 정보 요청
            )
            async for chunk in stream:
                if chunk.id: request_id = chunk.id
                if chunk.model: model_name = chunk.model
                if chunk.usage: usage_info = chunk.usage.model_dump() # 마지막 청크에서 캡처될 것으로 예상

                content = chunk.choices[0].delta.content
                if content:
                    buffer.extend(content.encode('utf-8')) # 버퍼에 바이트 추가

                    # 버퍼 크기가 설정값 이상이면 처리 시도
                    while len(buffer) >= buffer_size:
                        # 버퍼 크기 근처에서 마지막 완전한 UTF-8 문자 경계를 찾음
                        split_index = buffer_size
                        while split_index > 0:
                            try:
                                # 현재 인덱스까지 바이트 시퀀스를 디코딩 시도
                                decoded_part = buffer[:split_index].decode('utf-8')
                                # 디코딩 성공! 여기가 마지막 문자 경계임
                                chunk_to_send = buffer[:split_index] # 성공한 부분까지 자름
                                buffer = buffer[split_index:] # 나머지는 버퍼에 유지
                                yield f"data: {decoded_part}\n\n" # 디코딩된 문자열 전송
                                break # 내부 while 루프 탈출 (다음 버퍼 처리로)
                            except UnicodeDecodeError:
                                # 디코딩 실패: 현재 인덱스가 문자 중간임을 의미
                                # 인덱스를 하나 줄여서 다시 시도
                                split_index -= 1
                        else:
                            # while 루프가 break 없이 종료된 경우 (거의 발생하지 않음)
                            # 예외 처리: 버퍼에 유효한 UTF-8 시작점이 없거나 buffer_size가 너무 작을 때
                            # 여기서는 경고를 출력하고 문제가 될 수 있는 버퍼를 비움 (데이터 유실 가능)
                            print(f"경고: 버퍼에서 유효한 UTF-8 경계를 찾지 못했습니다 (buffer_size={buffer_size}). 버퍼를 비웁니다.")
                            buffer.clear()
                            break # 외부 while 루프도 탈출 (다음 청크 기다림)

            # 스트림 종료 후 버퍼에 남은 데이터가 있으면 모두 전송
            if buffer:
                try:
                    # 남은 데이터는 완전한 문자(열)이어야 함
                    yield f"data: {buffer.decode('utf-8')}\n\n"
                except UnicodeDecodeError as e:
                    # 이 단계에서 오류가 발생하면 데이터 손상을 의미할 수 있음
                    print(f"경고: 최종 버퍼 플러시 중 디코딩 오류: {e}. 일부 데이터가 손상될 수 있습니다.")
                    # 문제가 있는 문자는 ''로 대체하여 전송
                    yield f"data: {buffer.decode('utf-8', errors='replace')}\n\n"

        except OpenAIError as e:
            print(f"OpenAI API Error during buffered streaming: {e}")
            error_sse = await generate_error_sse(e)
            yield error_sse
        except Exception as e:
            # 스트리밍 중 예상치 못한 오류 처리
            print(f"Unexpected error during buffered streaming: {e}")
            error_json = json.dumps({"error": {"message": "버퍼 스트리밍 중 서버 내부 오류 발생", "status_code": 500}})
            yield f"event: error\ndata: {error_json}\n\n"
        finally:
            # --- 스트림 종료 후 Kafka 메시지 전송 ---
            if usage_info and request_id and model_name and producer:
                try:
                    usage_message = {
                        "request_id": request_id,
                        "model": model_name,
                        "usage": usage_info,
                        "api_timestamp_ms": None,
                        "processed_timestamp_ms": processed_timestamp_ms,
                        "stream": True
                    }
                    message_bytes = json.dumps(usage_message).encode('utf-8')
                    print(f"--- [Service Buffered] Sending usage info to Kafka topic '{settings.kafka_usage_topic}' ---")
                    await producer.send(settings.kafka_usage_topic, message_bytes) # Fire-and-forget
                    print(f"--- [Service Buffered] Usage info sent (fire-and-forget) ---")
                except Exception as kafka_e:
                    print(f"!!! [Service Buffered] Failed to send usage info to Kafka: {kafka_e}")
                    import traceback
                    traceback.print_exc()