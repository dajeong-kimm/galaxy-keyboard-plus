"""
대체 웹 검색 솔루션
Google 검색이 차단될 때 사용할 수 있는 대안들
"""
import requests
from typing import List, Dict
import json
import os
from dotenv import load_dotenv
import openai

load_dotenv()
client = openai.OpenAI(api_key=os.getenv("OPENAI_API_KEY"))


def search_duckduckgo(query: str) -> List[Dict]:
    """DuckDuckGo HTML 검색"""
    try:
        url = "https://html.duckduckgo.com/html/"
        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
        }
        data = {'q': query}
        
        response = requests.post(url, headers=headers, data=data, timeout=10)
        response.raise_for_status()
        
        # HTML 파싱
        import re
        pattern = r'<a rel="nofollow" class="result__a" href="([^"]+)">([^<]+)</a>'
        matches = re.findall(pattern, response.text)
        
        results = []
        for url, title in matches[:5]:
            # 스니펫 추출
            snippet_pattern = rf'<a[^>]*href="{re.escape(url)}"[^>]*>.*?</a>\s*<span class="result__snippet"[^>]*>([^<]+)</span>'
            snippet_match = re.search(snippet_pattern, response.text, re.DOTALL)
            snippet = snippet_match.group(1) if snippet_match else ""
            
            results.append({
                'title': title.strip(),
                'url': url,
                'snippet': snippet.strip()
            })
        
        return results
        
    except Exception as e:
        print(f"DuckDuckGo 검색 실패: {e}")
        return []


def search_google_cache(query: str) -> List[Dict]:
    """Google 검색 결과 캐시 (목업)"""
    # 실제로는 이전 검색 결과를 캐싱하거나 데이터베이스에 저장
    cache = {
        "Python programming": [
            {
                "title": "Python.org",
                "url": "https://www.python.org",
                "snippet": "The official home of the Python Programming Language"
            },
            {
                "title": "Python Tutorial - W3Schools",
                "url": "https://www.w3schools.com/python/",
                "snippet": "Learn Python programming with this comprehensive tutorial"
            }
        ],
        "hello world": [
            {
                "title": "Hello, World! - Wikipedia",
                "url": "https://en.wikipedia.org/wiki/%22Hello,_World!%22_program",
                "snippet": "A 'Hello, World!' program is a simple computer program that outputs..."
            }
        ]
    }
    
    # 부분 일치 검색
    for key in cache:
        if query.lower() in key.lower() or key.lower() in query.lower():
            return cache[key]
    
    return []


def search_with_fallback(query: str) -> Dict:
    """폴백이 있는 검색"""
    # 1. DuckDuckGo 시도
    results = search_duckduckgo(query)
    
    # 2. 캐시 검색
    if not results:
        results = search_google_cache(query)
    
    # 3. LLM 직접 답변
    if not results:
        return {
            "error": "검색 실패",
            "fallback": True,
            "message": "웹 검색이 실패했지만 AI가 직접 답변합니다"
        }
    
    return {"results": results}


def generate_answer(query: str, search_results: Dict) -> str:
    """검색 결과로 답변 생성"""
    if "error" in search_results:
        # 검색 실패 시 LLM 직접 답변
        prompt = f"""웹 검색이 실패했지만, 당신의 지식으로 다음 질문에 답변해주세요:

질문: {query}

최신 정보가 필요한 경우 그 사실을 언급하고 대안을 제시하세요."""
    else:
        # 검색 성공 시
        results = search_results.get("results", [])
        context = "검색 결과:\n\n"
        for i, result in enumerate(results, 1):
            context += f"{i}. {result['title']}\n"
            context += f"   URL: {result['url']}\n"
            context += f"   요약: {result['snippet']}\n\n"
        
        prompt = f"""다음 검색 결과를 바탕으로 사용자 질문에 답변해주세요:

{context}

질문: {query}"""
    
    try:
        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[{"role": "user", "content": prompt}],
            max_tokens=500
        )
        return response.choices[0].message.content.strip()
    except Exception as e:
        return f"답변 생성 실패: {str(e)}"


def web_search_reliable(query: str) -> str:
    """신뢰할 수 있는 웹 검색"""
    print(f"대체 검색 중: {query}")
    
    # 검색 수행
    search_results = search_with_fallback(query)
    
    # 답변 생성
    answer = generate_answer(query, search_results)
    
    return answer


# 테스트
if __name__ == "__main__":
    test_queries = [
        "Python 최신 버전",
        "2024년 AI 트렌드",
        "오늘 날씨"
    ]
    
    for query in test_queries:
        print(f"\n질문: {query}")
        print("-" * 40)
        answer = web_search_reliable(query)
        print(f"답변:\n{answer}")
