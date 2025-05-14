# -*- coding: utf-8 -*-
"""
PoC: GitLab 프로젝트 분석 → Pinecone 저장 → 검색

사전 준비:
 1. python-gitlab 설치: pip install python-gitlab
 2. Pinecone SDK 설치: pip install pinecone-client
 3. sentence-transformers 설치: pip install sentence-transformers
 4. 환경 변수 설정:
    - GITLAB_URL
    - GITLAB_TOKEN
    - PINECONE_API_KEY
    - PINECONE_ENVIRONMENT

기능:
 1) GitLab 연결 및 프로젝트 메타데이터, 커밋, MR 로그 수집 후 요약 출력
 2) Pinecone 초기화 및 벡터 upsert
 3) 저장된 벡터 검색 예시
"""
import os
import gitlab
import pinecone
from sentence_transformers import SentenceTransformer

# === 1. GitLab 연결 및 프로젝트 정보 수집 ===

def fetch_gitlab_project_info(project_path: str):
    """
    GitLab 프로젝트에 연결하여 메타정보, 커밋 메시지, MR 로그를 수집합니다.

    Args:
        project_path: 'namespace/project' 형식
    Returns:
        dict: 프로젝트 정보 딕셔너리
    """
    # 환경 변수에서 GitLab URL 및 Token 읽기
    GITLAB_URL = os.getenv('GITLAB_URL')
    GITLAB_TOKEN = os.getenv('GITLAB_TOKEN')

    # GitLab 클라이언트 초기화
    gl = gitlab.Gitlab(GITLAB_URL, private_token=GITLAB_TOKEN)
    project = gl.projects.get(project_path)

    # 메타데이터
    info = {
        'id': project.id,
        'name': project.name,
        'description': project.description or '',
        'web_url': project.web_url,
    }

    # 모든 커밋 메시지 수집
    commits = project.commits.list(all=True)
    info['commits'] = [c.message for c in commits]

    # 모든 Merge Request 정보 수집
    mrs = project.mergerequests.list(state='all', all=True)
    info['merge_requests'] = [f"[{mr.title}] {mr.web_url}" for mr in mrs]

    # 프로젝트 요약 출력
    print("=== 프로젝트 메타 데이터 ===")
    print(f"ID: {info['id']}")
    print(f"Name: {info['name']}")
    print(f"Description: {info['description']}")
    print(f"URL: {info['web_url']}\n")

    print("=== 커밋 메시지 (최근 5개) ===")
    for msg in info['commits'][-5:]:
        print(f"- {msg}")
    print()

    print("=== Merge Requests (최근 5개) ===")
    for mr in info['merge_requests'][-5:]:
        print(f"- {mr}")
    print()

    return info


# === 2. Pinecone DB에 프로젝트 정보 저장 ===

def init_pinecone(index_name: str = 'project-index') -> pinecone.Index:
    """
    Pinecone 초기화 및 인덱스 생성/접근
    """
    # 환경 변수에서 Pinecone API 키, 환경 읽기
    PINECONE_API_KEY = os.getenv('PINECONE_API_KEY')
    PINECONE_ENV = os.getenv('PINECONE_ENVIRONMENT')

    pinecone.init(api_key=PINECONE_API_KEY, environment=PINECONE_ENV)

    # 인덱스가 없으면 생성 (차원은 임베딩 모델 기준)
    if index_name not in pinecone.list_indexes():
        pinecone.create_index(name=index_name, dimension=768)

    return pinecone.Index(index_name)


def upsert_project_to_pinecone(info: dict, index: pinecone.Index):
    """
    프로젝트 정보를 벡터로 변환하여 Pinecone에 저장
    """
    # 임베딩 모델 로드
    model = SentenceTransformer('all-MiniLM-L6-v2')

    # 텍스트 결합: 이름 + 설명 + 모든 커밋 메시지
    text = info['name'] + ' ' + info['description'] + ' ' + ' '.join(info['commits'])
    vector = model.encode(text).tolist()

    # upsert
    index.upsert(
        vectors=[{
            'id': str(info['id']),
            'values': vector,
            'metadata': {
                'name': info['name'],
                'description': info['description'],
                'url': info['web_url']
            }
        }]
    )
    print(f"프로젝트 {info['name']} (ID: {info['id']}) 업서트 완료.")


# === 3. Pinecone에서 정보 검색 ===

def query_pinecone(query_text: str, index: pinecone.Index, top_k: int = 5):
    """
    쿼리 텍스트로 벡터 검색 수행 후 결과 반환
    """
    model = SentenceTransformer('all-MiniLM-L6-v2')
    q_vector = model.encode(query_text).tolist()

    response = index.query(
        vector=q_vector,
        top_k=top_k,
        include_metadata=True
    )

    print(f"=== 쿼리 결과: '{query_text}' ===")
    for match in response['matches']:
        meta = match['metadata']
        score = match['score']
        print(f"- ID: {match['id']}, 스코어: {score:.4f}, 이름: {meta['name']}, URL: {meta['url']}")
    print()

    return response


# === 사용 예시 ===
if __name__ == '__main__':
    # 1) GitLab에서 정보 수집
    project_path = 'your-group/your-project'
    info = fetch_gitlab_project_info(project_path)

    # 2) Pinecone 초기화 및 저장
    index = init_pinecone()
    upsert_project_to_pinecone(info, index)

    # 3) 검색 예시
    query_pinecone('로그인 기능 구현', index)
