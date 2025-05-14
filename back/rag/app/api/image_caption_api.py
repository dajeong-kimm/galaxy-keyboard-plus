# app/api/image_caption_api.py

from fastapi import APIRouter, UploadFile, File
from fastapi.responses import JSONResponse
from app.utils.image_captioner import generate_image_caption

router = APIRouter()


@router.post("/describe-image")
async def describe_image(file: UploadFile = File(...)):
    try:
        contents = await file.read()
        caption = generate_image_caption(contents)
        return JSONResponse(content={"caption": caption})
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})
