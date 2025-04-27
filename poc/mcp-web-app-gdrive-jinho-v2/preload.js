// preload.js
const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("api", {
  // 자연어 명령 실행
  sendCommand: (text) => ipcRenderer.invoke("run-command", text),
  // 구글 인증 트리거
  googleAuth: () => ipcRenderer.invoke("google-auth")
});
