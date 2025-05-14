# llm/app/controllers/chat_controller.py
from typing import Dict, Any
from aiokafka import AIOKafkaProducer
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.responses import StreamingResponse
from openai import AsyncOpenAI, OpenAIError

# 의존성, 모델, 서비스 임포트
from app.core.openai_client import get_openai_client, handle_openai_error
from app.core.config import get_stream_buffer_size, get_settings, Settings
from app.core.kafka_producer import get_kafka_producer
from app.models.chat import ChatCompletionInput
from app.services.chat_service import ChatService

# 컨트롤러 라우터 생성
router = APIRouter(prefix="/llm", tags=["LLM Chat"])

@router.post(
    "/chat",
    summary="LLM 채팅 (Full Response + Usage Tracking)",
    description="OpenAI API를 호출하고 받은 전체 JSON 응답을 반환하며, 토큰 사용량을 Kafka로 전송합니다.",
    response_model=Dict[str, Any] # 응답 모델 변경 (OpenAI 응답 구조는 동적이므로 Dict 사용)
)
async def post_chat_completion(
    payload: ChatCompletionInput,
    client: AsyncOpenAI = Depends(get_openai_client),
    producer: AIOKafkaProducer = Depends(get_kafka_producer),
    settings: Settings = Depends(get_settings)
    # ChatService를 직접 주입할 수도 있지만, 여기서는 static method를 사용
) -> Dict[str, Any]:
    """컨트롤러: 요청 처리, 서비스 호출(Producer, Settings 전달), 전체 응답 반환"""
    try:
        # 서비스 계층 호출 (전체 OpenAI 응답 객체를 받음)
        full_result = await ChatService.create_completion(payload, client, producer, settings)
        # 추출 로직 없이 그대로 반환
        return full_result
    except OpenAIError as e:
        # 서비스에서 발생한 OpenAI 오류를 HTTP 예외로 변환
        raise handle_openai_error(e)
    # HTTPException 은 handle_openai_error 에서 발생시키므로 별도 처리는 불필요할 수 있음
    # except HTTPException:
    #      raise
    except Exception as e:
        # 기타 예상치 못한 오류 처리
        print(f"Unexpected error in /chat controller: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="서버 내부 오류가 발생했습니다.",
        )

@router.post(
    "/chat/stream",
    summary="LLM 채팅 (네이티브 스트림 + Usage Tracking))",
    description="OpenAI API 응답 스트림을 SSE로 직접 중계하고, 완료 후 토큰 사용량을 Kafka로 전송합니다."
)
async def post_chat_completion_stream(
    payload: ChatCompletionInput,
    client: AsyncOpenAI = Depends(get_openai_client),
    producer: AIOKafkaProducer = Depends(get_kafka_producer),
    settings: Settings = Depends(get_settings)
    # ChatService 주입 가능
) -> StreamingResponse:
    """컨트롤러: 요청 처리, 서비스 호출(Producer, Settings 전달), 스트리밍 응답 반환"""
    # 서비스의 제너레이터를 직접 StreamingResponse에 전달
    return StreamingResponse(
        ChatService.stream_completion(payload, client, producer, settings),
        media_type="text/event-stream"
    )

@router.post(
    "/chat/stream-buffer",
    summary="LLM 채팅 (버퍼링된 스트림 + Usage Tracking)",
    description="OpenAI 응답 스트림을 버퍼링하여 SSE 청크로 전송하고, 완료 후 토큰 사용량을 Kafka로 전송합니다."
)
async def post_chat_completion_stream_buffer(
    payload: ChatCompletionInput,
    client: AsyncOpenAI = Depends(get_openai_client),
    buffer_size: int = Depends(get_stream_buffer_size),
    producer: AIOKafkaProducer = Depends(get_kafka_producer),
    settings: Settings = Depends(get_settings)
    # ChatService 주입 가능
) -> StreamingResponse:
    """컨트롤러: 요청 처리, 서비스 호출(Producer, Settings 전달), 버퍼링 스트리밍 응답 반환"""
    if buffer_size <= 0:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Buffer size must be positive.")

    # 서비스의 버퍼링 제너레이터를 StreamingResponse에 전달
    return StreamingResponse(
        ChatService.stream_buffered_completion(payload, client, buffer_size, producer, settings),
        media_type="text/event-stream"
    )