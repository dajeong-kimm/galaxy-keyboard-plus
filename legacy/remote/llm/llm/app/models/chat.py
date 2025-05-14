# llm/app/models/chat.py
from typing import List, Optional, Dict, Any # Dict, Any 추가
from pydantic import BaseModel, Field

class ChatMessageInput(BaseModel):
    """OpenAI 채팅 메시지 형식"""
    role: str = Field(..., description="메시지 역할 (system, user, assistant 등)")
    content: str | None = Field(None, description="메시지 내용 (text content)") # content는 text 또는 tool call 응답 등일 수 있으므로 Optional
    # tool_calls, tool_call_id 등 다른 필드도 필요에 따라 추가 가능 (특히 assistant나 tool 역할 메시지 처리 시)

# --- Tool 관련 모델 정의 ---
class FunctionDefinition(BaseModel):
    """Tool의 함수 정의"""
    name: str
    description: Optional[str] = None
    parameters: Dict[str, Any] # JSON Schema 형태

class ToolDefinition(BaseModel):
    """Tool 정의"""
    type: str = Field("function", description="Tool type, currently only 'function' is supported")
    function: FunctionDefinition

class ToolChoiceFunction(BaseModel):
    """특정 함수를 tool_choice로 지정하기 위한 모델"""
    name: str

class ToolChoiceOption(BaseModel):
    """tool_choice 옵션 모델"""
    type: str = Field("function", description="Tool type, currently only 'function' is supported")
    function: ToolChoiceFunction

# --- 수정된 ChatCompletionInput 모델 ---
class ChatCompletionInput(BaseModel):
    """채팅 완료 요청의 입력 스키마 (확장됨)"""
    messages: List[ChatMessageInput] = Field(..., description="OpenAI 채팅 메시지 목록")

    # --- 주요 선택적 파라미터들 ---
    max_tokens: Optional[int] = Field(None, description="최대 생성 토큰 수 (OpenAI 기본값 사용)")
    temperature: Optional[float] = Field(None, ge=0.0, le=2.0, description="샘플링 온도 (OpenAI 기본값 사용)")
    tools: Optional[List[ToolDefinition]] = Field(None, description="사용 가능한 도구 목록")
    tool_choice: Optional[str | ToolChoiceOption] = Field(None, description="도구 사용 방식 제어 ('none', 'auto', 또는 특정 함수 지정)")
    # 필요에 따라 다른 파라미터 추가 가능 (예: top_p, stop, seed 등)
    # response_format: Optional[Dict[str, str]] = Field(None, description="응답 형식 지정 (예: {'type': 'json_object'})")

    model_config = {
        "json_schema_extra": {
            "examples": [
                { # 기본 예시
                    "messages": [
                        {"role": "system", "content": "You are a helpful assistant."},
                        {"role": "user", "content": "안녕? 니 소개를 해줘"}
                    ],
                    "max_tokens": 150,
                    "temperature": 0.7
                },
                { # Tool 사용 예시
                    "messages": [
                         {"role": "user", "content": "부산 날씨 어때?"}
                    ],
                    "tools": [
                        {
                            "type": "function",
                            "function": {
                                "name": "get_current_weather",
                                "description": "Get the current weather in a given location",
                                "parameters": {
                                    "type": "object",
                                    "properties": {
                                        "location": {"type": "string", "description": "The city and state, e.g. San Francisco, CA"},
                                        "unit": {"type": "string", "enum": ["celsius", "fahrenheit"]}
                                    },
                                    "required": ["location"]
                                }
                            }
                        }
                    ],
                    "tool_choice": "auto"
                }
            ]
        }
    }

# ChatSimpleResponse 모델은 삭제하거나 그대로 둘 수 있습니다 (더 이상 /llm/chat 기본 응답 모델은 아님).
# class ChatSimpleResponse(BaseModel):
#    """단순 텍스트 응답을 위한 스키마"""
#    response: str = Field(..., description="LLM이 생성한 응답 텍스트")