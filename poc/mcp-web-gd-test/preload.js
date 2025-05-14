// preload.js
const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("api", {
  // 자연어 명령 실행
  sendCommand: (text) => ipcRenderer.invoke("run-command", text),
  // Google Drive OAuth 인증
  googleAuth: () => ipcRenderer.invoke("google-auth"),
  // (선택적) 프로젝트 폴더 선택
  selectProjectFolder: () => ipcRenderer.invoke("select-folder")
});
