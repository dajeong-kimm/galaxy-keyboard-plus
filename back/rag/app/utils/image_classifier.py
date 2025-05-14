# app/utils/image_classifier.py

from google.cloud import vision
from dotenv import load_dotenv
import os

# .env 파일에서 GOOGLE_APPLICATION_CREDENTIALS 변수 로드
load_dotenv()

# 환경 변수에서 키 경로를 가져와 Vision API 인증
key_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS")
if not key_path:
    raise RuntimeError(
        "환경 변수 GOOGLE_APPLICATION_CREDENTIALS가 설정되지 않았습니다."
    )

os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = (
    key_path  # 명시적으로 지정 (일부 환경에 필요)
)


def classify_image_from_bytes(image_bytes: bytes) -> float:
    client = vision.ImageAnnotatorClient()

    image = vision.Image(content=image_bytes)
    response = client.text_detection(image=image)

    if response.error.message:
        raise Exception(f"Google Vision API Error: {response.error.message}")

    annotations = response.text_annotations
    if not annotations:
        return 0.0  # 텍스트 없음

    full_text = annotations[0].description
    text_length = len(full_text.strip())

    # 텍스트 길이에 따라 0~1 점수 계산
    score = min(1.0, text_length / 1000.0)
    return round(score, 3)
