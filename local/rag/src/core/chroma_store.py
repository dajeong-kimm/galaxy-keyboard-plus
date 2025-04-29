# src/core/chroma_store.py
from langchain_huggingface import HuggingFaceEmbeddings
from langchain.vectorstores import Chroma
from src.config import settings
import os

def get_chroma_store(embedder):

    os.makedirs(settings.chroma_persist_dir, exist_ok=True)

    return Chroma(
        persist_directory=settings.chroma_persist_dir,
        collection_name=settings.chroma_collection_name,
        embedding_function=embedder,
        client_settings={},      # 기본 설정
    )
