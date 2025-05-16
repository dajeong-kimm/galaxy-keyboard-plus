import re


def extract_clipboard_items(text: str) -> list[dict]:
    """
    OCR 결과에서 클립보드로 복사할 만한 정보 추출
    예: 와이파이 PW, 계좌번호 등
    """
    results = []

    # 와이파이 PW 추출
    # 예: PW : 1234567890 또는 Password: 12345678 등
    wifi_pw_matches = re.findall(
        r"(?:PW|Password)[^\w]*[:\-]?[^\d]*(\d{6,})", text, flags=re.IGNORECASE
    )
    for pw in wifi_pw_matches:
        results.append({"type": "와이파이PW", "value": pw.strip()})

    return results
