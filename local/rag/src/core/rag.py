# src/core/rag.py

from typing import Optional
from langchain.chains import RetrievalQA
from langchain.prompts import PromptTemplate

QUESTION_PROMPT = PromptTemplate(
    input_variables=["context_str", "question"],  # "context"에서 "context_str"로 변경
    template=(
        "당신은 사용자가 제공한 문서를 참고해 질문에 답하는 도우미입니다.\n\n"
        "문서 내용:\n"
        "{context_str}\n\n"  # {context}에서 {context_str}로 변경
        "질문: {question}\n\n"
        "위 문서에서 답을 찾을 수 있으면 문서의 내용을 바탕으로 "
        "구체적이고 자세히 답변하세요.\n"
        "문서에 해당 내용이 없으면 “죄송하지만, 문서에 답이 없습니다.”라고 솔직하게 말씀해주세요.\n"
    )
)

REFINE_PROMPT = PromptTemplate(
    # Refine 단계에서는 'context_str'라는 이름으로 문서를 넘깁니다.
    input_variables=["existing_answer", "context_str", "question"],
    template=(
        "기존 답변:\n"
        "{existing_answer}\n\n"
        "추가 문서 내용:\n"
        "{context_str}\n\n"
        "위 추가된 내용을 참고해, 질문에 대한 답변을 더 구체적이고 정확하게 개선해 주세요.\n"
        "질문: {question}\n"
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
