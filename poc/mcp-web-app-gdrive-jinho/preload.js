// preload.js
const { contextBridge, ipcRenderer } = require('electron');

// 렌더러 프로세스(웹 콘텐츠)에 노출할 API 정의
contextBridge.exposeInMainWorld('mcpApi', {
  // 프로젝트 폴더 선택 - 이름 변경된 IPC 핸들러에 맞춤
  selectFolder: () => ipcRenderer.invoke('select-folder'),
  
  // 명령 실행 - 이름 변경된 IPC 핸들러에 맞춤
  runCommand: (text) => ipcRenderer.invoke('run-command', text),
  
  // 인증 재시도
  retryAuth: () => ipcRenderer.invoke('retry-auth'),
  
  // 서버 재시작 (추가)
  restartServers: () => ipcRenderer.invoke('restart-servers'),
  
  // 이벤트 리스너
  onAuthStatus: (callback) => {
    const listener = (_event, message) => callback(message);
    ipcRenderer.on('auth-status', listener);
    return () => ipcRenderer.removeListener('auth-status', listener);
  },
  
  onAuthError: (callback) => {
    const listener = (_event, error) => callback(error);
    ipcRenderer.on('auth-error', listener);
    return () => ipcRenderer.removeListener('auth-error', listener);
  },
  
  onServersReady: (callback) => {
    const listener = (_event, message) => callback(message);
    ipcRenderer.on('servers-ready', listener);
    return () => ipcRenderer.removeListener('servers-ready', listener);
  },
  
  onServerError: (callback) => {
    const listener = (_event, error) => callback(error);
    ipcRenderer.on('server-error', listener);
    return () => ipcRenderer.removeListener('server-error', listener);
  }
});

// 콘솔에 로딩 메시지 표시
console.log('Electron preload script 로드 완료');