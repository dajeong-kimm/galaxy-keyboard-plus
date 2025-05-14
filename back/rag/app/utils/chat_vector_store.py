from uuid import uuid4
from app.utils.embedding import get_text_embedding
from app.utils.vector_store import index  # 기존 index 객체 재사용


def save_chat_vector_to_pinecone(
    user_id: str,
    role: str,
    content: str,
    timestamp: int,
):
    """
    사용자 대화 기록을 Pinecone에 저장 (chat 전용)

    Args:
        user_id (str): 사용자 ID
        role (str): "user" or "assistant"
        content (str): 대화 내용
        timestamp (int): Unix timestamp
    """
    namespace = f"{user_id}_chat"
    vector = get_text_embedding(content)

    metadata = {
        "user_id": user_id,
        "role": role,
        "text": content,
        "timestamp": timestamp,
    }

    index.upsert(
        vectors=[
            {
                "id": f"{user_id}_{role}_{timestamp}",
                "values": vector,
                "metadata": metadata,
            }
        ],
        namespace=namespace,
    )


def search_chat_history(user_id: str, query: str, top_k: int = 5) -> list[dict]:
    namespace = f"{user_id}_chat"
    vector = get_text_embedding(query)

    response = index.query(
        vector=vector,
        namespace=namespace,
        top_k=top_k,
        include_metadata=True,
    )

    return [
        {
            "role": match["metadata"].get("role", "unknown"),
            "text": match["metadata"].get("text", ""),
            "timestamp": match["metadata"].get("timestamp", 0),
            "score": round(match["score"], 3),
        }
        for match in response["matches"]
    ]
