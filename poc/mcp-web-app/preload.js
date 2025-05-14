/****************************************************************
 *  Preload 스크립트
 *
 *  ▸ Renderer(웹페이지)와 Main 프로세스 사이에 ‘보호된’ 다리 역할
 *    (contextIsolation: true 일 때만 preload 가 존재)
 *
 *  ▸ exposeInMainWorld 로 최소 API 만 노출
 *      • selectProjectFolder() – 폴더 선택 다이얼로그
 *      • sendCommand(text)     – 자연어 명령 실행
 *
 *  ▸ 보안 이유로 Node.js 객체를 직접 넘기지 않는다.
 ****************************************************************/

const { contextBridge, ipcRenderer } = require("electron");

/* ───────────── CSP(Nonce) 헬퍼 ─────────────
   Renderer HTML 은 <script nonce="__NONCE__"> 로 삽입되고,
   여기서 생성한 난수 값을 전역 window.__nonce 로 노출해
   index.html 의 Content-Security-Policy 와 일치시킨다.
   (optional – 필수가 아니면 삭제 OK)
────────────────────────────────────────── */
const nonce = crypto.randomUUID();
process.once("loaded", () => {
  Object.defineProperty(window, "__nonce", { value: nonce });
});

/* ───────────── IPC 브리지 ─────────────
   Main.process 의 ipcMain.handle() 과 짝을 이룬다.
   Renderer 에선 window.api.* 로 접근
────────────────────────────────────────── */
contextBridge.exposeInMainWorld("api", {
  /* 프로젝트 루트 폴더 선택 다이얼로그 호출 */
  selectProjectFolder: () => ipcRenderer.invoke("select-folder"),

  /* 자연어 명령 → MCP 실행 → 결과 반환 */
  sendCommand: (text) => ipcRenderer.invoke("run-command", text),
});
