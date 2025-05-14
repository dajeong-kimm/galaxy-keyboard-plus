# app/utils/image_captioner.py

import base64
import os
import io
from PIL import Image
from openai import OpenAI
from dotenv import load_dotenv

load_dotenv()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))


def resize_image(image_bytes: bytes, max_size=(512, 512)) -> bytes:
    image = Image.open(io.BytesIO(image_bytes))

    # JPEG 저장을 위해 RGB로 변환
    if image.mode != "RGB":
        image = image.convert("RGB")

    image.thumbnail(max_size)

    output = io.BytesIO()
    try:
        image.save(output, format="JPEG")
    except Exception as e:
        raise RuntimeError(f"JPEG 저장 실패: {e}")
    return output.getvalue()


def generate_image_caption(image_bytes: bytes) -> str:
    # 비용 절감을 위해 해상도 축소
    resized_bytes = resize_image(image_bytes)

    # base64 인코딩
    encoded_image = base64.b64encode(resized_bytes).decode("utf-8")

    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": (
                            "이 이미지를 자세히 설명해줘. 사진 속에 보이는 사물, 인물, 동물, 배경, 위치, 색감, 분위기 등을 가능한 한 구체적으로 묘사해줘. "
                            "사진의 주요 구성 요소와 그 관계도 설명해줘. 불분명한 부분은 추정해서 말해줘도 돼."
                            "이 이미지에 자동차가 있다면, 반드시 자동차의 **번호판**에 적힌 글자를 최대한 정확하게 읽어줘. "
                            "번호판의 글자는 띄어쓰기 포함 여부와 상관없이 가능한 그대로 적어줘. "
                            "글자가 잘 안보이더라도 추정해서라도 적어줘. "
                        ),
                    },
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:image/jpeg;base64,{encoded_image}",
                        },
                    },
                ],
            }
        ],
    )

    return response.choices[0].message.content.strip()
