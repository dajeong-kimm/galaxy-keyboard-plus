# app/utils/image_text_extractor.py

import os
from dotenv import load_dotenv
from google.cloud import vision

# .env의 GOOGLE_APPLICATION_CREDENTIALS 경로 읽기
load_dotenv()
os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = os.getenv(
    "GOOGLE_APPLICATION_CREDENTIALS"
)

client = vision.ImageAnnotatorClient()


def extract_text_from_image(image_bytes: bytes) -> str:
    image = vision.Image(content=image_bytes)
    response = client.text_detection(image=image)

    if response.error.message:
        raise Exception(f"Vision API 오류: {response.error.message}")

    annotations = response.text_annotations
    if not annotations:
        return ""  # 텍스트 없음

    return annotations[0].description.strip()
