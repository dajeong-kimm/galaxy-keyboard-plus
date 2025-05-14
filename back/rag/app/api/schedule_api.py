# app/api/schedule_api.py

from fastapi import APIRouter
from pydantic import BaseModel
from fastapi.responses import JSONResponse
from app.utils.schedule_parser import extract_schedule

router = APIRouter()


class ScheduleRequest(BaseModel):
    text: str


@router.post("/parse-schedule")
async def parse_schedule_endpoint(request: ScheduleRequest):
    try:
        result = extract_schedule(request.text)
        # 이미 딕셔너리이므로 json.loads() 필요 없음
        return JSONResponse(content=result)
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})
