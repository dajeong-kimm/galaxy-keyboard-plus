import os
from uuid import uuid4
from dotenv import load_dotenv
from pinecone import Pinecone
from app.utils.embedding import get_text_embedding

# .env 파일 로드 (이미 있지만 확실히 하기 위해 유지)
load_dotenv()

# API 키 로드 로직 개선
pinecone_api_key = os.getenv("PINECONE_API_KEY") or os.getenv("PINECONE_KEY")
pinecone_index_name = os.getenv("PINECONE_INDEX_NAME")

# 환경 변수 디버깅 출력
print(f"DEBUG: PINECONE_API_KEY 존재: {'있음' if pinecone_api_key else '없음'}")
print(f"DEBUG: PINECONE_INDEX_NAME 존재: {'있음' if pinecone_index_name else '없음'}")

# 환경 변수가 없는 경우 대비
if not pinecone_api_key:
    raise ValueError("Pinecone API 키가 환경 변수에 설정되어 있지 않습니다.")

if not pinecone_index_name:
    raise ValueError("Pinecone 인덱스 이름이 환경 변수에 설정되어 있지 않습니다.")

# Pinecone 클라이언트 초기화
pc = Pinecone(api_key=pinecone_api_key)
index = pc.Index(pinecone_index_name)


def save_text_to_pinecone(user_id: str, text: str, target: str) -> str:
    """
    user_id와 target (photo/text)에 따라 지정된 네임스페이스로 텍스트 저장
    """
    namespace = f"{user_id}_{target}"
    vector = get_text_embedding(text)

    index.upsert(
        vectors=[
            {
                "id": str(uuid4()),
                "values": vector,
                "metadata": {"text": text},
            }
        ],
        namespace=namespace,
    )

    return namespace
