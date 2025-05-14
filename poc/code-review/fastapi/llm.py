import os
from openai import AsyncOpenAI
from dotenv import load_dotenv

load_dotenv()

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
if not OPENAI_API_KEY:
    raise EnvironmentError("❌ OPENAI_API_KEY 환경변수가 설정되어 있지 않습니다.")

client = AsyncOpenAI(api_key=OPENAI_API_KEY)

MAX_TOKENS_PER_CHUNK = 3000  # 토큰 단위 기준 (프롬프트 + 응답 포함)


async def generate_review_prompt_chunked(mr_desc, file_diff_text):
    # 총 문자열 길이 확인
    total_len = len(file_diff_text)
    print(f"💾 [OpenAI] 파일 diff 길이: {total_len} 문자")
    
    # diff가 너무 길면 메시지 분할 여부 결정
    if total_len > 6000:  # OpenAI API의 토큰 제한을 고려한 값
        print("🔄 [OpenAI] 길이 제한으로 메시지 분할")
        chunks = split_text_by_token(file_diff_text, max_chars=5500)
        responses = []

        for i, chunk in enumerate(chunks):
            print(f"🧠 [OpenAI] GPT 요청 {i+1}/{len(chunks)}...")
            prompt = f"""
[📄 MR 설명]
{mr_desc}

[🔧 파일 변경 사항 - Part {i+1}/{len(chunks)}]
{chunk}

이 변경사항을 기반으로 코드 품질, 보안, 성능, 리팩토링 측면에서 구체적으로 리뷰해주세요. 파일에 적합한 방식으로 리뷰해주세요.
"""
            res = await client.chat.completions.create(
                model="gpt-4o-mini",
                messages=[
                    {"role": "system", "content": "당신은 숙련된 코드 리뷰어입니다. 구체적이고 액션가능한 코드 리뷰를 제공합니다."},
                    {"role": "user", "content": prompt},
                ],
                temperature=0.7,
            )
            responses.append(res.choices[0].message.content.strip())
        
        return "\n\n".join(responses)
    else:
        print(f"🧠 [OpenAI] 단일 GPT 요청...")
        prompt = f"""
[📄 MR 설명]
{mr_desc}

[🔧 파일 변경 사항]
{file_diff_text}

이 변경사항을 기반으로 코드 품질, 보안, 성능, 리팩토링 측면에서 구체적으로 리뷰해주세요. 불필요한 내용은 제외하고 중요한 해결방안에 초점을 맞추세요.
"""
        res = await client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {"role": "system", "content": "당신은 숙련된 코드 리뷰어입니다. 구체적이고 액션가능한 코드 리뷰를 제공합니다."},
                {"role": "user", "content": prompt},
            ],
            temperature=0.7,
        )
        return res.choices[0].message.content.strip()


def split_text_by_token(text, max_chars=3500):
    # 단순히 길이 기준으로 문자열 분할
    chunks = []
    while text:
        chunks.append(text[:max_chars])
        text = text[max_chars:]
    return chunks
