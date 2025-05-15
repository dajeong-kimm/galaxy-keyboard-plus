from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from app.utils.image_captioner import generate_image_caption
from app.utils.image_text_extractor import extract_text_from_image
from app.utils.vector_store import save_text_to_pinecone
import json
import os
import psycopg2
from openai import OpenAI
from dotenv import load_dotenv
from datetime import datetime
import traceback
import time
import logging
import asyncio
from concurrent.futures import ThreadPoolExecutor

# 로거 설정
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

load_dotenv()
router = APIRouter()

# ThreadPoolExecutor 생성 (동기 함수를 비동기로 실행하기 위해)
executor = ThreadPoolExecutor(max_workers=5)

# DB 연결 설정
DB_PARAMS = {
    "host": "3.38.95.110",
    "port": "5434",
    "database": os.getenv("POSTGRES_RAG_DB_NAME"),
    "user": os.getenv("POSTGRES_RAG_USER"),
    "password": os.getenv("POSTGRES_RAG_PASSWORD"),
}


async def async_generate_image_caption(image_bytes):
    """동기 함수를 비동기로 실행"""
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(executor, generate_image_caption, image_bytes)


async def async_extract_text_from_image(image_bytes):
    """동기 함수를 비동기로 실행"""
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(executor, extract_text_from_image, image_bytes)


async def async_save_text_to_pinecone(user_id, combined_text, namespace):
    """동기 함수를 비동기로 실행"""
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(
        executor, save_text_to_pinecone, user_id, combined_text, namespace
    )


@router.post("/upload-image-keyword/")
async def upload_image_keyword(
    user_id: str = Form(...),
    access_id: str = Form(...),
    file: UploadFile = File(...),
):
    total_start_time = time.time()

    try:
        logger.info(
            f"🔍 이미지 업로드 프로세스 시작 - user_id: {user_id}, access_id: {access_id}"
        )
        print("🔍 DB_PARAMS = ", DB_PARAMS)

        # DB 연결
        db_connect_start = time.time()
        connection = psycopg2.connect(**DB_PARAMS)
        cursor = connection.cursor()
        logger.info(f"✅ DB 연결 완료: {time.time() - db_connect_start:.3f}초")

        # access_id 중복 체크
        duplicate_check_start = time.time()
        check_query = "SELECT id FROM images WHERE access_id = %s;"
        cursor.execute(check_query, (access_id,))
        existing = cursor.fetchone()
        logger.info(f"✅ 중복 체크 완료: {time.time() - duplicate_check_start:.3f}초")

        if existing:
            cursor.close()
            connection.close()
            logger.info(f"⚠️ 이미 존재하는 이미지 - access_id: {access_id}")
            return {
                "user_id": user_id,
                "access_id": access_id,
                "status": "skipped",
                "message": "이미 등록된 이미지입니다.",
            }

        # 이미지 읽기
        file_read_start = time.time()
        image_bytes = await file.read()
        logger.info(
            f"✅ 이미지 파일 읽기 완료: {time.time() - file_read_start:.3f}초, 크기: {len(image_bytes)} bytes"
        )

        # 비동기로 동시에 처리할 작업들: 캡션 생성과 OCR 텍스트 추출
        async_tasks_start = time.time()

        # 캡션 생성과 OCR을 동시에 시작
        caption_task = async_generate_image_caption(image_bytes)
        ocr_task = async_extract_text_from_image(image_bytes)

        # 두 작업이 완료될 때까지 대기
        caption, ocr_text = await asyncio.gather(caption_task, ocr_task)

        logger.info(
            f"✅ 캡션 생성 및 OCR 동시 처리 완료: {time.time() - async_tasks_start:.3f}초"
        )

        # 벡터 스토어 저장과 OpenAI 키워드 추출을 동시에 처리
        parallel_tasks_start = time.time()

        # 벡터 스토어 저장 시작
        combined_text = f"{access_id}: {ocr_text} {caption}".strip()
        namespace = f"information"
        vector_store_task = async_save_text_to_pinecone(
            user_id, combined_text, namespace
        )

        # OpenAI 키워드 추출 시작
        openai_task = asyncio.create_task(extract_keywords_async(caption))

        # 두 작업 완료 대기
        await vector_store_task
        keywords = await openai_task

        logger.info(
            f"✅ 벡터 스토어 저장 및 키워드 추출 완료: {time.time() - parallel_tasks_start:.3f}초"
        )

        # 3. DB 저장
        db_save_start = time.time()
        now = datetime.utcnow()

        # 이미지 정보 INSERT
        insert_image_query = """
        INSERT INTO images (user_id, access_id, caption, image_time)
        VALUES (%s, %s, %s, %s)
        ON CONFLICT (access_id) DO UPDATE SET caption = EXCLUDED.caption
        RETURNING id;
        """
        cursor.execute(insert_image_query, (user_id, access_id, caption, now))
        image_id = cursor.fetchone()[0]

        # 키워드 INSERT
        insert_keyword_query = """
        INSERT INTO image_keywords (image_id, keyword, created_at)
        VALUES (%s, %s, %s);
        """
        for keyword in keywords:
            cursor.execute(insert_keyword_query, (image_id, keyword, now))

        connection.commit()
        cursor.close()
        connection.close()

        logger.info(f"✅ DB 저장 완료: {time.time() - db_save_start:.3f}초")

        # 전체 프로세스 시간
        total_time = time.time() - total_start_time
        logger.info(f"🎉 전체 프로세스 완료: {total_time:.3f}초")
        logger.info(f"📊 성능 요약:")
        logger.info(
            f"  - DB 연결: {time.time() - db_connect_start - (time.time() - duplicate_check_start):.3f}초"
        )
        logger.info(
            f"  - 중복 체크: {time.time() - duplicate_check_start - (time.time() - file_read_start):.3f}초"
        )
        logger.info(
            f"  - 이미지 읽기: {time.time() - file_read_start - (time.time() - async_tasks_start):.3f}초"
        )
        logger.info(
            f"  - 캡션 생성 + OCR (병렬): {time.time() - async_tasks_start - (time.time() - parallel_tasks_start):.3f}초"
        )
        logger.info(
            f"  - 벡터 스토어 + 키워드 추출 (병렬): {time.time() - parallel_tasks_start - (time.time() - db_save_start):.3f}초"
        )
        logger.info(f"  - DB 저장: {time.time() - db_save_start:.3f}초")

        return {
            "user_id": user_id,
            "access_id": access_id,
            "caption": caption,
            "keywords": keywords,
            "status": "success",
            "processing_time": f"{total_time:.3f}초",
        }

    except Exception as e:
        error_time = time.time() - total_start_time
        logger.error(f"❌ 오류 발생 ({error_time:.3f}초 후): {str(e)}")
        print("❌ 예외 발생:")
        traceback.print_exc()
        return {
            "user_id": user_id,
            "access_id": access_id,
            "status": "error",
            "message": str(e) or repr(e),
            "processing_time": f"{error_time:.3f}초",
        }


async def extract_keywords_async(caption):
    """OpenAI를 사용한 비동기 키워드 추출"""
    loop = asyncio.get_event_loop()

    def sync_extract_keywords():
        client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
        prompt = (
            f"다음 사진 설명을 보고 사람들이 검색할 수 있는 키워드를 가능한 많이 추출해줘. "
            f"각 키워드는 한 단어로 된 명사 위주로 해줘. 중복 없이 출력하고, 결과는 JSON 배열로 줘. "
            f'예시: ["노트북", "회의", "커피"]\n\n'
            f"설명: {caption}"
        )

        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {
                    "role": "system",
                    "content": "너는 키워드 추출 전문가야. 출력은 JSON 배열로만 해. 마크다운 코드블록 쓰지 마.",
                },
                {"role": "user", "content": prompt},
            ],
            temperature=0.3,
        )

        keywords_raw = response.choices[0].message.content

        # 코드블록 제거
        if keywords_raw.startswith("```"):
            keywords_raw = keywords_raw.strip("`").strip()
            if keywords_raw.lower().startswith("json"):
                keywords_raw = keywords_raw[4:].strip()

        try:
            return json.loads(keywords_raw)
        except json.JSONDecodeError:
            return [
                kw.strip().strip('"')
                for kw in keywords_raw.strip("[]").split(",")
                if kw.strip()
            ]

    return await loop.run_in_executor(executor, sync_extract_keywords)
