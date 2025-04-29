# llm/app/services/chat_service.py
import json
from typing import AsyncGenerator, Dict, Any

from openai import AsyncOpenAI, OpenAIError

from app.models.chat import ChatCompletionInput
from app.core.openai_client import generate_error_sse # SSE 오류 생성 헬퍼 사용

class ChatService:
    """OpenAI 채팅 관련 비즈니스 로직을 처리하는 서비스 클래스"""

    MODEL_NAME = "gpt-4o-mini" # 사용할 모델 고정 또는 설정에서 가져오기

    @staticmethod
    async def create_completion(
        payload: ChatCompletionInput, client: AsyncOpenAI
    ) -> Dict[str, Any]:
        """OpenAI API를 호출하여 완전한 응답을 반환합니다 (stream=False)."""
        try:
            request_data = payload.model_dump(exclude_none=True)
            completion = await client.chat.completions.create(
                model=ChatService.MODEL_NAME,
                **request_data,
                stream=False
            )
            return completion.model_dump()
        except OpenAIError as e:
            # 서비스 레벨에서 오류를 잡아서 다시 발생시켜 컨트롤러에서 처리하도록 함
            # 또는 여기서 직접 HTTP 예외를 발생시킬 수도 있음 (설계에 따라 다름)
            print(f"OpenAI API Error in create_completion: {e}")
            raise e # 컨트롤러나 전역 핸들러에서 처리하도록 다시 발생

    @staticmethod
    async def stream_completion(
        payload: ChatCompletionInput, client: AsyncOpenAI
    ) -> AsyncGenerator[str, None]:
        """OpenAI API 응답 스트림을 SSE 형식으로 생성합니다 (stream=True)."""
        try:
            request_data = payload.model_dump(exclude_none=True)
            stream = await client.chat.completions.create(
                model=ChatService.MODEL_NAME,
                **request_data,
                stream=True
            )
            async for chunk in stream:
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

    @staticmethod
    async def stream_buffered_completion(
        payload: ChatCompletionInput, client: AsyncOpenAI, buffer_size: int
    ) -> AsyncGenerator[str, None]:
        """OpenAI 응답 스트림을 버퍼링하여 SSE 형식으로 생성합니다 (UTF-8 문자 경계 처리 개선)."""
        buffer = bytearray()
        try:
            request_data = payload.model_dump(exclude_none=True)
            stream = await client.chat.completions.create(
                model=ChatService.MODEL_NAME,
                **request_data,
                stream=True
            )
            async for chunk in stream:
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