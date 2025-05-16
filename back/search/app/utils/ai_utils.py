import asyncio
import json
from typing import List, Dict, Optional
import logging
from openai import OpenAI
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
from app.config.settings import (
    OPENAI_API_KEY,
    INTENT_MODEL,
    KEYWORD_EXTRACTION_MODEL,
    QUERY_EXPANSION_MODEL,
    ANSWER_GENERATION_MODEL,
)

logger = logging.getLogger(__name__)

# OpenAI 클라이언트
openai_client = OpenAI(api_key=OPENAI_API_KEY)

# ThreadPoolExecutor
executor = ThreadPoolExecutor(max_workers=10)


async def determine_image_query_intent(query: str) -> str:
    """이미지 관련 질문의 의도를 파악"""

    def sync_determine_intent():
        prompt = f"""
사용자의 질문이 다음 중 어느 의도에 해당하는지 분석하세요:
- "find_photo": 사용자가 사진을 찾고자 함
- "get_info": 사용자가 텍스트 정보나 설명을 원함

판단 기준:
- 질문에 "사진", "이미지", "찍은", "보여줘" 등의 단어가 포함되어 있으면 find_photo
- 질문이 단어 하나뿐인 경우에도 그 단어가 장소, 인물, 사물 등 일반적으로 사진에 등장할 수 있는 키워드이면 find_photo
- 그렇지 않으면 get_info

예시:
- "헬로키티" → find_photo
- "헬로키티 사진" → find_photo
- "헬로키티는 누구야?" → get_info

질문: {query}

응답은 반드시 "find_photo" 또는 "get_info" 중 하나만 주세요.
"""

        response = openai_client.chat.completions.create(
            model=INTENT_MODEL,
            messages=[
                {
                    "role": "system",
                    "content": "너는 의도 분류 전문가야. 정확하게 분류해줘.",
                },
                {"role": "user", "content": prompt},
            ],
            temperature=0,
        )

        return response.choices[0].message.content.strip().lower()

    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(executor, sync_determine_intent)


async def extract_photo_keywords(query: str) -> List[str]:
    """사진 검색을 위한 키워드 추출 - 직접 관련 키워드와 날짜 표현 처리"""

    def sync_extract_keywords():
        # 1. 키워드 추출 프롬프트
        prompt = f"""
다음 질문에서 사진을 찾기 위한 직접 관련 키워드를 추출하세요.
질문과 직접 관련된 핵심 단어와 유사어만 추출하고, 관련성이 낮은 확장 키워드는 제외합니다.
날짜/시간 표현은 제외하고 다른 키워드만 추출하세요.

질문: {query}

추출 규칙:
1. 질문과 직접 관련된 주요 명사와 형용사만 추출
2. 핵심 개념의 유사어와 관련어 포함 (최대 2-3개)
3. 간접적으로 연관된 확장 키워드는 제외
4. 동사는 관련 명사로만 변환 (예: "먹다" → "식사")
5. 날짜/시간 표현은 별도로 처리되므로 제외

예시:
"생일 파티 사진" → ["생일", "파티", "축하"]
"어제 회사에서 찍은 사진" → ["회사", "사무실", "직장"]
"지난주 해변에서 찍은 사진" → ["해변", "바다", "바닷가"]

JSON 배열로만 반환하세요.
"""

        keyword_response = openai_client.chat.completions.create(
            model=KEYWORD_EXTRACTION_MODEL,
            messages=[
                {
                    "role": "system",
                    "content": "키워드 추출 전문가. 사진 검색에 직접 관련된 핵심 키워드만 추출. JSON 배열만 반환.",
                },
                {"role": "user", "content": prompt},
            ],
            temperature=0.2,
        )

        keywords_raw = keyword_response.choices[0].message.content

        # 코드블록 제거
        if "```" in keywords_raw:
            keywords_raw = (
                keywords_raw.replace("```json", "").replace("```", "").strip()
            )

        try:
            keywords = json.loads(keywords_raw)
        except json.JSONDecodeError:
            # JSON 파싱 실패시 간단한 처리
            keywords = [
                kw.strip() for kw in keywords_raw.strip("[]").split(",") if kw.strip()
            ]

        # 2. 시간 표현이 있는지 확인하고 날짜 추출
        time_words = [
            "어제",
            "오늘",
            "내일",
            "그저께",
            "모레",
            "지난주",
            "이번주",
            "다음주",
            "지난달",
            "이번달",
            "다음달",
            "작년",
            "올해",
            "내년",
            "전날",
            "다음날",
        ]

        has_time_expression = any(word in query for word in time_words)

        date_keywords = []
        if has_time_expression:
            # 현재 날짜 가져오기
            current_date = datetime.now()

            # 날짜 추출 프롬프트
            date_prompt = f"""
다음 질문의 시간 표현을 오늘 날짜({current_date.strftime('%Y년 %m월 %d일')})를 기준으로 
정확한 날짜(YYYY년 MM월 DD일)로 변환하세요.

질문: {query}

반환 규칙:
1. 날짜만 추출하여 "YYYY년 MM월 DD일" 형식으로 반환
2. 날짜 범위가 있으면 시작일과 종료일을 "YYYY년 MM월 DD일~YYYY년 MM월 DD일" 형식으로 반환
3. 날짜 정보가 없으면 빈 배열([])을 반환

예시:
- "어제 찍은 사진" → (오늘이 2025년 05월 16일일 경우) ["2025년 05월 15일"]
- "지난주 여행" → ["2025년 05월 05일~2025년 05월 11일"]
- "이번달 초에 찍은 사진" → ["2025년 05월 01일~2025년 05월 05일"]
- "작년 크리스마스" → ["2024년 12월 25일"]

JSON 배열로만 반환하세요.
"""

            date_response = openai_client.chat.completions.create(
                model=KEYWORD_EXTRACTION_MODEL,
                messages=[
                    {
                        "role": "system",
                        "content": "날짜 추출 전문가. 질문에서 언급된 시간 표현을 정확한 날짜로 변환. JSON 배열만 반환.",
                    },
                    {"role": "user", "content": date_prompt},
                ],
                temperature=0.1,
            )

            date_raw = date_response.choices[0].message.content

            # 코드블록 제거
            if "```" in date_raw:
                date_raw = date_raw.replace("```json", "").replace("```", "").strip()

            try:
                date_data = json.loads(date_raw)
                date_keywords.extend(date_data)
            except json.JSONDecodeError:
                # 파싱 실패시 날짜에 대한 간단한 처리
                if "어제" in query:
                    yesterday = current_date - timedelta(days=1)
                    date_keywords.append(yesterday.strftime("%Y년 %m월 %d일"))
                elif "오늘" in query:
                    date_keywords.append(current_date.strftime("%Y년 %m월 %d일"))
                elif "내일" in query:
                    tomorrow = current_date + timedelta(days=1)
                    date_keywords.append(tomorrow.strftime("%Y년 %m월 %d일"))

        # 3. 키워드와 날짜 합치기
        final_keywords = keywords + date_keywords

        # 중복 제거
        return list(set(final_keywords))

    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(executor, sync_extract_keywords)


async def expand_info_query(query: str) -> List[str]:
    """정보 검색을 위한 쿼리 확장"""

    def sync_expand_query():
        prompt = f"""
다음 질문과 관련된 다양한 검색 쿼리를 생성하세요.
원본 질문의 의미를 유지하면서 동의어, 관련어, 다양한 표현을 사용합니다.

원본 질문: {query}

변형 규칙:
1. 핵심 키워드 포함
2. 동의어 사용
3. 약어와 전체 표현
4. 한국어와 영어 혼용
5. 관련 용어 추가

예시:
"스타벅스 와이파이 알려줘" → 
["스타벅스 WiFi", "스타벅스 와이파이", "starbucks wifi password", "스타벅스 인터넷", "스타벅스 무선인터넷", "스타벅스 비밀번호", "KT WiFi zone", "스타벅스 네트워크"]

JSON 배열로 5-7개의 변형 쿼리를 반환하세요.
"""

        response = openai_client.chat.completions.create(
            model=QUERY_EXPANSION_MODEL,
            messages=[
                {"role": "system", "content": "쿼리 확장 전문가. JSON 배열만 반환."},
                {"role": "user", "content": prompt},
            ],
            temperature=0.3,
        )

        queries_raw = response.choices[0].message.content

        # 코드블록 제거
        if "```" in queries_raw:
            queries_raw = queries_raw.replace("```json", "").replace("```", "").strip()

        try:
            expanded = json.loads(queries_raw)
            expanded.append(query)  # 원본 쿼리 포함
            return list(set(expanded))[:8]
        except:
            return [query]

    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(executor, sync_expand_query)


async def generate_info_answer(
    user_id: str, query: str, context_info: List[Dict]
) -> str:
    """정보 기반 질문에 대한 답변 생성"""

    def sync_generate_answer():
        # context 정보를 더 체계적으로 정리
        context_texts = []
        for i, item in enumerate(context_info[:5]):  # 상위 5개만 사용
            text = item.get("text", "").strip()
            if text:
                context_texts.append(f"{i+1}. {text}")

        context_text = "\n".join(context_texts)

        if not context_text:
            return "관련 정보를 찾을 수 없습니다. 다른 질문을 해보세요."

        prompt = f"""
사용자의 질문에 대해 아래 제공된 정보를 활용하여 답변하세요.
정보가 부족하다면 그 사실을 언급하고, 알려진 내용만으로 답변하세요.

[제공된 정보]
{context_text}

[사용자 질문]
{query}

답변 작성 규칙:
1. 제공된 정보를 기반으로 정확하게 답변
2. 친근하고 자연스러운 대화체 사용
3. 불확실한 내용은 추측하지 말고 명시
4. 필요시 추가 정보를 요청하는 안내 포함

답변:
"""

        response = openai_client.chat.completions.create(
            model=ANSWER_GENERATION_MODEL,
            messages=[
                {
                    "role": "system",
                    "content": "너는 사용자의 개인 비서야. 제공된 정보를 활용해 정확하고 도움이 되는 답변을 해줘.",
                },
                {"role": "user", "content": prompt},
            ],
            temperature=0.7,
        )

        return response.choices[0].message.content

    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(executor, sync_generate_answer)
