from pydantic import BaseModel, Field
from langchain_openai import ChatOpenAI
from langchain.output_parsers import PydanticOutputParser
from langchain.prompts import ChatPromptTemplate
from app.core.config import OPENAI_API_KEY
from typing import Optional

# LLM 설정
llm = ChatOpenAI(
    model="gpt-4o-mini",
    temperature=0.2,
    openai_api_key=OPENAI_API_KEY,
)


# 응답 스키마 정의
class ScheduleSchema(BaseModel):
    is_schedule: bool = Field(..., description="문장이 일정 관련인지 여부")
    datetime: Optional[str] = Field(
        None, description="일정이 있다면 ISO8601 형식의 일시"
    )
    event: Optional[str] = Field(None, description="일정의 이벤트 설명")


# OutputParser 설정
parser = PydanticOutputParser(pydantic_object=ScheduleSchema)

# 프롬프트 정의 - 예시 포함
template = """
너는 일정 분석 도우미야.

아래 문장에서 일정 정보를 추출해서 JSON 형식으로 반환해야 해.
반드시 다음 형식의 JSON을 반환해야 해:
{format_instructions}

예시 1:
입력: "내일 오후 2시 회의"
출력: {{"is_schedule": true, "datetime": "2023-05-06T14:00:00", "event": "회의"}}

예시 2:
입력: "오늘 날씨 어때?"
출력: {{"is_schedule": false, "datetime": null, "event": null}}

분석할 문장: {text}
"""

prompt = ChatPromptTemplate.from_template(template)

# LangChain 체인 구성
chain = prompt | llm | parser


# 최종 유틸 함수
def extract_schedule(text: str) -> dict:
    try:
        # LangChain 체인을 통한 처리
        result = chain.invoke(
            {"text": text, "format_instructions": parser.get_format_instructions()}
        )

        # 결과를 딕셔너리로 변환
        return result.model_dump()
    except Exception as e:
        # 오류 처리
        return {"is_schedule": False, "error": f"처리 중 오류 발생: {str(e)}"}
