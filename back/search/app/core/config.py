import os
from dotenv import load_dotenv

# 환경 변수 로드
load_dotenv()

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY_2")
if not OPENAI_API_KEY:
    raise ValueError("`OPENAI_API_KEY_2` 환경 변수가 설정되지 않았습니다.")
