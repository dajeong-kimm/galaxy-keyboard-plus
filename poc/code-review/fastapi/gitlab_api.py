import os
from dotenv import load_dotenv
import httpx

GITLAB_API_URL = "https://lab.ssafy.com/api/v4"

load_dotenv()
TOKEN = os.getenv("GITLAB_TOKEN")

if not TOKEN:
    raise EnvironmentError("❌ GITLAB_TOKEN 환경변수가 설정되어 있지 않습니다.")


async def get_merge_request_changes(project_id, mr_iid):
    url = f"{GITLAB_API_URL}/projects/{project_id}/merge_requests/{mr_iid}/changes"
    headers = {"PRIVATE-TOKEN": TOKEN}
    print(f"🔍 [GitLab] MR 변경사항 요청: project_id={project_id}, mr_iid={mr_iid}")
    async with httpx.AsyncClient() as client:
        res = await client.get(url, headers=headers)
        print(f"✅ [GitLab] 변경사항 응답 상태코드: {res.status_code}")
        print(f"📦 [GitLab] 응답 데이터:", res.text)
        return res.json()


async def post_mr_comment(project_id, mr_iid, body):
    url = f"{GITLAB_API_URL}/projects/{project_id}/merge_requests/{mr_iid}/notes"
    headers = {"PRIVATE-TOKEN": TOKEN}
    data = {"body": body}
    print(f"📝 [GitLab] MR 댓글 등록 시도: project_id={project_id}, mr_iid={mr_iid}")
    async with httpx.AsyncClient() as client:
        res = await client.post(url, headers=headers, data=data)
        print(f"✅ [GitLab] 댓글 등록 완료: status={res.status_code}")
