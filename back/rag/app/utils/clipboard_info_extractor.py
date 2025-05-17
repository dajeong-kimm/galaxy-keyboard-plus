import re


def extract_clipboard_items(text: str) -> list[dict]:
    """
    OCR 결과에서 클립보드로 복사할 만한 정보 추출
    예: 와이파이 PW, 계좌번호, 전화번호 등
    """
    results = []

    # 1. 와이파이 PW 추출
    wifi_pw_matches = re.findall(
        r"(?:PW|Password)[^\w]*[:\-]?[^\d]*(\d{6,})", text, flags=re.IGNORECASE
    )
    for pw in wifi_pw_matches:
        results.append({"type": "와이파이PW", "value": pw.strip()})

    # 2. 계좌번호 추출
    account_matches = re.findall(
        r"(?:[가-힣]{2,10}(?:은행|뱅크|저축은행)?)\s*[:\-]?\s*((?:\d{2,6}[-\s]?){2,4}\d{2,6})",
        text,
    )
    for number in account_matches:
        clean_number = re.sub(r"[\s\-]", "", number.strip())  # 하이픈/공백 제거
        if 9 <= len(clean_number) <= 14:
            results.append({"type": "계좌번호", "value": number.strip()})  # 원본 유지

    # 3. 전화번호 추출 (국번 + 중간 + 끝자리까지 완성)
    phone_matches = re.findall(
        r"\b(01[016789]|0[2-6][0-9]?|070)[-\s.]?(\d{3,4})[-\s.]?(\d{4})\b", text
    )
    for match in phone_matches:
        full_number = f"{match[0]}-{match[1]}-{match[2]}"
        results.append({"type": "전화번호", "value": full_number})

    return results
