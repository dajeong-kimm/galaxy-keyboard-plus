# src/tasks/cluster_topics.py

import sys
from src.core.qdrant_store import get_qdrant_store
from src.core.embeddings import get_embedder
from src.core.clustering import cluster_session_topics
from src.config import settings

def main(session_id: str):
    embedder = get_embedder(settings.embed_model)
    store    = get_qdrant_store(settings, embedder)
    cluster_session_topics(store, session_id, min_cluster_size=5)
    print(f"[clustered] session {session_id}")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python cluster_topics.py <session_id>")
        sys.exit(1)
    main(sys.argv[1])
