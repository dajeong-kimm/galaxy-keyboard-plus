import os
from fastapi import FastAPI, Request
from pydantic import BaseModel
from dotenv import load_dotenv
from review import handle_merge_request
from typing import Dict, Any

load_dotenv()
print("🔐 GITLAB_TOKEN:", os.getenv("GITLAB_TOKEN"))  # 👉 확인용
app = FastAPI()


class GitLabWebhookPayload(BaseModel):
    object_kind: str
    project: Dict[str, Any]
    object_attributes: Dict[str, Any]


@app.post("/gitlab/webhook")
async def gitlab_webhook(payload: GitLabWebhookPayload):
    event = payload.model_dump()
    print(f"🚀 [Webhook] Webhook 수신: object_kind={event.get('object_kind')}")
    if event.get("object_kind") == "merge_request":
        action = event.get("object_attributes", {}).get("action")
        print(f"🔍 [Webhook] MR Action: {action}")
        if action == "open":  # MR이 처음 열렸을 때만 리뷰 실행
            await handle_merge_request(event)
    return {"status": "ok"}
