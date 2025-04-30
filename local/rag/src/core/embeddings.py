from langchain_huggingface import HuggingFaceEmbeddings

def get_embedder(model_name: str) -> HuggingFaceEmbeddings:
    # CPU 모드 명시적 설정
    return HuggingFaceEmbeddings(
        model_name=model_name,
        model_kwargs={"device": "cpu"}
    )