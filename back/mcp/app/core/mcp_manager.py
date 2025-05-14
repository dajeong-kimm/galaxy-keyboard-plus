import os
import asyncio
import logging
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
            
            # package.json 확인
            pkg_json_path = os.path.join(self.web_search_path, "package.json")
            if os.path.exists(pkg_json_path):
                try:
                    import json
                    with open(pkg_json_path, 'r') as f:
                        pkg_data = json.load(f)
                    logger.info(f"Package.json main: {pkg_data.get('main', 'Not specified')}")
                    logger.info(f"Package.json scripts: {pkg_data.get('scripts', {})}")
                except Exception as e:
                    logger.error(f"Error reading package.json: {str(e)}")
            
            # 가능한 JavaScript 진입점 경로 확인
            dist_path = os.path.join(self.web_search_path, "dist", "index.js")
            lib_path = os.path.join(self.web_search_path, "lib", "index.js")
            build_path = os.path.join(self.web_search_path, "build", "index.js")
            root_path = os.path.join(self.web_search_path, "index.js")
            
            # 존재하는 경로 확인
            paths_to_check = [dist_path, lib_path, build_path, root_path]
            valid_paths = [p for p in paths_to_check if os.path.exists(p)]
            
            if valid_paths:
                js_path = valid_paths[0]
                logger.info(f"Using JavaScript entry point: {js_path}")
                cmd = f"cd {self.web_search_path} && node {js_path} --port {self.web_search_port}"
            else:
                # 빌드된 파일이 없으면 src/index.ts 직접 실행 시도
                logger.warning("No compiled JavaScript file found, attempting to run TypeScript directly")
                ts_path = os.path.join(self.web_search_path, "src", "index.ts")
                if os.path.exists(ts_path):
                    cmd = f"cd {self.web_search_path} && npx ts-node {ts_path} --port {self.web_search_port}"
                else:
                    logger.error("Neither JavaScript nor TypeScript entry point found!")
                    return False
            
            logger.info(f"Executing command: {cmd}")
            
            process = await asyncio.create_subprocess_shell(
                cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE
            )
            
            # 비동기적으로 표준 출력/오류 로깅
            asyncio.create_task(self._log_stream(process.stdout, f"{server_name} stdout"))
            asyncio.create_task(self._log_stream(process.stderr, f"{server_name} stderr"))
            
            # 프로세스 상태 확인
            await asyncio.sleep(2)
            if process.returncode is not None:
                logger.error(f"Server process exited immediately with code {process.returncode}")
                return False
            
            self.servers[server_name] = process
            logger.info(f"Started web_search MCP server with PID: {process.pid} on port {self.web_search_port}")
            return True
        
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
                process.terminate()
                try:
                    await asyncio.wait_for(process.wait(), timeout=5.0)
                    logger.info(f"Server {name} terminated gracefully")
                except asyncio.TimeoutError:
                    logger.warning(f"Server {name} did not terminate gracefully, killing process")
                    process.kill()
                logger.info(f"Stopped {name} MCP server")
        self.servers.clear()
    
    async def initialize_client(self, server_name: str) -> Optional[MCPClient]:
        """MCP 클라이언트 초기화"""
        if server_name == "web_search":
            server_url = f"http://localhost:{self.web_search_port}"
            logger.info(f"Initializing client for {server_name} at {server_url}")
            
            client = MCPClient(server_name, server_url)
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
        
        return None
    
    def get_server_status(self, server_name: str = None) -> Dict[str, Any]:
        """서버 상태 정보 반환"""
        if server_name:
            # 특정 서버 상태만 반환
            if server_name not in self.servers:
                logger.warning(f"Server not found: {server_name}")
                return {"error": f"Server not found: {server_name}"}
            
            server = self.servers[server_name]
            # 서버 프로세스 상태 확인 및 로깅
            if server is None:
                logger.error(f"Server {server_name} process is None")
                status = "stopped"
            elif server.returncode is not None:
                logger.error(f"Server {server_name} process exited with code {server.returncode}")
                status = "stopped"
            else:
                try:
                    # 프로세스가 실제로 실행 중인지 확인 (플랫폼에 따라 다름)
                    os.kill(server.pid, 0)  # 신호를 보내지 않고 프로세스 존재 여부만 확인
                    status = "running"
                    logger.info(f"Server {server_name} with PID {server.pid} is running")
                except OSError:
                    logger.error(f"Server {server_name} with PID {server.pid} is not running despite having no returncode")
                    status = "stopped"
                except Exception as e:
                    logger.error(f"Error checking process status: {str(e)}")
                    status = "unknown"
            
            return {
                "name": server_name,
                "status": status,
                "port": self.web_search_port if server_name == "web_search" else None,
                "client_connected": server_name in self.clients
            }
        else:
            # 모든 서버 상태 반환
            result = {}
            for name, server in self.servers.items():
                status_info = self.get_server_status(name)
                result[name] = status_info
            
            return result