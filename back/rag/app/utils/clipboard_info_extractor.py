import re


def extract_clipboard_items(text: str) -> list[dict]:
    """
    OCR 결과에서 클립보드로 복사할 만한 정보 추출
    예: 와이파이 PW, 계좌번호 등
    """
    results = []

    # 1. 와이파이 PW 추출
    wifi_pw_matches = re.findall(
        r"(?:PW|Password)[^\w]*[:\-]?[^\d]*(\d{6,})", text, flags=re.IGNORECASE
    )
    for pw in wifi_pw_matches:
        results.append({"type": "와이파이PW", "value": pw.strip()})

    # 2. 계좌번호 추출 (은행명 제거하고 계좌번호만 value로)
    account_matches = re.findall(
        r"(?:[가-힣]{2,10}(?:은행|뱅크|저축은행)?)\s*[:\-]?\s*((?:\d{2,6}[-\s]?){2,4}\d{2,6})",
        text,
    )
    for number in account_matches:
        clean_number = re.sub(r"\s+", "", number.strip())  # 공백 제거, 하이픈은 유지
        results.append({"type": "계좌번호", "value": clean_number})

    return results
