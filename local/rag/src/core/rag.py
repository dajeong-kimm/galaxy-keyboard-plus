# src/core/rag.py

from typing import Optional
from langchain.chains import RetrievalQA
from langchain.prompts import PromptTemplate

QUESTION_PROMPT = PromptTemplate(
    input_variables=["context_str", "question"],
    template=(
        "당신은 사용자의 로컬 데이터를 기반으로 도움을 제공하는 비서입니다.\n\n"
        "컨텍스트: {context_str}\n\n"
        "질문: {question}\n\n"
        "컨텍스트의 정보를 토대로 간결하고 정확하게 답변하세요. 관련 정보가 없으면 '컨텍스트에 관련 정보가 없습니다'라고 답하세요. 필요시 MCP 도구를 제안하세요."
    )
)

REFINE_PROMPT = PromptTemplate(
    input_variables=["existing_answer", "context_str", "question"],
    template=(
        "이전 답변: {existing_answer}\n\n"
        "추가 컨텍스트: {context_str}\n\n"
        "질문: {question}\n\n"
        "추가 컨텍스트를 활용해 이전 답변을 보완하거나 정정하세요. 중요 정보만 추가하고 중복은 제거하세요."
    )
)

def get_rag_chain(
    llm,
    vectorstore,
    session_id: str,
    k: int,
    topic_id: Optional[str] = None
):
    # metadata 필터: session_id (+ topic_id)
    f = {"session_id": session_id}
    if topic_id is not None:
        f["topic_id"] = topic_id

    retriever = vectorstore.as_retriever(
        search_kwargs={"k": k},
        filter=f,
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