# src/core/clustering.py

from hdbscan import HDBSCAN
import numpy as np
from qdrant_client.http.models import Filter, FieldCondition, MatchValue
from src.config import settings

def cluster_session_topics(store, session_id: str, min_cluster_size: int = 5):
    """
    - store: Qdrant 래퍼 인스턴스
    - session_id: 클러스터링할 세션 UUID
    - min_cluster_size: HDBSCAN 최소 클러스터 크기
    """

    # 1) 세션 필터 생성
    scroll_filter = Filter(
        must=[
            FieldCondition(
                key="session_id",
                match=MatchValue(value=session_id)
            )
        ]
    )

    # 2) scroll 호출
    response = store.client.scroll(
        collection_name=settings.qdrant_collection,
        scroll_filter=scroll_filter,
        with_vectors=True,     # 벡터 가져오기
        with_payload=True,     # 메타데이터(페이로드) 가져오기
        limit=10000
    )

    # 3) 결과 리스트로 추출
    #   response: tuple[list[Record], Optional[PointId]]
    records, _ = response
    if not records:
        return

    # 4) 벡터 배열과 레코드 ID 추출
    vectors = np.stack([rec.vector for rec in records])
    ids     = [rec.id     for rec in records]

    # 5) HDBSCAN 클러스터링
    clusterer = HDBSCAN(min_cluster_size=min_cluster_size, metric="cosine")
    labels    = clusterer.fit_predict(vectors)

    # 6) topic_id 메타 업데이트
    updates = []
    for _id, label in zip(ids, labels):
        updates.append({
            "id": _id,
            "payload": {"topic_id": str(label)}
        })

    store.client.set_payload(
        collection_name=settings.qdrant_collection,
        payload=updates
    )
