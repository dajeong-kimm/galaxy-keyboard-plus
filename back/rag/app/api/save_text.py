from fastapi import APIRouter
from pydantic import BaseModel
from app.utils.vector_store import save_text_to_pinecone

router = APIRouter()


class TextPayload(BaseModel):
    user_id: str
    text: str


@router.post("/save/photo-text")
def save_photo_text(payload: TextPayload):
    namespace = save_text_to_pinecone(payload.user_id, payload.text, "photo")
    return {"status": "saved", "namespace": namespace}


@router.post("/save/plain-text")
def save_plain_text(payload: TextPayload):
    namespace = save_text_to_pinecone(payload.user_id, payload.text, "text")
    return {"status": "saved", "namespace": namespace}
