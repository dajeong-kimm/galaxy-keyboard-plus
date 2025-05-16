import httpx
import os

GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY")


async def reverse_geocode(lat: str, lon: str) -> str:
    """위도, 경도를 주소로 변환 (Google Geocoding API)"""
    url = (
        f"https://maps.googleapis.com/maps/api/geocode/json?"
        f"latlng={lat},{lon}&key={GOOGLE_API_KEY}&language=ko"
    )
    async with httpx.AsyncClient() as client:
        response = await client.get(url)
        data = response.json()
        if data["status"] == "OK" and data["results"]:
            return data["results"][0]["formatted_address"]
        return "주소 정보 없음"
