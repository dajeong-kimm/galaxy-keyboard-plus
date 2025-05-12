import os
import re
import json
import subprocess
import tempfile
import time
from typing import Literal, Optional, Dict, List, Any, Union
from dotenv import load_dotenv
import openai
import shutil

# 환경 변수 로드
load_dotenv()

# OpenAI 클라이언트 설정
client = openai.OpenAI(api_key=os.getenv("OPENAI_API_KEY"))

MCP_WEB_RESEARCH_ENABLED = True
MCP_TIMEOUT = 30


class MCPWebResearch:
    """MCP 웹 검색 기능을 관리하는 클래스"""

    def __init__(self):
        self.process = None
        self.temp_dir = None
        self.initialized = False
        self.setup_mcp_server()

    def setup_mcp_server(self) -> None:
        if not MCP_WEB_RESEARCH_ENABLED:
            return

        try:
            self.temp_dir = tempfile.TemporaryDirectory()

            # npx 경로 확인
            npx_path = shutil.which("npx")
            if not npx_path:
                raise FileNotFoundError("npx 실행 파일을 찾을 수 없습니다.")
            print("✅ npx 위치:", npx_path)

            # 임시 배치 파일 생성
            bat_path = os.path.join(self.temp_dir.name, "run_mcp.bat")
            with open(bat_path, "w", encoding="utf-8") as f:
                f.write(f'"{npx_path}" -y @mzxrai/mcp-webresearch@latest\n')

            # 배치 파일 실행
            self.process = subprocess.Popen(
                ["cmd.exe", "/c", bat_path],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1,
            )

            # 초기화 메시지 전송
            init_msg = {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "initialize",
                "params": {
                    "protocolVersion": "0.1.0",
                    "capabilities": {},
                    "clientInfo": {"name": "mcp-client", "version": "0.1.0"},
                },
            }

            self.process.stdin.write(json.dumps(init_msg) + "\n")
            self.process.stdin.flush()

            # 응답 대기
            response = self._read_response(timeout=5)
            if response and "result" in response:
                self.initialized = True
                print("✅ MCP 웹 검색 서버가 성공적으로 초기화되었습니다.")
            else:
                print("⚠️ MCP 웹 검색 서버 초기화 실패")
                self.cleanup()

        except Exception as e:
            print(f"🚨 MCP 웹 검색 서버 설정 오류: {e}")
            self.cleanup()

    def _read_response(self, timeout=MCP_TIMEOUT) -> Dict:
        """MCP 서버로부터 응답 읽기"""
        if not self.process:
            return {}

        start_time = time.time()
        result = ""

        while time.time() - start_time < timeout:
            line = self.process.stdout.readline().strip()
            if line:
                try:
                    return json.loads(line)
                except json.JSONDecodeError:
                    result += line
            time.sleep(0.1)

        # 타임아웃일 경우 stderr 출력 시도
        if self.process:
            err = self.process.stderr.read()
            print("❌ MCP stderr 로그:\n", err)

        return {}

    def search_google(self, query: str) -> Dict:
        """Google 검색 수행"""
        if not self.initialized:
            return {"error": "MCP 웹 검색 서버가 초기화되지 않았습니다."}

        try:
            search_msg = {
                "jsonrpc": "2.0",
                "id": 2,
                "method": "tools/call",
                "params": {"name": "search_google", "arguments": {"query": query}},
            }

            self.process.stdin.write(json.dumps(search_msg) + "\n")
            self.process.stdin.flush()

            response = self._read_response()
            if response and "result" in response:
                content = response["result"].get("content", [])
                if content and len(content) > 0:
                    text_content = content[0].get("text", "{}")
                    try:
                        return json.loads(text_content)
                    except json.JSONDecodeError:
                        return {"error": "검색 결과 파싱 실패", "raw": text_content}

            return {"error": "검색 실패 또는 결과 없음"}

        except Exception as e:
            return {"error": f"검색 중 오류 발생: {str(e)}"}

    def visit_page(self, url: str, take_screenshot: bool = False) -> Dict:
        """웹페이지 방문 및 컨텐츠 추출"""
        if not self.initialized:
            return {"error": "MCP 웹 검색 서버가 초기화되지 않았습니다."}

        try:
            visit_msg = {
                "jsonrpc": "2.0",
                "id": 3,
                "method": "tools/call",
                "params": {
                    "name": "visit_page",
                    "arguments": {"url": url, "takeScreenshot": take_screenshot},
                },
            }

            self.process.stdin.write(json.dumps(visit_msg) + "\n")
            self.process.stdin.flush()

            response = self._read_response()
            if response and "result" in response:
                content = response["result"].get("content", [])
                if content and len(content) > 0:
                    text_content = content[0].get("text", "{}")
                    try:
                        return json.loads(text_content)
                    except json.JSONDecodeError:
                        return {
                            "error": "페이지 방문 결과 파싱 실패",
                            "raw": text_content,
                        }

            return {"error": "페이지 방문 실패 또는 결과 없음"}

        except Exception as e:
            return {"error": f"페이지 방문 중 오류 발생: {str(e)}"}

    def cleanup(self):
        """리소스 정리"""
        if self.process:
            try:
                self.process.terminate()
                self.process = None
            except:
                pass

        if self.temp_dir:
            try:
                self.temp_dir.cleanup()
                self.temp_dir = None
            except:
                pass

        self.initialized = False

    def __del__(self):
        """소멸자에서 정리"""
        self.cleanup()


# MCP 웹 검색 인스턴스 초기화
mcp_web_research = MCPWebResearch() if MCP_WEB_RESEARCH_ENABLED else None


def determine_query_type(query: str) -> Literal["photo", "info", "ambiguous", "web"]:
    """질문이 사진 관련인지 정보 관련인지 웹 검색이 필요한지 판별"""
    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[
            {
                "role": "system",
                "content": (
                    "사용자의 질문이 사진(이미지)을 찾으려는 것인지, "
                    "정보(텍스트/일정)를 찾으려는 것인지, "
                    "웹 검색이 필요한지, 혹은 애매한지를 판단해줘. "
                    "'photo', 'info', 'web', 'ambiguous' 중 하나로만 응답해. "
                    "만약 질문이 현재 뉴스, 최신 정보, 특정 웹사이트 내용, "
                    "실시간 데이터 등을 요구하면 'web'으로 분류해."
                ),
            },
            {"role": "user", "content": query},
        ],
        max_tokens=10,
    )
    answer = response.choices[0].message.content.strip().lower()
    if answer not in {"photo", "info", "web", "ambiguous"}:
        return "ambiguous"
    return answer


def perform_web_search(query: str) -> Dict:
    """웹 검색 수행 및 결과 파싱"""
    if (
        not MCP_WEB_RESEARCH_ENABLED
        or not mcp_web_research
        or not mcp_web_research.initialized
    ):
        return {"error": "웹 검색 기능이 활성화되지 않았습니다."}

    # 웹 검색 수행
    search_results = mcp_web_research.search_google(query)

    # 오류 발생 시 처리
    if "error" in search_results:
        return search_results

    # 결과가 유효한 경우 상위 결과에 대해 웹페이지 방문
    web_contents = []

    if isinstance(search_results, list) and search_results:
        # 최대 3개의 상위 결과에 대해 웹페이지 방문
        for i, result in enumerate(search_results[:3]):
            if "url" in result:
                page_data = mcp_web_research.visit_page(result["url"])
                if "error" not in page_data and "content" in page_data:
                    web_contents.append(
                        {
                            "title": result.get("title", "제목 없음"),
                            "url": result["url"],
                            "content": page_data.get("content", ""),
                            "snippet": result.get("snippet", ""),
                        }
                    )

    return {"search_results": search_results, "web_contents": web_contents}


def generate_answer_from_web_search(query: str, web_search_results: Dict) -> str:
    """웹 검색 결과를 바탕으로 LLM이 답변 생성"""
    # 에러 처리
    if "error" in web_search_results:
        return f"웹 검색 중 오류가 발생했습니다: {web_search_results['error']}"

    # 컨텍스트 구성
    context = ""

    # 웹 콘텐츠가 있는 경우
    if "web_contents" in web_search_results and web_search_results["web_contents"]:
        context += "### 웹페이지 내용:\n\n"
        for i, content in enumerate(web_search_results["web_contents"]):
            context += f"출처 {i+1}: {content['title']} ({content['url']})\n"
            # 컨텐츠가 너무 길면 잘라내기
            page_content = content.get("content", "")
            if len(page_content) > 1500:
                page_content = page_content[:1500] + "... (내용 생략)"
            context += f"{page_content}\n\n"

    # 검색 결과만 있는 경우
    elif (
        "search_results" in web_search_results and web_search_results["search_results"]
    ):
        context += "### 검색 결과:\n\n"
        for i, result in enumerate(web_search_results["search_results"][:5]):
            context += f"결과 {i+1}: {result.get('title', '제목 없음')} ({result.get('url', 'URL 없음')})\n"
            context += f"{result.get('snippet', '내용 없음')}\n\n"

    # 컨텍스트가 없으면 오류 메시지 반환
    if not context:
        return "웹 검색 결과가 없습니다."

    # LLM에 질문과 컨텍스트 전달하여 답변 생성
    prompt = f"""다음은 웹 검색으로 찾은 정보들입니다:

{context}

사용자 질문: "{query}"

위 정보를 바탕으로 사용자 질문에 정확하게 답변해주세요. 출처를 인용하면서 답변하고, 정보가 없는 경우 모르는 것을 인정해주세요."""

    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": prompt}],
        max_tokens=800,
    )

    return response.choices[0].message.content.strip()


def web_search(query: str) -> str:
    """웹 검색 수행 및 답변 생성"""
    # 웹 검색 수행
    web_results = perform_web_search(query)
    # 검색 결과로 답변 생성
    return generate_answer_from_web_search(query, web_results)
