import asyncio
import logging
import time
import json
import re
from typing import List, Dict, Optional
from app.utils.ai_utils import expand_info_query, generate_info_answer, openai_client
from app.utils.semantic_search import search_similar_items_enhanced_optimized
from app.utils.context_helpers import check_if_requires_context, get_chat_context, generate_contextualized_info_answer
from app.config.settings import MAX_CONTEXT_ITEMS
from concurrent.futures import ThreadPoolExecutor

logger = logging.getLogger(__name__)
executor = ThreadPoolExecutor(max_workers=10)


async def process_info_search(user_id: str, query: str, timings: Dict) -> Dict:
    """정보 검색 처리 - 이전 대화 기록 활용"""
    # 맥락 필요 여부 확인
    requires_context = check_if_requires_context(query)
    chat_history = []
    
    if requires_context:
        # 이전 대화 기록 가져오기
        chat_history, context_timings = await get_chat_context(user_id, query)
        timings.update(context_timings)
        logger.info(f"🔍 이전 대화 검색: {len(chat_history)}개 ({timings.get('context_retrieval', 0):.3f}초)")
    query_expand_start = time.time()
    expanded_queries = await expand_info_query(query)
    timings["query_expansion"] = time.time() - query_expand_start
    logger.info(f"🔍 확장된 쿼리: {expanded_queries}")

    # 벡터 검색으로 관련 정보 찾기
    vector_search_start = time.time()
    namespace = f"{user_id}_information"
    loop = asyncio.get_event_loop()

    logger.info(
        f"🔍 벡터 검색 시작 - namespace: {namespace}, queries: {expanded_queries}"
    )

    # 검색 로직
    context_info = await perform_vector_search(user_id, expanded_queries, query)

    timings["vector_search"] = time.time() - vector_search_start
    logger.info(
        f"📚 검색된 정보: {len(context_info)}개 ({timings['vector_search']:.3f}초)"
    )

    # 답변 생성
    answer_start = time.time()
    
    if chat_history:
        # 정보와 맥락을 모두 고려한 답변 생성
        answer, used_info_indices, used_chat = await generate_contextualized_info_answer(
            user_id, query, context_info, chat_history
        )
        logger.info(f"✍️ 맥락 기반 답변 생성 (사용된 정보: {len(used_info_indices)}개, 사용된 대화: {len(used_chat)}개)")
    else:
        # 정보만 고려한 답변 생성
        answer, used_info_indices = await generate_enhanced_info_answer(user_id, query, context_info)
        used_chat = []
        logger.info(f"✍️ 일반 답변 생성 (사용된 정보: {len(used_info_indices)}개)")

    timings["answer_generation"] = time.time() - answer_start
    logger.info(f"✍️ 답변 생성 완료 ({timings['answer_generation']:.3f}초)")
    
    # 응답에 실제로 사용된 정보 소스의 ID만 추출
    photo_ids = []
    if used_info_indices and context_info:
        for idx in used_info_indices:
            if idx < len(context_info):
                item_id = extract_id_from_item(context_info[idx])
                if item_id:
                    photo_ids.append(item_id)
    
    logger.info(f"🆔 답변에 사용된 ID: {photo_ids}")

    # 결과 구성
    return {
        "type": "info_search",
        "query": query,
        "answer": answer,
        "context_count": len(context_info),
        "chat_context_used": len(used_chat) > 0,  # 대화 맥락 사용 여부
        "photo_ids": photo_ids,
        "_timings": timings,
        "_debug": {
            "expanded_queries": expanded_queries,
            "namespace": namespace,
            "context_sample": (
                context_info[0].get("text", "")[:200] if context_info else None
            ),
            "chat_history_count": len(chat_history),
        },
    }


async def perform_vector_search(
    user_id: str, expanded_queries: List[str], original_query: str
) -> List[Dict]:
    """벡터 검색 수행"""
    context_info = []
    loop = asyncio.get_event_loop()

    try:
        # 1. 확장된 쿼리로 검색
        result1 = await loop.run_in_executor(
            executor,
            search_similar_items_enhanced_optimized,
            user_id,
            expanded_queries,
            "information",
            20,
        )
        context_info.extend(result1)
        logger.info(f"✅ 확장된 쿼리 결과: {len(result1)}개")

        # 2. 원본 쿼리로도 검색
        if len(context_info) < 5:
            result2 = await loop.run_in_executor(
                executor,
                search_similar_items_enhanced_optimized,
                user_id,
                [original_query],
                "information",
                10,
            )
            context_info.extend(result2)
            logger.info(f"✅ 원본 쿼리 결과: {len(result2)}개")

        # 중복 제거
        seen_texts = set()
        unique_results = []
        for item in context_info:
            text = item.get("text", "")
            if text and text not in seen_texts:
                seen_texts.add(text)
                unique_results.append(item)

        return unique_results[:MAX_CONTEXT_ITEMS]

    except Exception as e:
        logger.error(f"❌ 벡터 검색 실패: {str(e)}", exc_info=True)
        return []


async def generate_enhanced_info_answer(
    user_id: str, query: str, context_info: List[Dict]
) -> tuple[str, List[int]]:
    """개선된 정보 기반 답변 생성 - 사용된 컨텍스트 인덱스 반환 & 불충분한 정보에도 대응"""

    def sync_generate_enhanced_answer():
        # context 정보를 더 체계적으로 정리
        context_texts = []
        for i, item in enumerate(context_info[:5]):  # 상위 5개만 사용
            text = item.get("text", "").strip()
            if text:
                context_texts.append(f"{i+1}. {text}")

        context_text = "\n".join(context_texts)

        # 사용된 컨텍스트 인덱스를 추적하기 위한 프롬프트 추가
        if context_text:
            prompt = f"""
사용자의 질문에 대해 아래 제공된 정보를 활용하여 답변하세요.
정보가 부족하더라도 최대한 관련된 내용을 추출하여 자연스러운 답변을 구성하세요.

[제공된 정보]
{context_text}

[사용자 질문]
{query}

답변 작성 규칙:
1. 제공된 정보를 기반으로 정확하게 답변
2. 친근하고 자연스러운 대화체 사용
3. 정보가 부족한 경우에도 질문에 맞는 답변 제공
4. 알려진 정보만으로 답변하되, 의미있는 정보가 전혀 없는 경우 적절히 안내
5. 반드시 사용한 정보의 번호를 마지막에 목록으로 추가 (1, 3, 5처럼 숫자만 쓰고 각 숫자는 쉼표로 구분)

답변:
"""
        else:
            # 컨텍스트가 없는 경우
            prompt = f"""
사용자의 질문: "{query}"

질문에 대한 정확한 정보를 찾지 못했지만, 최대한 관련된 내용을 제공해보세요. 
정보가 부족하더라도 사용자의 질문에 유용한 답변을 제공하되, 추측임을 지하고 정확한 정보를 제공하는 방식으로 작성해주세요.

답변:
"""

        response = openai_client.chat.completions.create(
            model="gpt-4o-mini",  # 다른 모델로 변경 가능
            messages=[
                {
                    "role": "system",
                    "content": "너는 사용자의 개인 비서야. 제공된 정보를 활용해 정확하고 도움이 되는 답변을 해줘. 어떤 정보를 사용했는지 표시하라.",
                },
                {"role": "user", "content": prompt},
            ],
            temperature=0.7,
        )

        answer = response.choices[0].message.content.strip()

        # 사용된 컨텍스트 인덱스 추출
        used_indices = []
        if context_text:  # 컨텍스트가 있었을 때만 추출
            # 답변 끝부분에서 번호 목록 추출
            indices_pattern = r"\b([0-9]+(?:,\s*[0-9]+)*)\b"
            indices_matches = re.findall(indices_pattern, answer.split("\n")[-1])

            if indices_matches:
                # 마지막 변에서 받은 것이 리스트의 형태로 도출되면 그걸 사용
                last_match = indices_matches[-1]
                for idx_str in last_match.split(","):
                    try:
                        idx = int(idx_str.strip()) - 1  # 1-based -> 0-based
                        if 0 <= idx < len(context_info):
                            used_indices.append(idx)
                    except ValueError:
                        continue

            # 수처리된 마지막 행을 제거 (외부에서 보이지 않게)
            if used_indices and "\n" in answer:
                lines = answer.split("\n")
                if any(
                    all(c in "0123456789, " for c in line.strip())
                    for line in lines[-2:]
                ):
                    answer = "\n".join(lines[:-1]).strip()

        return answer, used_indices

    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(executor, sync_generate_enhanced_answer)


def extract_id_from_item(item: Dict) -> Optional[str]:
    """한 개의 검색 결과 항목에서 ID 추출"""
    # ID가 이미 있는 경우
    if "id" in item and item["id"] not in ["unknown", ""]:
        return item["id"]

    text = item.get("text", "")
    if not text:
        return None

    # 1. 단순 숫자로 시작하는 영수증 번호 패턴
    receipt_id_match = re.match(r"^(\d{5,10})", text.strip())
    if receipt_id_match:
        return receipt_id_match.group(1)

    # 2. "숫자: " 형태의 ID
    prefix_id_match = re.match(r"^(\d+):\s", text.strip())
    if prefix_id_match:
        return prefix_id_match.group(1)

    # 3. 첫 줄이 숫자로만 이루어진 경우
    first_line = text.strip().split("\n")[0].strip() if "\n" in text else ""
    if first_line and first_line.isdigit() and len(first_line) > 4:
        return first_line

    return None
