# src/core/rag.py
from typing import Optional
from langchain.chains import RetrievalQA
from langchain.prompts import PromptTemplate

# ───────── Prompt 템플릿 ─────────
# 기존 QUESTION_PROMPT 주석 처리
'''
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
'''

# 개선된 QUESTION_PROMPT
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

# 기존 REFINE_PROMPT 주석 처리
'''
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
'''

# 개선된 REFINE_PROMPT
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

# ───────── 체인 생성 함수 ─────────
# 기존 get_rag_chain 함수 주석 처리
'''
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
'''

# 개선된 get_rag_chain 함수
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
    # 메타데이터 필터 설정
    metadata_filter = {"chat_id": chat_id}
    if topic_id:
        metadata_filter["topic_id"] = topic_id

    # 검색 방식 개선 - MMR 방식 사용 (다양성 확보)
    retriever = vectorstore.as_retriever(
        search_type="mmr",  # Maximum Marginal Relevance 사용
        search_kwargs={
            "k": k, 
            "filter": metadata_filter,
            "fetch_k": k * 2,  # 더 많은 후보를 가져와서 다양성 확보
            "lambda_mult": 0.7  # 0.7은 관련성과 다양성의 균형을 의미 (0: 다양성만, 1: 관련성만)
        }
    )

    return RetrievalQA.from_chain_type(
        llm=llm,
        chain_type="refine",  # 기존과 동일하게 refine 방식 유지
        retriever=retriever,
        chain_type_kwargs={
            "question_prompt": QUESTION_PROMPT,
            "refine_prompt": REFINE_PROMPT,
            "document_variable_name": "context_str",
        },
        return_source_documents=False,
    )