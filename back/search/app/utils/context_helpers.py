import asyncio
import re
from typing import List, Dict, Tuple, Optional
from app.utils.ai_utils import openai_client
from app.utils.chat_vector_store import search_chat_history

# 공통 실행기 재사용
from app.utils.ai_utils import executor


def check_if_requires_context(query: str) -> bool:
    """질문이 맥락이 필요한지 판단"""
    # 사용자 정보나 이전 대화를 참조하는 키워드
    context_keywords = [
        # 이전 대화 참조
        "이전", "아까", "방금", "그때", "다시", "그거", "그것", "저번에", "어제", "지난", "전에",
        # 개인 선호도 참조
        "좋아하", "좋아해", "좋아", "좋은", "선호하", "선호도", "좋아하는", "우리", "우리는", "우리가", "우리의",
        # 소유를 나타내는 표현
        "내", "나의", "나는", "내가", "내가 좋아하는", "내 좋아하는", "내가 선호하는", "내 선호하는",
        # 관심사/취미 관련
        "취미", "취미는", "취미가", "관심사", "관심", "관심있", "여가", "여가는", "여가가",
        # 음식/식품 관련
        "음식", "음식은", "음식을", "음식이", "음식은", "먹을", "먹는", "즐겨먹", "즐겨",
    ]
    
    # 사용자 정보나 이전 대화에 대한 질문 여부
    query_lower = query.lower()
    return any(keyword in query_lower for keyword in context_keywords)


async def get_chat_context(user_id: str, query: str) -> Tuple[List[Dict], Dict]:
    """사용자의 이전 대화 기록에서 현재 질문과 관련된 맥락 추출"""
    timings = {}
    
    # 맥락 필요 여부 확인
    context_needed = check_if_requires_context(query)
    if not context_needed:
        return [], timings
    
    # 이전 대화 검색
    context_start = asyncio.get_event_loop().time()
    chat_history = search_chat_history(user_id, query, top_k=5)
    
    # 시간 순으로 정렬
    chat_history.sort(key=lambda x: x.get("timestamp", 0))
    
    timings["context_retrieval"] = asyncio.get_event_loop().time() - context_start
    
    return chat_history, timings


async def generate_contextualized_conversation_response(user_id: str, query: str, chat_history: List[Dict]) -> Tuple[str, List[Dict]]:
    """맥락을 고려한 대화형 응답 생성"""

    def sync_generate_contextualized_response():
        # 이전 대화 기록 포맷팅
        formatted_history = []
        for i, chat in enumerate(chat_history):
            role = chat.get("role", "unknown")
            text = chat.get("text", "")
            if text:
                formatted_history.append(f"{i+1}. [{role}] {text}")
        
        context_text = "\n".join(formatted_history)
        
        # 응답 생성을 위한 프롬프트
        prompt = f"""
사용자의 질문에 대해 이전 대화 기록을 고려하여 답변하세요.

[이전 대화 기록]
{context_text}

[사용자 질문]
{query}

답변 작성 규칙:
1. 이전 대화에서 특히 유의할 내용이 없는 경우 일반적인 답변 제공
2. 사용자의 이전 발언에 기반하여 개인화된 응답 제공 
3. 자연스럽고 친근한 대화체 사용
4. 특히 개인 선호도나 이전 언급에 언급되었던 특정 정보를 포함하여 맞춤형 응답 제공
5. 사용한 이전 대화 번호를 마지막에 목록으로 추가 (예: "1, 3, 5")

답변:
"""

        response = openai_client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {
                    "role": "system",
                    "content": "너는 사용자의 개인 비서야. 이전 대화를 기반으로 맞춤형 답변을 제공해줄. 어떤 정보를 사용했는지 표시하라.",
                },
                {"role": "user", "content": prompt},
            ],
            temperature=0.7,
        )

        answer = response.choices[0].message.content.strip()
        
        # 사용된 채팅 기록 인덱스 추출
        used_history = []
        indices_pattern = r'\b([0-9]+(?:,\s*[0-9]+)*)\b'
        indices_matches = re.findall(indices_pattern, answer.split('\n')[-1])
        
        if indices_matches:
            # 마지막 일치하는 것을 인덱스 목록으로 간주
            last_match = indices_matches[-1]
            for idx_str in last_match.split(','):
                try:
                    idx = int(idx_str.strip()) - 1  # 1-based -> 0-based
                    if 0 <= idx < len(chat_history):
                        used_history.append(chat_history[idx])
                except ValueError:
                    continue
        
        # 수처리된 마지막 행을 제거 (외부에서 보이지 않게)
        if used_history and '\n' in answer:
            lines = answer.split('\n')
            if any(all(c in '0123456789, ' for c in line.strip()) for line in lines[-2:]):
                answer = '\n'.join(lines[:-1]).strip()
        
        return answer, used_history

    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(executor, sync_generate_contextualized_response)


async def generate_contextualized_info_answer(
    user_id: str, query: str, context_info: List[Dict], chat_history: List[Dict]
) -> Tuple[str, List[int], List[Dict]]:
    """맥락과 정보를 모두 고려한 답변 생성"""

    def sync_generate_contextualized_info():
        # 정보 컨텍스트 포맷팅
        info_texts = []
        for i, item in enumerate(context_info[:5]):  # 상위 5개 정보만 사용
            text = item.get("text", "").strip()
            if text:
                info_texts.append(f"정보{i+1}: {text}")
        
        # 대화 컨텍스트 포맷팅
        chat_texts = []
        for i, chat in enumerate(chat_history[:3]):  # 상위 3개 대화만 사용
            role = chat.get("role", "unknown")
            text = chat.get("text", "")
            if text:
                chat_texts.append(f"대화{i+1}: [{role}] {text}")
        
        info_context = "\n\n".join(info_texts)
        chat_context = "\n".join(chat_texts)
        
        # 프롬프트 구성
        prompt = f"""
사용자의 질문에 대해 제공된 정보와 이전 대화 맥락을 모두 고려하여 답변하세요.

[검색된 정보]
{info_context}

[이전 대화 맥락]
{chat_context}

[사용자 질문]
{query}

답변 작성 규칙:
1. 제공된 정보를 우선적으로 활용하여 정확한 답변 제공
2. 이전 대화 맥락을 고려하여 개인화된 응답 추가
3. 친근하고 자연스러운 대화체 사용
4. 정보가 부족하더라도 최대한 맥락을 활용해 유용한 답변 제공
5. 마지막 줄에 사용한 '정보'와 '대화' 번호를 명시 (예: "정보1, 정보3, 대화2")

답변:
"""
        
        response = openai_client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {
                    "role": "system",
                    "content": "너는 사용자의 개인 비서야. 제공된 정보와 맥락을 활용해 정확하고 개인화된 답변을 제공해줘.",
                },
                {"role": "user", "content": prompt},
            ],
            temperature=0.7,
        )
        
        answer = response.choices[0].message.content.strip()
        
        # 사용된 정보와 대화 인덱스 추출
        used_info_indices = []
        used_chat_indices = []
        
        # 정보 인덱스 추출
        info_pattern = r'정보(\d+)'
        info_matches = re.findall(info_pattern, answer.split('\n')[-1])
        for idx_str in info_matches:
            try:
                idx = int(idx_str) - 1  # 1-based -> 0-based
                if 0 <= idx < len(context_info):
                    used_info_indices.append(idx)
            except ValueError:
                continue
        
        # 대화 인덱스 추출
        chat_pattern = r'대화(\d+)'
        chat_matches = re.findall(chat_pattern, answer.split('\n')[-1])
        for idx_str in chat_matches:
            try:
                idx = int(idx_str) - 1  # 1-based -> 0-based
                if 0 <= idx < len(chat_history):
                    used_chat_indices.append(chat_history[idx])
            except ValueError:
                continue
        
        # 마지막 행 제거
        if (used_info_indices or used_chat_indices) and '\n' in answer:
            lines = answer.split('\n')
            answer = '\n'.join(lines[:-1]).strip()
        
        return answer, used_info_indices, used_chat_indices
    
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(executor, sync_generate_contextualized_info)
