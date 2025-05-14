# 코드 흐름 요약

1. **앱 시작**
   1. `main.js`가 실행되면서 `.env` 로부터 환경 변수를 읽고 Electron `app.whenReady()` 대기.
   2. 앱이 준비되면 `@modelcontextprotocol/server-filesystem` 바이너리를 **child process**로 스폰하고 `StdioRPCClient`로 JSON-RPC 스트림 연결.

2. **BrowserWindow 생성**
   - `createWindow()`에서 `renderer/index.html`을 로드하고  
     `preload.js`를 통해 `window.api`(IPC 브리지) 노출.

3. **폴더 선택(UI)**
   1. 사용자가 **[📁 폴더 선택]** 버튼 클릭 → Renderer가 `select-folder` IPC 호출.
   2. 메인 프로세스가 OS 다이얼로그로 디렉터리를 받아오고, 기존 Filesystem 서버를 `kill()` 후 **새 allowedDir**로 재스폰.

4. **자연어 명령 처리**
   1. 사용자가 메시지를 입력하면 Renderer → `run-command` IPC 전송.
   2. 메인 프로세스 `decideCall()`  
      1. 모든 MCP 툴 스키마를 포함해 **OpenAI ChatCompletion** 호출.  
      2. ▸ **툴 호출**(tool_call) vs **텍스트** 응답 판단.
      3. `'/' / '.'` 같은 경로는 `.`(루트)로 보정.

5. **툴 호출 분기**
   - **텍스트만** 오면 그대로 Renderer에 반환.  
   - **tool_call**이면:
     1. 해당 MCP 서버에 `call_tool`(또는 `tools/call`) RPC 요청.
     2. Filesystem 서버가 돌려준 `rawResult`를 추출(텍스트만 필터링).

6. **후처리 요약**
   - `rawResult`를 포함해 다시 **GPT-4o mini**에 보내  
     “한국어 한 문단”으로 요약 → Renderer로 전달.

7. **Renderer 출력**
   - 사용자 메시지·어시스턴트 응답을 채팅 뷰에 append.

8. **종료**
   - 앱 종료 시 `app.on('will-quit')`에서 모든 MCP 서버 `proc.kill()` 호출.
