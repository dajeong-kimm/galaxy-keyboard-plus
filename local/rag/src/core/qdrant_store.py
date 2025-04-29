# # src/core/qdrant_store.py

# from langchain_community.vectorstores import Qdrant
# from qdrant_client import QdrantClient

# def get_qdrant_store(settings, embedder):

#     """
#     1) QdrantClient 인스턴스 생성(url, prefer_grpc)
#     2) Qdrant 래퍼에 client 전달
#     """
#     client = QdrantClient(
#         url=str(settings.qdrant_url),          # 프로토콜 포함된 URL 문자열
#         prefer_grpc=settings.qdrant_prefer_grpc,
#     )

#     return Qdrant(
#         client=client,
#         collection_name=settings.qdrant_collection,
#         embeddings=embedder,
#     )
