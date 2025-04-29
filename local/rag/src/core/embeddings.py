from langchain_huggingface import HuggingFaceEmbeddings
import os

# 환경 변수 설정으로 CUDA 비활성화 (이미 import 전에 설정되어야 함)
os.environ["CUDA_VISIBLE_DEVICES"] = ""
# 또는
os.environ["PYTORCH_CUDA_ALLOC_CONF"] = "max_split_size_mb:32"

def get_embedder(model_name: str) -> HuggingFaceEmbeddings:
    # CPU 모드 명시적 설정
    return HuggingFaceEmbeddings(
        model_name=model_name,
        model_kwargs={"device": "cpu"}
    )