"""
MCP 웹 검색 - 통합 최종 버전
"""
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
import logging
import threading
import queue
import atexit
import sys
import glob

# 환경 변수 로드
load_dotenv()

# OpenAI 클라이언트 설정
client = openai.OpenAI(api_key=os.getenv("OPENAI_API_KEY"))

MCP_WEB_RESEARCH_ENABLED = True
MCP_TIMEOUT = 60


class MCPWebResearch:
    """MCP 웹 검색 기능을 관리하는 클래스"""

    def __init__(self):
        self.process = None
        self.temp_dir = None
        self.temp_dir_path = None
        self.initialized = False
        self.response_queue = queue.Queue()
        self.reader_thread = None
        self.error_reader_thread = None
        self.message_id = 1
        self.available_tools = []
        self._ensure_playwright_browser()  # 브라우저 설치 확인
        self.setup_mcp_server()
        atexit.register(self.cleanup)

    def _ensure_playwright_browser(self):
        """Playwright Chromium 브라우저 설치 확인"""
        try:
            # Chromium이 이미 설치되어 있는지 확인
            chromium_path = os.path.expanduser(r"~\AppData\Local\ms-playwright")
            browsers = glob.glob(os.path.join(chromium_path, "chromium-*"))
            
            if not browsers:
                print("Chromium 브라우저가 없습니다. 설치 중...")
                # Playwright 설치
                subprocess.run(["npm", "install", "playwright"], 
                             capture_output=True, shell=True)
                # Chromium 설치
                subprocess.run(["npx", "playwright", "install", "chromium"],
                             capture_output=True, shell=True)
                print("✅ Chromium 브라우저 설치 완료")
            else:
                print(f"✅ Chromium 브라우저 확인: {browsers[0]}")
                
        except Exception as e:
            print(f"브라우저 설치 확인 중 오류: {e}")

    def setup_mcp_server(self) -> None:
        if not MCP_WEB_RESEARCH_ENABLED:
            return

        try:
            # 안전한 임시 디렉토리 생성
            self.temp_dir_path = os.path.join(tempfile.gettempdir(), f"mcp_{int(time.time())}")
            os.makedirs(self.temp_dir_path, exist_ok=True)
            
            # Windows 인코딩 설정
            if sys.platform == 'win32':
                os.system('chcp 65001 > nul 2>&1')

            # npx 경로 확인
            npx_path = shutil.which("npx")
            if not npx_path:
                npx_path = r"C:\Program Files\nodejs\npx.CMD"
                if not os.path.exists(npx_path):
                    raise FileNotFoundError("npx 실행 파일을 찾을 수 없습니다.")
            print("✅ npx 위치:", npx_path)

            # 환경 변수 설정
            env = os.environ.copy()
            env['PYTHONIOENCODING'] = 'utf-8'
            env['NODE_OPTIONS'] = '--max-old-space-size=4096'
            env['LANG'] = 'en_US.UTF-8'
            
            # 브라우저 옵션을 환경 변수로 설정
            env['PUPPETEER_ARGS'] = '--no-sandbox --disable-setuid-sandbox --disable-gpu --disable-dev-shm-usage'
            env['PUPPETEER_SKIP_DOWNLOAD'] = 'true'  # 추가 다운로드 방지
            
            # Chrome/Chromium 경로 설정
            chromium_path = self._find_chromium_path()
            if chromium_path:
                env['PUPPETEER_EXECUTABLE_PATH'] = chromium_path
                env['PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH'] = chromium_path

            # 직접 MCP 실행 (브라우저 옵션 추가)
            cmd = [npx_path, "-y", "@mzxrai/mcp-webresearch@latest"]

            # Windows용 시작 정보
            startupinfo = None
            if sys.platform == 'win32':
                startupinfo = subprocess.STARTUPINFO()
                startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
                startupinfo.wShowWindow = subprocess.SW_HIDE

            self.process = subprocess.Popen(
                cmd,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                encoding='utf-8',
                errors='replace',
                bufsize=0,
                env=env,
                cwd=self.temp_dir_path,
                startupinfo=startupinfo,
                creationflags=subprocess.CREATE_NO_WINDOW if sys.platform == 'win32' else 0,
            )

            # stdout 리더 스레드 시작
            self.reader_thread = threading.Thread(target=self._read_output_thread)
            self.reader_thread.daemon = True
            self.reader_thread.start()

            # stderr 리더 스레드 시작
            self.error_reader_thread = threading.Thread(target=self._read_error_thread)
            self.error_reader_thread.daemon = True
            self.error_reader_thread.start()

            # 프로세스가 준비될 때까지 충분히 대기
            time.sleep(15)

            # 초기화 메시지 전송
            init_msg = {
                "jsonrpc": "2.0",
                "id": self.message_id,
                "method": "initialize",
                "params": {
                    "protocolVersion": "0.1.0",
                    "capabilities": {},
                    "clientInfo": {"name": "mcp-client", "version": "0.1.0"},
                },
            }

            self._send_message(init_msg)

            # 응답 대기
            response = self._wait_for_response(timeout=20)
            if response and "result" in response:
                self.initialized = True
                print("✅ MCP 웹 검색 서버가 성공적으로 초기화되었습니다.")
                logging.info(f"초기화 응답: {response}")
                
                # 사용 가능한 도구 확인
                self._check_available_tools()
            else:
                print("⚠️ MCP 웹 검색 서버 초기화 실패")
                if response:
                    print(f"응답: {response}")
                self.cleanup()

        except Exception as e:
            print(f"🚨 MCP 웹 검색 서버 설정 오류: {e}")
            logging.error(f"MCP 서버 설정 오류: {e}")
            self.cleanup()

    def _find_chromium_path(self) -> Optional[str]:
        """Chromium 실행 파일 경로 찾기"""
        possible_paths = [
            # Playwright가 설치한 Chromium
            os.path.expanduser(r"~\AppData\Local\ms-playwright\chromium-*\chrome-win\chrome.exe"),
            # Chrome 브라우저
            r"C:\Program Files\Google\Chrome\Application\chrome.exe",
            r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
            os.path.expanduser(r"~\AppData\Local\Google\Chrome\Application\chrome.exe"),
            # Brave 브라우저
            r"C:\Program Files\BraveSoftware\Brave-Browser\Application\brave.exe",
        ]
        
        for path in possible_paths:
            if "*" in path:
                # 와일드카드 처리
                matches = glob.glob(path)
                if matches:
                    return matches[0]
            elif os.path.exists(path):
                return path
        
        return None

    def _read_output_thread(self):
        """stdout에서 계속 읽어서 큐에 넣는 스레드"""
        buffer = ""
        while self.process and self.process.poll() is None:
            try:
                char = self.process.stdout.read(1)
                if not char:
                    time.sleep(0.1)
                    continue
                    
                buffer += char
                
                if char == '\n':
                    line = buffer.strip()
                    buffer = ""
                    
                    if line:
                        logging.debug(f"📥 MCP stdout: {line}")
                        
                        if line.startswith("{") and line.endswith("}"):
                            try:
                                data = json.loads(line)
                                self.response_queue.put(data)
                            except json.JSONDecodeError as e:
                                logging.error(f"JSON 파싱 실패: {line} - {e}")
            except Exception as e:
                logging.error(f"stdout 읽기 오류: {e}")
                break

    def _read_error_thread(self):
        """stderr에서 계속 읽어서 로깅하는 스레드"""
        while self.process and self.process.poll() is None:
            try:
                line = self.process.stderr.readline()
                if line:
                    logging.debug(f"📥 MCP stderr: {line.strip()}")
            except Exception as e:
                logging.error(f"stderr 읽기 오류: {e}")
                break

    def _send_message(self, message: Dict):
        """메시지를 MCP 서버로 전송"""
        if not self.process or self.process.poll() is not None:
            raise RuntimeError("MCP 프로세스가 실행 중이 아닙니다.")

        msg_str = json.dumps(message)
        logging.debug(f"📤 MCP로 전송: {msg_str}")
        self.process.stdin.write(msg_str + "\n")
        self.process.stdin.flush()
        self.message_id += 1

    def _wait_for_response(self, timeout=MCP_TIMEOUT) -> Dict:
        """큐에서 응답 대기"""
        try:
            return self.response_queue.get(timeout=timeout)
        except queue.Empty:
            logging.error(f"응답 타임아웃 ({timeout}초)")
            return {}

    def search_google(self, query: str) -> Dict:
        if not self.initialized:
            return {"error": "MCP 웹 검색 서버가 초기화되지 않았습니다."}

        # 도구 확인
        if "search_google" not in self.available_tools:
            return {"error": "search_google 도구를 사용할 수 없습니다.", "available_tools": self.available_tools}

        try:
            logging.info(f"📤 MCP search_google 요청: {query}")
            search_msg = {
                "jsonrpc": "2.0",
                "id": self.message_id,
                "method": "tools/call",
                "params": {"name": "search_google", "arguments": {"query": query}},
            }

            self._send_message(search_msg)

            response = self._wait_for_response(timeout=30)
            logging.info(f"📥 MCP search_google 응답: {response}")

            if response and "result" in response:
                content = response["result"].get("content", [])
                if content and len(content) > 0:
                    text_content = content[0].get("text", "{}")
                    
                    # 오류 메시지 확인
                    if "Failed to perform search" in text_content or "No search results found" in text_content:
                        # Google이 차단했을 때 DuckDuckGo로 대체
                        print("⚠️ Google 검색 차단, DuckDuckGo로 대체")
                        
                        try:
                            import requests
                            import re
                            
                            url = "https://html.duckduckgo.com/html/"
                            headers = {
                                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
                            }
                            data = {'q': query}
                            
                            resp = requests.post(url, headers=headers, data=data, timeout=10)
                            resp.raise_for_status()
                            
                            # HTML 파싱
                            pattern = r'<a rel="nofollow" class="result__a" href="([^"]+)">([^<]+)</a>'
                            matches = re.findall(pattern, resp.text)
                            
                            results = []
                            for url, title in matches[:5]:
                                results.append({
                                    'title': title.strip(),
                                    'url': url,
                                    'snippet': f'검색 결과: {title[:100]}...'
                                })
                            
                            if results:
                                return results
                        except Exception as e:
                            logging.error(f"DuckDuckGo 검색 실패: {e}")
                        
                        # 모든 검색 실패
                        return {
                            "error": "Google 검색 차단",
                            "message": "Google이 자동화된 브라우저를 차단했습니다.",
                            "details": text_content
                        }
                    
                    try:
                        return json.loads(text_content)
                    except json.JSONDecodeError:
                        return {"error": "검색 결과 파싱 실패", "raw": text_content}

            return {"error": "검색 실패 또는 결과 없음", "raw_response": response}

        except Exception as e:
            logging.error(f"검색 중 오류: {e}")
            return {"error": f"검색 중 오류 발생: {str(e)}"}

    def visit_page(self, url: str, take_screenshot: bool = False) -> Dict:
        """웹페이지 방문 및 컨텐츠 추출"""
        if not self.initialized:
            return {"error": "MCP 웹 검색 서버가 초기화되지 않았습니다."}

        # 도구 확인
        if "visit_page" not in self.available_tools:
            return {"error": "visit_page 도구를 사용할 수 없습니다.", "available_tools": self.available_tools}

        try:
            visit_msg = {
                "jsonrpc": "2.0",
                "id": self.message_id,
                "method": "tools/call",
                "params": {
                    "name": "visit_page",
                    "arguments": {"url": url, "takeScreenshot": take_screenshot},
                },
            }

            self._send_message(visit_msg)

            response = self._wait_for_response(timeout=20)
            if response and "result" in response:
                content = response["result"].get("content", [])
                if content and len(content) > 0:
                    text_content = content[0].get("text", "{}")
                    try:
                        return json.loads(text_content)
                    except json.JSONDecodeError:
                        return {"error": "페이지 방문 결과 파싱 실패", "raw": text_content}

            return {"error": "페이지 방문 실패 또는 결과 없음"}

        except Exception as e:
            return {"error": f"페이지 방문 중 오류 발생: {str(e)}"}

    def get_process_info(self) -> Dict:
        """프로세스 상태 정보 반환"""
        if not self.process:
            return {"status": "not_started"}

        poll_result = self.process.poll()
        return {
            "status": "running" if poll_result is None else "terminated",
            "return_code": poll_result,
            "initialized": self.initialized,
            "pid": self.process.pid if self.process else None,
        }

    def cleanup(self):
        """안전한 리소스 정리"""
        # 프로세스 종료
        if self.process:
            try:
                self.process.terminate()
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                try:
                    self.process.kill()
                except:
                    pass
            except:
                pass
            finally:
                self.process = None

        # 임시 디렉토리 삭제 (안전하게)
        if self.temp_dir_path and os.path.exists(self.temp_dir_path):
            try:
                # Windows에서 파일이 사용 중일 수 있으므로 여러 번 시도
                for _ in range(3):
                    try:
                        shutil.rmtree(self.temp_dir_path, ignore_errors=True)
                        break
                    except:
                        time.sleep(1)
            except:
                # 삭제 실패해도 계속 진행
                pass
            finally:
                self.temp_dir_path = None

        self.initialized = False
        self.available_tools = []

    def __del__(self):
        """소멸자에서 정리"""
        self.cleanup()
    
    def _check_available_tools(self):
        """사용 가능한 도구 확인"""
        tool_msg = {
            "jsonrpc": "2.0",
            "id": self.message_id,
            "method": "tools/list",
            "params": {}
        }
        
        self._send_message(tool_msg)
        
        try:
            response = self._wait_for_response(timeout=10)
            if response and "result" in response:
                tools = response["result"].get("tools", [])
                self.available_tools = [tool["name"] for tool in tools]
                print(f"✅ 사용 가능한 도구: {self.available_tools}")
            else:
                print("⚠️ 도구 목록을 가져올 수 없습니다")
        except:
            print("⚠️ 도구 목록 확인 실패")


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

    # 프로세스 상태 확인
    process_info = mcp_web_research.get_process_info()
    logging.info(f"MCP 프로세스 상태: {process_info}")

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
        error_msg = web_search_results.get('message', web_search_results['error'])
        
        # Google 검색 실패 시 LLM으로 직접 답변
        prompt = f"""사용자가 다음을 검색하려 했지만 실패했습니다: "{query}"
오류: {error_msg}

웹 검색 없이 당신의 지식으로 최선의 답변을 제공해주세요.
최신 정보가 필요한 경우 그 사실을 언급하고 대안을 제시해주세요."""
        
        try:
            response = client.chat.completions.create(
                model="gpt-4o-mini",
                messages=[{"role": "user", "content": prompt}],
                max_tokens=800
            )
            return response.choices[0].message.content.strip()
        except Exception as e:
            return f"웹 검색 중 오류가 발생했습니다: {error_msg}"

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
