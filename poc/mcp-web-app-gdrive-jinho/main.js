// main.js
require("dotenv").config();
const { app, BrowserWindow, ipcMain, dialog } = require("electron");
const path = require("path");
const { spawnAllServers } = require("./src/mcp/server-manager");
const { runCommand } = require("./src/mcp/mcp-client");
const { selectProjectFolder } = require("./src/mcp/server-manager");
const { isAuthenticated, doGoogleOAuth, clearAuthentication, cleanup } = require("./src/auth/auth-manager");

// 메인 윈도우 참조 유지
let mainWindow;

// 앱 준비 완료 시 윈도우 생성
app.whenReady().then(createWindow);

// 메인 윈도우 생성 함수
function createWindow() {
  // 메인 윈도우 생성
  mainWindow = new BrowserWindow({
    width: 1200, 
    height: 800,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
    },
    title: "MCP 데스크톱 앱"
  });
  
  // HTML 파일 로드
  mainWindow.loadFile(path.join(__dirname, "renderer", "index.html"));

  // 개발 환경에서는 개발자 도구 표시
  if (process.env.NODE_ENV === "development") {
    mainWindow.webContents.openDevTools();
  }

  // 렌더러 로드 완료 후 초기화 실행
  mainWindow.webContents.once("did-finish-load", async () => {
    try {
      // 1) Google 인증 확인 및 진행
      const authStatus = await handleAuthentication();
      
      // 인증에 실패한 경우 초기화 중단
      if (!authStatus) {
        mainWindow.webContents.send("auth-error", "Google 인증에 실패했습니다. 다시 시도해주세요.");
        return;
      }
      
      // 2) 인증 성공 시 MCP 서버 시작
      try {
        await spawnAllServers();
        mainWindow.webContents.send("servers-ready", "MCP 서버가 준비되었습니다.");
      } catch (serverErr) {
        console.error("서버 시작 오류:", serverErr);
        mainWindow.webContents.send("server-error", `서버 시작 오류: ${serverErr.message}`);
      }
    } catch (err) {
      console.error("초기화 중 오류 발생:", err);
      dialog.showErrorBox("초기화 오류", `앱 초기화 중 오류가 발생했습니다: ${err.message}`);
    }
  });
}

// 인증 처리 함수
async function handleAuthentication() {
  try {
    // 이미 인증된 경우
    if (await isAuthenticated()) {
      console.log("이미 인증되어 있습니다.");
      return true;
    }
    
    // 인증 필요한 경우
    console.log("Google 인증이 필요합니다. 인증 창을 엽니다...");
    if (mainWindow) {
      mainWindow.webContents.send("auth-status", "Google 인증을 진행합니다...");
    }
    
    // 인증 진행
    await doGoogleOAuth();
    
    // 인증 성공 메시지
    if (mainWindow) {
      mainWindow.webContents.send("auth-status", "Google 인증이 완료되었습니다.");
    }
    return true;
  } catch (err) {
    console.error("인증 오류:", err.message);
    if (mainWindow) {
      mainWindow.webContents.send("auth-error", `인증 오류: ${err.message}`);
    }
    return false;
  }
}

// 프로젝트 폴더 선택 IPC 핸들러
ipcMain.handle("select-folder", async () => {
  try {
    return await selectProjectFolder();
  } catch (err) {
    console.error("폴더 선택 오류:", err);
    return null;
  }
});

// 명령 실행 IPC 핸들러
ipcMain.handle("run-command", async (_e, text) => {
  if (!text) {
    return { error: "명령을 입력하세요." };
  }
  
  try {
    return await runCommand(text);
  } catch (err) {
    console.error("명령 실행 오류:", err);
    return { error: `명령 실행 중 오류가 발생했습니다: ${err.message}` };
  }
});

// 인증 재시도 IPC 핸들러
ipcMain.handle("retry-auth", async () => {
  try {
    // 기존 인증 정보 삭제
    clearAuthentication();
    // 인증 다시 시도
    return await handleAuthentication();
  } catch (err) {
    console.error("인증 재시도 오류:", err);
    return false;
  }
});

// 앱 종료 시 서버 종료 및 리소스 정리
app.on("will-quit", () => {
  try {
    // 서버 종료
    require("./src/mcp/server-manager").killAllServers();
    console.log("모든 서버가 종료되었습니다.");
    
    // 인증 리소스 정리
    cleanup();
  } catch (err) {
    console.error("정리 작업 중 오류:", err);
  }
});

// 모든 창이 닫혔을 때 앱 종료 (macOS 제외)
app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});

// macOS에서 앱 아이콘 클릭 시 창 다시 생성
app.on("activate", () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow();
  }
});