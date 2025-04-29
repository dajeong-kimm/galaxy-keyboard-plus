# src/core/rag.py
from typing import Optional
from langchain.chains import RetrievalQA
from langchain.prompts import PromptTemplate

# ───────── Prompt 템플릿 ─────────
QUESTION_PROMPT = PromptTemplate(
    input_variables=["context_str", "question"],
    template=(
        "당신은 사용자의 로컬 데이터를 기반으로 도움을 제공하는 비서입니다.\n\n"
        "컨텍스트:\n{context_str}\n\n"
        "질문: {question}\n\n"
        "컨텍스트에 근거하여 간결하고 정확하게 답변하세요. "
        "관련 정보가 없으면 '컨텍스트에 관련 정보가 없습니다'라고 답하세요. "
        "필요 시 MCP 도구 사용을 제안하세요."
    ),
)

REFINE_PROMPT = PromptTemplate(
    input_variables=["existing_answer", "context_str", "question"],
    template=(
        "이전 답변: {existing_answer}\n\n"
        "추가 컨텍스트:\n{context_str}\n\n"
        "질문: {question}\n\n"
        "추가 컨텍스트를 활용해 답변을 보완하거나 정정하세요. "
        "중복은 제거하고 핵심 정보만 포함하세요."
    ),
)

# ───────── 체인 생성 함수 ─────────
def get_rag_chain(
    llm,
    vectorstore,
    chat_id: str,
    k: int,
    topic_id: Optional[str] = None,
):
    """
    • chat_id  : 각 채팅방 UUID (메타데이터)
    • topic_id : 선택적으로 세부 토픽 필터
    """
    # 여기를 session_id → chat_id로 변경했습니다
    metadata_filter = {"chat_id": chat_id}
    if topic_id:
        metadata_filter["topic_id"] = topic_id

    # filter는 search_kwargs 안에 넣어야 Chroma가 인식합니다
    retriever = vectorstore.as_retriever(
        search_kwargs={"k": k, "filter": metadata_filter}
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
