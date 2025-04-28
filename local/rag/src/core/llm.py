# src/core/llm.py

from langchain.chat_models import ChatOpenAI
from src.config import settings

def get_llm() -> ChatOpenAI:
    """
    외부 OpenAI ChatCompletion 모델을 반환합니다.
    """
    return ChatOpenAI(
        model_name=settings.openai_model_name,
        openai_api_key=settings.openai_api_key,
        temperature=0.0,
        verbose=False,
    )
