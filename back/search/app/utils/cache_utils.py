import hashlib
from datetime import datetime, timedelta
import logging
from typing import Dict, Optional

logger = logging.getLogger(__name__)

# 캐시 저장소
cache: Dict[str, Dict] = {}


def get_cache_key(user_id: str, query: str) -> str:
    """캐시 키 생성"""
    cache_data = f"image:{user_id}:{query}"
    return hashlib.md5(cache_data.encode()).hexdigest()


def get_from_cache(key: str, ttl_seconds: int) -> Optional[Dict]:
    """캐시에서 데이터 가져오기"""
    if key in cache:
        cached_data = cache[key]
        if datetime.now() < cached_data["expires_at"]:
            logger.info(f"✅ 캐시 히트: {key}")
            return cached_data["data"]
        else:
            del cache[key]
            logger.info(f"🗑️ 만료된 캐시 삭제: {key}")
    return None


def set_cache(key: str, data: Dict, ttl_seconds: int, max_cache_size: int):
    """캐시에 데이터 저장"""
    if len(cache) >= max_cache_size:
        oldest_key = min(cache.keys(), key=lambda k: cache[k]["created_at"])
        del cache[oldest_key]
        logger.info(f"🗑️ 캐시 크기 초과로 오래된 항목 제거: {oldest_key}")

    cache[key] = {
        "data": data,
        "created_at": datetime.now(),
        "expires_at": datetime.now() + timedelta(seconds=ttl_seconds),
    }
    logger.info(f"💾 캐시 저장: {key}")


def get_cache_status():
    """캐시 상태 반환"""
    current_time = datetime.now()
    valid_count = sum(1 for data in cache.values() if current_time < data["expires_at"])
    expired_count = len(cache) - valid_count

    return {
        "total_items": len(cache),
        "valid_items": valid_count,
        "expired_items": expired_count,
    }


def clear_cache() -> int:
    """캐시 초기화 및 삭제된 항목 수 반환"""
    cache_size = len(cache)
    cache.clear()
    logger.info(f"🗑️ 캐시 초기화: {cache_size}개 항목 삭제")
    return cache_size
