import os
import asyncio
import logging
import signal
import time
from typing import Dict, Optional
from app.core.mcp_client import MCPClient
from typing import Dict, List, Optional, Any 

logger = logging.getLogger(__name__)

class MCPManager:
    """MCP 서버 관리 클래스"""
    
    def __init__(self):
        self.servers = {}  # 서버 프로세스
        self.clients = {}  # 클라이언트
        self.web_search_port = 8100
        self.web_search_path = os.path.join(
            os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
            "mcp_servers/web_search"
        )
    
    async def start_server(self, server_name: str) -> bool:
        """MCP 서버 시작"""
        if server_name == "web_search":
            logger.info(f"Starting web_search server from path: {self.web_search_path}")
            
            # build/index.js 파일 존재 확인
            build_path = os.path.join(self.web_search_path, "build", "index.js")
            if not os.path.exists(build_path):
                logger.error(f"Build file does not exist: {build_path}")
                return False
            
            # JavaScript 진입점 확인
            logger.info(f"Found JavaScript entry point: {build_path}")
            
            # 이미 실행 중인 프로세스 확인 및 종료
            if server_name in self.servers and self.servers[server_name] is not None:
                old_process = self.servers[server_name]
                try:
                    logger.info(f"Terminating existing process with PID: {old_process.pid}")
                    old_process.terminate()
                    await asyncio.wait_for(old_process.wait(), timeout=5.0)
                except Exception as e:
                    logger.error(f"Error terminating existing process: {str(e)}")
                    try:
                        old_process.kill()
                    except:
                        pass
            
            # 8100 포트 확인 (중복 사용 가능성)
            try:
                import socket
                sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                result = sock.connect_ex(('localhost', self.web_search_port))
                if result == 0:
                    logger.warning(f"Port {self.web_search_port} is already in use. Attempting to free it.")
                    # 가능한 경우 lsof 또는 다른 방법으로 포트 사용 중인 프로세스 종료 시도
                sock.close()
            except Exception as e:
                logger.error(f"Error checking port availability: {str(e)}")
            
            # Node.js 실행 명령어
            cmd = f"cd {self.web_search_path} && node {build_path} --port {self.web_search_port}"
            logger.info(f"Executing command: {cmd}")
            
            # 환경 변수 설정
            env = os.environ.copy()
            env["NODE_DEBUG"] = "net,http,fs"  # 자세한 Node.js 디버깅
            
            # 프로세스 시작
            try:
                process = await asyncio.create_subprocess_shell(
                    cmd,
                    stdout=asyncio.subprocess.PIPE,
                    stderr=asyncio.subprocess.PIPE,
                    env=env
                )
                
                # 비동기적으로 표준 출력/오류 로깅
                asyncio.create_task(self._log_stream(process.stdout, f"{server_name} stdout"))
                asyncio.create_task(self._log_stream(process.stderr, f"{server_name} stderr"))
                
                # 프로세스 상태 확인을 위해 잠시 대기
                await asyncio.sleep(2)
                
                if process.returncode is not None:
                    logger.error(f"Server process exited immediately with code {process.returncode}")
                    return False
                
                # 서버 프로세스 등록
                self.servers[server_name] = process
                logger.info(f"Started web_search MCP server with PID: {process.pid} on port {self.web_search_port}")
                
                # 서버 시작 후 추가 검증
                for _ in range(5):  # 5번 시도
                    try:
                        import aiohttp
                        async with aiohttp.ClientSession() as session:
                            async with session.get(f"http://localhost:{self.web_search_port}", timeout=1) as response:
                                logger.info(f"Server connection verified with status: {response.status}")
                                return True
                    except Exception as e:
                        logger.warning(f"Server connection attempt failed: {str(e)}")
                        await asyncio.sleep(1)  # 1초 대기 후 재시도
                
                # 연결 시도 실패했지만 프로세스는 실행 중
                if process.returncode is None:
                    logger.warning("Server process is running but connection attempts failed. Proceeding anyway.")
                    return True
                else:
                    logger.error(f"Server process exited with code {process.returncode} during connection attempts")
                    return False
                
            except Exception as e:
                logger.error(f"Error starting server process: {str(e)}")
                return False
        
        return False
    
    async def _log_stream(self, stream, prefix):
        """스트림에서 비동기적으로 라인을 읽고 로깅"""
        while True:
            try:
                line = await stream.readline()
                if not line:
                    logger.info(f"{prefix}: Stream closed")
                    break
                logger.info(f"{prefix}: {line.decode().strip()}")
            except Exception as e:
                logger.error(f"Error reading {prefix}: {str(e)}")
                break
    
    async def stop_all_servers(self):
        """모든 MCP 서버 종료"""
        # 클라이언트 종료
        for client in list(self.clients.values()):
            await client.close()
        self.clients.clear()
        
        # 서버 프로세스 종료
        for name, process in list(self.servers.items()):
            if process:
                logger.info(f"Stopping {name} MCP server with PID: {process.pid}")
                try:
                    process.terminate()
                    try:
                        await asyncio.wait_for(process.wait(), timeout=5.0)
                        logger.info(f"Server {name} terminated gracefully")
                    except asyncio.TimeoutError:
                        logger.warning(f"Server {name} did not terminate gracefully, killing process")
                        process.kill()
                except Exception as e:
                    logger.error(f"Error stopping server process: {str(e)}")
                logger.info(f"Stopped {name} MCP server")
        self.servers.clear()
    
    async def initialize_client(self, server_name: str) -> Optional[MCPClient]:
        """MCP 클라이언트 초기화"""
        if server_name == "web_search":
            server_url = f"http://localhost:{self.web_search_port}"
            logger.info(f"Initializing client for {server_name} at {server_url}")
            
            client = MCPClient(server_name, server_url)
            try:
                await client.initialize()
                
                # 서버 연결 테스트
                try:
                    import aiohttp
                    async with aiohttp.ClientSession() as session:
                        try:
                            async with session.get(server_url, timeout=5) as response:
                                logger.info(f"Server connection test result: {response.status}")
                                if response.status != 200:
                                    logger.warning(f"Server returned non-200 status: {response.status}")
                        except Exception as e:
                            logger.error(f"Server connection test failed: {str(e)}")
                except Exception as e:
                    logger.error(f"Error during connection test: {str(e)}")
                
                self.clients[server_name] = client
                logger.info(f"Initialized client for {server_name} MCP server")
                return client
            except Exception as e:
                logger.error(f"Error initializing client: {str(e)}")
                return None
        
        return None
    
    def get_server_status(self, server_name: str = None) -> Dict[str, Any]:
        """서버 상태 정보 반환"""
        if server_name:
            # 특정 서버 상태만 반환
            if server_name not in self.servers:
                logger.warning(f"Server not found: {server_name}")
                return {"error": f"Server not found: {server_name}"}
            
            server = self.servers[server_name]
            status = "running" if server is not None and server.returncode is None else "stopped"
            
            # 추가 로깅
            if server is None:
                logger.warning(f"Server {server_name} process is None")
            elif server.returncode is not None:
                logger.warning(f"Server {server_name} process exited with code {server.returncode}")
            else:
                logger.info(f"Server {server_name} process is running with PID {server.pid}")
            
            return {
                "name": server_name,
                "status": status,
                "port": self.web_search_port if server_name == "web_search" else None,
                "client_connected": server_name in self.clients
            }
        else:
            # 모든 서버 상태 반환
            result = {}
            for name in list(self.servers.keys()):
                result[name] = self.get_server_status(name)
            
            # 서버 목록이 비어있다면 web_search 서버 정보 추가
            if not result and "web_search" not in result:
                result["web_search"] = {
                    "name": "web_search",
                    "status": "stopped",
                    "port": self.web_search_port,
                    "client_connected": "web_search" in self.clients
                }
            
            return result