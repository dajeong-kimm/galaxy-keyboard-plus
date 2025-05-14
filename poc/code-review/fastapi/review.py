from gitlab_api import get_merge_request_changes, post_mr_comment
from llm import generate_review_prompt_chunked


def summarize_changes(changes):
    summaries = []
    for change in changes.get("changes", []):
        filename = change.get("new_path", "unknown")
        diff = change.get("diff", "")

        if diff:  # 변경 내용이 있는 경우만 처리
            summary = f"📄 {filename} 변경사항:\n"
            summary += diff
            summaries.append((filename, summary))
    return summaries


async def handle_merge_request(event):
    print("📥 [Handler] Merge Request 이벤트 처리 시작")
    project_id = event["project"]["id"]
    mr_iid = event["object_attributes"]["iid"]
    mr_desc = event["object_attributes"].get("description", "")

    print(f"🔎 [Handler] project_id={project_id}, mr_iid={mr_iid}")
    print("📦 [Handler] MR 설명:", mr_desc)

    changes = await get_merge_request_changes(project_id, mr_iid)
    file_summaries = summarize_changes(changes)
    
    if not file_summaries:
        print("⚠️ [Handler] 변경된 파일이 없습니다.")
        return
        
    print(f"📊 [Handler] 변경 파일 수: {len(file_summaries)}")
    
    # 종합 리뷰 메시지 생성
    review_comment = f"🤖 **자동 코드 리뷰 결과**\n\n"
    
    # 각 파일마다 리뷰 진행
    for filename, file_summary in file_summaries:
        print(f"📁 [Handler] '{filename}' 파일 리뷰 중...")
        
        # 파일별 리뷰 진행
        file_review = await generate_review_prompt_chunked(mr_desc, file_summary)
        
        # 리뷰 결과를 종합 메시지에 추가
        review_comment += f"### {filename} 리뷰\n\n{file_review}\n\n---\n\n"
        
        print(f"✅ [Handler] '{filename}' 파일 리뷰 완료")
    
    # 리뷰 내용 로그
    print("✍️ [Handler] 전체 리뷰 내용:")
    print(review_comment)
    
    # 리뷰 결과 보내기
    await post_mr_comment(project_id, mr_iid, review_comment)
    print("🎉 [Handler] 코드 리뷰 완료 및 댓글 등록")
