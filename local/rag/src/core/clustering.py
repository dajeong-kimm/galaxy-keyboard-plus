# # src/core/clustering.py
# import numpy as np
# from hdbscan import HDBSCAN
# from src.config import settings


# def cluster_session_topics_chroma(store, session_id: str, min_cluster_size: int = 5):
#     """
#     store : langchain Chroma 래퍼
#     """
#     # 1) 세션 메타데이터로 문서 조회
#     docs = store.get(
#         where={"chat_id": session_id},
#         include=["embeddings", "metadatas", "ids"],
#     )
#     if not docs["ids"]:
#         return 0

#     # 2) 벡터 배열
#     vectors = np.vstack(docs["embeddings"])

#     # 3) HDBSCAN
#     labels = HDBSCAN(min_cluster_size=min_cluster_size, metric="euclidean").fit_predict(vectors)

#     # 4) topic_id 필드 업데이트
#     updates = []
#     for doc_id, label in zip(docs["ids"], labels):
#         updates.append(
#             {"id": doc_id, "metadata": {"topic_id": str(label)}}
#         )
#     store.update_documents(updates)

#     return len(set(labels))  # 생성된 클러스터 수
