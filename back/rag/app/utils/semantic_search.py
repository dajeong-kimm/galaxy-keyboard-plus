import os
import re
from dotenv import load_dotenv
from typing import Literal
from app.utils.embedding import get_text_embedding
from pinecone import Pinecone
from openai import OpenAI
from app.utils.chat_vector_store import search_chat_history
import json

load_dotenv()

pc = Pinecone(api_key=os.getenv("PINECONE_API_KEY"))
index = pc.Index(os.getenv("PINECONE_INDEX_NAME"))
openai = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))


def determine_query_type(query: str) -> Literal["photo", "info", "ambiguous"]:
    """질문이 사진 관련인지 정보 관련인지 판별"""
    response = openai.chat.completions.create(
        model="gpt-4o-mini",
        messages=[
            {
                "role": "system",
                "content": (
                    "사용자의 질문이 사진(이미지)을 찾으려는 것인지, "
                    "정보(텍스트/일정)를 찾으려는 것인지, 혹은 애매한지를 판단해줘. "
                    "'photo', 'info', 'ambiguous' 중 하나로만 응답해."
                ),
            },
            {"role": "user", "content": query},
        ],
        max_tokens=10,
    )
    answer = response.choices[0].message.content.strip().lower()
    if answer not in {"photo", "info", "ambiguous"}:
        return "ambiguous"
    return answer


def search_similar_items(
    user_id: str, query: str, target: str, top_k: int = 5
) -> list[dict]:
    """Pinecone에서 해당 네임스페이스로 유사 항목 검색"""
    namespace = f"{user_id}_{target}"
    vector = get_text_embedding(query)

    response = index.query(
        vector=vector,
        namespace=namespace,
        top_k=top_k,
        include_metadata=True,
    )

    results = []
    for match in response["matches"]:
        full_text = match["metadata"].get("text", "")
        if ": " in full_text:
            image_id, description = full_text.split(": ", 1)
        else:
            image_id, description = "unknown", full_text  # fallback

        image_id = re.sub(r"\s*\([^)]*\)", "", image_id).strip()

        results.append(
            {
                "score": round(match["score"], 3),
                "id": image_id,
                "text": description,
            }
        )

    return results


def search_similar_items_enhanced(
    user_id: str, queries: list[str], target: str, top_k: int = 5
) -> list[dict]:
    """향상된 벡터 검색 - 여러 쿼리로 검색 후 병합"""
    namespace = f"{user_id}_{target}"

    all_results = {}
    for query in queries[:3]:  # 최대 3개 쿼리만 사용
        vector = get_text_embedding(query)

        response = index.query(
            vector=vector,
            namespace=namespace,
            top_k=top_k,
            include_metadata=True,
        )

        # 결과 병합 (중복 제거)
        for match in response["matches"]:
            match_id = match["id"]
            if (
                match_id not in all_results
                or match["score"] > all_results[match_id]["score"]
            ):
                all_results[match_id] = match

    # 점수 순으로 정렬
    sorted_matches = sorted(
        all_results.values(), key=lambda x: x["score"], reverse=True
    )

    results = []
    for match in sorted_matches[:top_k]:
        full_text = match["metadata"].get("text", "")
        if ": " in full_text:
            image_id, description = full_text.split(": ", 1)
        else:
            image_id, description = "unknown", full_text

        image_id = re.sub(r"\s*\([^)]*\)", "", image_id).strip()

        results.append(
            {
                "score": round(match["score"], 3),
                "id": image_id,
                "text": description,
            }
        )

    return results


def generate_answer_from_info(query: str, results: list[dict]) -> str:
    """유사한 정보 결과를 바탕으로 LLM이 답변 생성"""
    context = "\n".join([f"- {item['text']}" for item in results])
    prompt = f"""다음은 참고할 정보들입니다:

{context}

사용자 질문: "{query}"

이 정보를 기반으로 사용자 질문에 답변해줘."""

    response = openai.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": prompt}],
        max_tokens=300,
    )

    return response.choices[0].message.content.strip()


def generate_answer_with_context(
    user_id: str, query: str, info_results: list[dict]
) -> str:
    # chat history 불러오기
    history = search_chat_history(user_id, query, top_k=5)

    history_text = "\n".join([f"{h['role']}: {h['text']}" for h in history])
    info_text = "\n".join([f"- {item['text']}" for item in info_results])

    prompt = f"""아래는 이전 대화 기록입니다:
{history_text}

다음은 참고할 정보들입니다:
{info_text}

사용자 질문: "{query}"

이 모든 내용을 바탕으로 사용자 질문에 대해 정확하고 간결하게 답변해줘."""

    response = openai.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": prompt}],
        max_tokens=300,
    )

    return response.choices[0].message.content.strip()


def generate_combined_answer_with_context(
    user_id: str, query: str, info_results: list[dict], photo_results: list[dict]
) -> dict:
    """정보 + 이미지 설명을 바탕으로 종합 답변 생성"""

    # 대화 기록 가져오기
    history = search_chat_history(user_id, query, top_k=10)

    # 관련된 대화만 필터링
    filtered_history = filter_relevant_chat_history(query, history)

    # 프롬프트 구성 시 필터링된 대화만 사용
    history_text = ""
    if filtered_history:
        history_text = "아래는 관련된 이전 대화 기록입니다:\n"
        history_text += "\n".join(
            [f"{h['role']}: {h['text']}" for h in filtered_history]
        )
        history_text += "\n\n"

    info_text = "\n".join([f"- {item['text']}" for item in info_results])
    photo_text = "\n".join(
        [f"- {item['id']}: {item['text']}" for item in photo_results]
    )

    prompt = f"""{history_text}다음은 참고할 정보들입니다:
{info_text}

다음은 관련된 사진 설명입니다:
{photo_text}

사용자 질문: "{query}"

중요: 위 질문에만 집중해서 답변해주세요. 
현재 질문과 직접 관련된 내용만 답변에 포함시켜주세요."""

    response = openai.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": prompt}],
        max_tokens=400,
    )

    return {
        "answer": response.choices[0].message.content.strip(),
        "photo_results": photo_results,
        "info_results": info_results,
    }


def filter_relevant_items_with_llm(
    query: str, items: list[dict], item_type: str
) -> list[dict]:
    """
    LLM에게 항목 중 질문과 관련 있는 것만 추려달라고 요청
    item_type: "정보" 또는 "사진"
    """
    bullet_list = "\n".join([f"- {item['id']}: {item['text']}" for item in items])
    prompt = f"""
다음은 사용자의 질문입니다:
"{query}"

다음은 {item_type} 항목 리스트입니다:
{bullet_list}

이 중 질문과 직접 관련이 있는 항목만 골라줘. 
그 항목들의 ID만 리스트 형태로 반환해줘. (예: ["123", "456"])
    """.strip()

    response = openai.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": prompt}],
        max_tokens=100,
        temperature=0.3,
    )

    try:
        relevant_ids = json.loads(response.choices[0].message.content.strip())
        return [item for item in items if item["id"] in relevant_ids]
    except Exception:
        # 실패 시 전체 반환
        return items


def extract_personal_info_context(history: list[dict]) -> str:
    # 예시: 이름, 애완동물, 선호 등 추론
    notes_text = "\n".join([f"{h['role']}: {h['text']}" for h in history])

    prompt = f"""아래 대화에서 사용자의 개인화 정보(이름, 가족, 반려동물, 일정 등)를 요약해줘.
문장이 아닌 키워드 또는 간단한 문장으로 적어줘.

대화:
{notes_text}
"""
    response = openai.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": prompt}],
        max_tokens=100,
    )
    return response.choices[0].message.content.strip()


def enhance_query_with_personal_context(user_id: str, query: str) -> str:
    # 과거 대화 기반 개인 정보 요약
    history = search_chat_history(user_id, query, top_k=20)
    personal_context = extract_personal_info_context(history)

    if personal_context:
        return f"{query}\n\n(참고: {personal_context})"
    return query


def filter_relevant_chat_history(query: str, history: list[dict]) -> list[dict]:
    """현재 질문과 관련된 대화만 필터링"""
    if not history:
        return []

    history_text = "\n".join(
        [f"{i}: {h['role']}: {h['text']}" for i, h in enumerate(history)]
    )

    prompt = f"""
    현재 질문: "{query}"
    
    아래 대화 기록들 중 현재 질문과 직접적으로 관련이 있는 것만 골라줘:
    {history_text}
    
    관련된 대화의 인덱스만 반환해줘. (예: [0, 2, 3])
    연속적인 대화나 이전 맥락이 필요한 경우가 아니라면 빈 리스트 []를 반환해도 돼.
    """

    response = openai.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": prompt}],
        max_tokens=100,
        temperature=0.3,
    )

    try:
        relevant_indices = json.loads(response.choices[0].message.content.strip())
        return [history[i] for i in relevant_indices if i < len(history)]
    except Exception:
        # 실패 시 맥락이 필요한 키워드가 있는지만 확인
        context_keywords = ["이전에", "아까", "방금", "그때", "다시", "그거", "그것"]
        if any(keyword in query for keyword in context_keywords):
            return history[:3]  # 최근 3개만
        return []


def filter_relevant_items_with_context(
    original_query: str, expanded_query: str, items: list[dict], item_type: str
) -> list[dict]:
    """개선된 LLM 필터링 - 원본 질문과의 관련성 확인"""

    if len(items) <= 3:
        return items

    # 질문 의도 파악
    query_intent = determine_query_intent(original_query)

    if query_intent == "photo_search":
        # 사진 찾기 요청 - 더 관대한 필터링
        threshold = 0.6
    else:
        # 정보 요청 - 엄격한 필터링
        threshold = 0.8

    # 간단한 필터링
    items_text = "\n".join(
        [
            f"{i}: {item['id']} - {item['text'][:50]}"
            for i, item in enumerate(items[:10])
        ]
    )

    prompt = f"""
원본 질문: "{original_query}"
확장 검색어: "{expanded_query}"

검색 결과:
{items_text}

원본 질문과 직접 관련된 항목들의 인덱스를 반환해줘. (예: [0, 2, 3])
"""

    response = openai.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": prompt}],
        max_tokens=50,
        temperature=0.3,
    )

    try:
        indices = json.loads(response.choices[0].message.content.strip())
        return [items[i] for i in indices if i < len(items)]
    except:
        # 점수 기반 필터링
        if items and "score" in items[0]:
            top_score = items[0]["score"]
            filtered = [
                item for item in items if item["score"] >= top_score * threshold
            ]
            return filtered[:7]
        return items[:7]


def determine_query_intent(query: str) -> str:
    """질문 의도 파악"""
    photo_keywords = ["사진", "찾아", "보여", "이미지", "찾아줘", "있나", "있어"]
    info_keywords = ["알려", "설명", "무엇", "어떤", "언제", "어디", "누구"]

    query_lower = query.lower()

    photo_count = sum(1 for keyword in photo_keywords if keyword in query_lower)
    info_count = sum(1 for keyword in info_keywords if keyword in query_lower)

    if photo_count > info_count:
        return "photo_search"
    else:
        return "info_request"


def expand_query_with_synonyms(query: str) -> list[str]:
    """쿼리를 유사한 자연어로 확장"""
    prompt = f"""
질문: "{query}"

이 질문과 비슷한 의미의 표현들을 3-5개 만들어줘. 
예: "네일아트 사진" → ["매니큐어 바른 사진", "네일 디자인 사진", "손톱 사진", "네일아트"]

비슷한 표현들만 리스트로 반환해줘.
"""

    response = openai.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": prompt}],
        max_tokens=100,
        temperature=0.7,
    )

    try:
        # 응답에서 리스트 추출
        content = response.choices[0].message.content.strip()
        if content.startswith("[") and content.endswith("]"):
            expanded = json.loads(content)
        else:
            # 콤마로 구분된 텍스트 처리
            expanded = [term.strip() for term in content.split(",")]

        return [query] + expanded[:4]  # 원본 포함 최대 5개
    except:
        return [query]


def enhance_query_with_personal_context_v2(user_id: str, query: str) -> list[str]:
    """개선된 쿼리 향상 - 의미 기반 유사 질문 생성 + 맥락 반영"""

    # 1. 맥락이 필요한지 판단
    context_keywords = [
        "이전에",
        "아까",
        "방금",
        "그때",
        "다시",
        "그거",
        "그것",
        "저번에",
        "어제",
        "지난주에",
    ]
    needs_context = any(keyword in query for keyword in context_keywords)

    context_text = ""
    if needs_context:
        history = search_chat_history(user_id, query, top_k=5)
        if history:
            context_text = "\n".join([f"- {h['text']}" for h in history[:3]])

    # 2. LLM에게 유사 질문 생성 요청
    if context_text:
        context_block = f"\n이전에 나눈 대화:\n{context_text}"
    else:
        context_block = ""

    prompt = f"""
    다음 사용자 질문을 보고, 유사한 의미를 가진 질문 3~5개를 생성해줘.
    - 질문은 정보 검색에 유용하도록 명확하고 직관적이어야 해.
    - 각 질문은 실제 사용자가 검색할 법한 자연스러운 문장으로 구성해줘.
    - 출력은 리스트 형태로 해줘 (ex: ["...질문1...", "...질문2...", "...질문3..."])

    사용자 질문: "{query}"
    {context_block}
    """

    response = openai.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": prompt}],
        temperature=0.5,
        max_tokens=300,
    )

    output_text = response.choices[0].message.content.strip()

    try:
        # 안전하게 리스트로 파싱
        enhanced_queries = json.loads(output_text)
        if isinstance(enhanced_queries, list):
            return enhanced_queries
    except json.JSONDecodeError:
        pass

    # 실패한 경우 fallback
    return [query]


def generate_answer_by_intent(
    user_id: str,
    query: str,
    info_results: list[dict],
    photo_results: list[dict],
    query_intent: str,
) -> dict:
    """질문 의도에 따라 LLM을 통해 자연스러운 응답 생성"""

    # 1. 맥락 필요 여부 판단
    history_text = ""
    if needs_context(query):
        history = search_chat_history(user_id, query, top_k=5)
        if history:
            history_text = (
                "이전 대화 기록:\n"
                + "\n".join([f"{h['role']}: {h['text']}" for h in history])
                + "\n\n"
            )

    # 결과 통합
    combined_results = (photo_results or []) + (info_results or [])
    combined_text = []

    for item in combined_results:
        text = item.get("text", "").strip()
        if text:
            combined_text.append(f"- {text[:300]}")  # 너무 길면 자름

    if not combined_text:
        answer = f"'{query}'에 대한 관련 정보를 찾을 수 없었습니다."
    else:
        prompt_intro = (
            "다음은 질문과 관련된 사진 또는 정보 설명입니다:\n"
            if photo_results
            else "다음은 질문과 관련된 정보 설명입니다:\n"
        )

        prompt = f"""
당신은 사용자 질문에 대해 친절하고 정확하게 답변하는 어시스턴트입니다.

{history_text}
사용자 질문:
"{query}"

{prompt_intro}
{chr(10).join(combined_text[:7])}

이 내용을 바탕으로 질문에 대해 자연스럽고 정확하게 답변해 주세요.
사진이 있는 경우, 어떤 장면이 담겨 있는지 설명해 주세요.
중복되거나 불필요한 내용은 생략하고, 핵심만 요약해 주세요.
        """.strip()

        response = openai.chat.completions.create(
            model="gpt-4o-mini",
            messages=[{"role": "user", "content": prompt}],
            max_tokens=300,
            temperature=0.5,
        )

        answer = response.choices[0].message.content.strip()

    return {
        "answer": answer,
        "photo_results": photo_results[:5],
        "info_results": info_results[:5],
        "query_intent": query_intent,
    }


def needs_context(query: str) -> bool:
    response = openai.chat.completions.create(
        model="gpt-4o-mini",
        messages=[
            {
                "role": "system",
                "content": "이 질문이 과거 대화 내용이 없으면 이해하기 어려운지 판단해줘. 'yes' 또는 'no'로만 답해.",
            },
            {"role": "user", "content": query},
        ],
        max_tokens=1,
    )
    return "yes" in response.choices[0].message.content.lower()
