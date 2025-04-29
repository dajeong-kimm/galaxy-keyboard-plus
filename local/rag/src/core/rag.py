# src/core/rag.py
from typing import Optional
from langchain.chains import RetrievalQA
from langchain.prompts import PromptTemplate

QUESTION_PROMPT = PromptTemplate(
    input_variables=["context_str", "question"],
    template=(
        "당신은 지식이 풍부한 AI 비서입니다. 다음 컨텍스트를 참조하되, 단순히 복사하지 말고 자연스러운 언어로 질문에 답변하세요.\n\n"
        "컨텍스트:\n{context_str}\n\n"
        "질문: {question}\n\n"
        "지침:\n"
        "1. 컨텍스트의 정보를 이해하고 자신만의 말로 답변을 작성하세요.\n"
        "2. 컨텍스트에 없는 정보는 '컨텍스트에 관련 정보가 없습니다'라고 솔직히 답변하세요.\n"
        "3. 답변은 간결하고 명확하게 작성하되, 필요한 모든 정보를 포함하세요.\n"
        "4. 가능하면 정보를 구조화하여 읽기 쉽게 만드세요.\n"
        "5. 필요 시 MCP 도구 사용을 제안하세요.\n\n"
        "답변:"
    ),
)

REFINE_PROMPT = PromptTemplate(
    input_variables=["existing_answer", "context_str", "question"],
    template=(
        "이전 답변: {existing_answer}\n\n"
        "추가 컨텍스트:\n{context_str}\n\n"
        "질문: {question}\n\n"
        "지침:\n"
        "1. 추가 컨텍스트를 활용해 이전 답변을 보완하거나 정정하세요.\n"
        "2. 새로운 정보만 추가하고, 중복 내용은 제거하세요.\n"
        "3. 원본 컨텍스트를 그대로 복사하지 말고 자연스러운 언어로 통합하세요.\n"
        "4. 답변은 일관성 있게 유지하며 마치 처음부터 작성한 것처럼 자연스럽게 만드세요.\n"
        "5. 정보가 상충하는 경우 가장 신뢰할 수 있는 정보를 선택하고 이유를 간략히 설명하세요.\n\n"
        "수정된 답변:"
    ),
)

def get_rag_chain(
    llm,
    vectorstore,
    chat_id: str,
    k: int,
    topic_id: Optional[str] = None,
):
    metadata_filter = {"chat_id": chat_id}
    if topic_id:
        metadata_filter["topic_id"] = topic_id

    retriever = vectorstore.as_retriever(
        search_type="mmr",
        search_kwargs={
            "k": k,
            "filter": metadata_filter,
            "fetch_k": k * 2,
            "lambda_mult": 0.7
        }
    )

    return RetrievalQA.from_chain_type(
        llm=llm,
        chain_type="refine",
        retriever=retriever,
        chain_type_kwargs={
            "question_prompt": QUESTION_PROMPT,
            "refine_prompt": REFINE_PROMPT,
            "document_variable_name": "context_str",
        },
        return_source_documents=False,
    )
