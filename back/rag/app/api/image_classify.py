from fastapi import APIRouter, UploadFile, File
from fastapi.responses import JSONResponse
from app.utils.image_classifier import classify_image_from_bytes

router = APIRouter()


@router.post("/classify-image")
async def classify_image(file: UploadFile = File(...)):
    try:
        contents = await file.read()
        score = classify_image_from_bytes(contents)
        return JSONResponse(content={"score": score})
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})
