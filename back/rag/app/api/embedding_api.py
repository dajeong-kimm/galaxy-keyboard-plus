# app/api/embedding_api.py

from fastapi import APIRouter
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from app.utils.embedding import get_text_embedding

router = APIRouter()


class TextRequest(BaseModel):
    text: str


@router.post("/embed")
async def embed_text(request: TextRequest):
    try:
        embedding = get_text_embedding(request.text)
        return JSONResponse(content={"embedding": embedding})
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})
