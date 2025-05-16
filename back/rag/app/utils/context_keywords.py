from datetime import datetime


def parse_address_keywords(address: str) -> list[str]:
    """
    주소 문자열을 기반으로 주요 지역 단어 키워드 추출
    숫자(번지/건물번호 등)는 제외
    예: "대한민국 부산광역시 강서구 송정동 1624" → ["대한민국", "부산광역시", "강서구", "송정동"]
    """
    if not address:
        return []

    keywords = []
    for word in address.split():
        word = word.strip()
        if not word:
            continue
        # 숫자로만 되어 있으면 제외
        if word.isdigit():
            continue
        keywords.append(word)

    return keywords


def parse_time_keywords(image_time_str: str) -> list[str]:
    """
    날짜 문자열을 기반으로 다양한 시간 키워드를 생성
    예: ['2025년', '05월', '15일', '2025년 05월', '2025년 05월 15일', '오후']
    """
    try:
        dt = datetime.strptime(image_time_str, "%Y:%m:%d %H:%M:%S")
    except ValueError:
        return []

    year = dt.strftime("%Y년")
    month = dt.strftime("%m월")
    day = dt.strftime("%d일")

    # 복합 표현
    year_month = f"{year} {month}"
    year_month_day = f"{year} {month} {day}"

    # 시간대 키워드
    hour = dt.hour
    if 5 <= hour < 12:
        time_of_day = "오전"
    elif 12 <= hour < 18:
        time_of_day = "오후"
    else:
        time_of_day = "저녁"

    return [year, month, day, year_month, year_month_day, time_of_day]
