# app/api/text_extractor_api.py

from fastapi import APIRouter, UploadFile, File
from fastapi.responses import JSONResponse
from app.utils.image_text_extractor import extract_text_from_image

router = APIRouter()


@router.post("/extract-text")
async def extract_text(file: UploadFile = File(...)):
    try:
        contents = await file.read()
        text = extract_text_from_image(contents)
        return JSONResponse(content={"text": text})
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})
