import asyncio
import logging
import json
from concurrent.futures import ThreadPoolExecutor
from app.utils.chat_vector_store import save_chat_vector_to_pinecone

logger = logging.getLogger(__name__)
executor = ThreadPoolExecutor(max_workers=10)


async def save_query_async(user_id: str, role: str, content: str, timestamp: int):
    """쿼리 저장을 위한 비동기 래퍼"""
    try:
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(
            executor, save_chat_vector_to_pinecone, user_id, role, content, timestamp
        )
        logger.info(f"✅ 쿼리 저장 완료: {user_id}")
    except Exception as e:
        logger.error(f"❌ 쿼리 저장 실패: {user_id} - {str(e)}")


async def save_result_async(user_id: str, role: str, content: str, timestamp: int):
    """결과 저장을 위한 비동기 래퍼"""
    try:
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(
            executor, save_chat_vector_to_pinecone, user_id, role, content, timestamp
        )
        logger.info(f"✅ 결과 저장 완료: {user_id}")
    except Exception as e:
        logger.error(f"❌ 결과 저장 실패: {user_id} - {str(e)}")
