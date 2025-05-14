# app/utils/embedding.py

import os
from dotenv import load_dotenv
from openai import OpenAI

load_dotenv()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY_2"))


def get_text_embedding(text: str) -> list[float]:
    if not text.strip():
        raise ValueError("입력이 비어 있습니다.")

    response = client.embeddings.create(
        model="text-embedding-3-small", input=[text]  # input은 리스트로 전달해야 함
    )
    return response.data[0].embedding
