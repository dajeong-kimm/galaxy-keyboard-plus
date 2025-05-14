import logging
import httpx
import json
from datetime import datetime
from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from app.utils.image_classifier import classify_image_from_bytes
from app.utils.image_captioner import generate_image_caption
from app.utils.image_text_extractor import extract_text_from_image
from app.utils.vector_store import save_text_to_pinecone
from app.utils.schedule_parser import extract_schedule

logger = logging.getLogger(__name__)

router = APIRouter()


@router.post("/upload-image/")
async def upload_image(
    user_id: str = Form(...),
    access_id: str = Form(...),
    image_time: str = Form(...),
    file: UploadFile = File(...),
):
    try:
        # logger.info(f"📥 이미지 처리 시작 - user_id={user_id}, access_id={access_id}")

        # 문자열로 받은 image_time → datetime 변환
        try:
            image_time_obj = datetime.strptime(image_time, "%Y:%m:%d %H:%M:%S")
        except ValueError as e:
            logger.error(f"❌ image_time 파싱 실패: {e}")
            raise HTTPException(
                status_code=400,
                detail="날짜 형식이 잘못되었습니다. (예: 2025:05:08 00:00:00)",
            )

        # logger.info(f"📥 이미지 업로드 시작 - user_id={user_id}, access_id={access_id}")

        async with httpx.AsyncClient() as client:
            # 중복 체크 요청
            check_url = f"http://backend-service:8083/api/v1/images/check"
            params = {"userId": user_id, "accessId": access_id}

            check_response = await client.get(check_url, params=params)
            # logger.info(f"🔍 중복 체크 응답: {check_response.status_code}")
            # logger.debug(f"🔍 중복 체크 바디: {check_response.text}")

            if check_response.status_code != 200:
                raise HTTPException(status_code=500, detail="중복 확인 실패")

            check_result = check_response.json().get("result", {})
            if check_result.get("exist", False):
                logger.warning(
                    f"⚠️ 이미 존재하는 이미지입니다. 업로드 중단 - access_id={access_id}"
                )
                return {
                    "access_id": access_id,
                    "image_time": image_time,
                    "status": "skipped",
                    "message": "이미 등록된 이미지입니다.",
                }

        image_bytes = await file.read()
        text_score = classify_image_from_bytes(image_bytes)
        # logger.info(f"🔍 이미지 분류 점수: {text_score:.3f} (access_id={access_id})")

        if text_score <= 0.09:  # photo
            caption = generate_image_caption(image_bytes)
            ocr_text = extract_text_from_image(image_bytes)

            target = "photo"

            # 두 개의 텍스트를 하나로 결합 (개행으로 구분)
            content = f"[Caption]\n{caption}\n\n[OCR]\n{ocr_text}".strip()

            # logger.info(f"🖼️ 이미지 설명 생성 완료 - {caption}")
            # logger.info(f"🔤 OCR 텍스트 추출 완료 - {ocr_text}")
        else:
            extracted_text = extract_text_from_image(image_bytes)
            target = "info"
            content = extracted_text
            # logger.info(f"📝 텍스트 추출 완료 - {extracted_text}")

        text_for_embedding = f"{access_id} ({image_time}): {content}"
        namespace = save_text_to_pinecone(user_id, text_for_embedding, target)
        # logger.info(f"✅ 벡터 저장 완료 - namespace={namespace}")

        image_payload = {
            "userId": user_id,
            "accessId": access_id,
            "imageTime": image_time,
            "type": target,
            "content": content,
        }

        async with httpx.AsyncClient() as client:
            # logger.info(f"📤 이미지 정보 전송 → payload: {image_payload}")
            # logger.info(
            #     f"📤 JSON:\n{json.dumps(image_payload, ensure_ascii=False, indent=2)}"
            # )

            image_response = await client.post(
                "http://backend-service:8083/api/v1/images", json=image_payload
            )
            # logger.info(f"📥 이미지 응답 상태: {image_response.status_code}")
            # logger.debug(f"📥 이미지 응답 내용: {image_response.text}")

            if image_response.status_code != 200:
                raise HTTPException(status_code=500, detail="이미지 정보 저장 실패")

            image_id = image_response.json().get("result", {}).get("imageId")

            if target == "info":
                schedule_result = extract_schedule(content)
                if schedule_result.get("is_schedule") and schedule_result.get(
                    "datetime"
                ):
                    plan_payload = {
                        "userId": user_id,
                        "planTime": image_time,
                        "planContent": schedule_result.get("event", content),
                        "imageId": image_id,
                    }

                    # logger.info(f"📤 일정 등록 전송 → payload: {plan_payload}")
                    # logger.info(
                    #     f"📤 JSON:\n{json.dumps(plan_payload, ensure_ascii=False, indent=2)}"
                    # )

                    plan_response = await client.post(
                        "http://backend-service:8083/api/v1/plans", json=plan_payload
                    )

                    if plan_response.status_code != 200:
                        logger.warning(f"⚠️ 일정 등록 실패: {plan_response.text}")
                    else:
                        logger.info(f"📅 일정 등록 완료: {plan_payload}")

        return {
            "access_id": access_id,
            "image_time": image_time,
            "type": target,
            "namespace": namespace,
            "content": text_for_embedding,
            "image_id": image_id,
            "status": "success",
        }

    except Exception as e:
        logger.error(f"❌ 처리 중 오류 발생 (access_id={access_id}): {e}")
        return {
            "access_id": access_id,
            "image_time": image_time,
            "status": "error",
            "message": str(e),
        }
