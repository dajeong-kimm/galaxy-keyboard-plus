// src/auth/auth-manager.js
const { google } = require("googleapis");
const { BrowserWindow, session } = require("electron");
const fs = require("fs");
const path = require("path");
const http = require("http");
const { URL } = require("url");
require("dotenv").config();

// 인증 정보를 저장할 경로
const OAUTH_PATH = path.resolve(__dirname, "../../oauth/gcp-oauth.keys.json");
// MCP 서버용 OAuth 경로 (env 파일에서 가져옴)
const MCP_OAUTH_PATH = path.resolve(process.cwd(), process.env.GDRIVE_OAUTH_PATH || "./oauth/gcp-oauth.keys.json");

let redirectServer = null;
let authWindowClosed = false;

// OAuth 클라이언트 생성 함수
function getOAuth2Client() {
  const client_id = process.env.GCP_CLIENT_ID;
  const client_secret = process.env.GCP_CLIENT_SECRET;
  const redirect_uri = process.env.GCP_REDIRECT_URI;
  
  console.log("OAuth 클라이언트 설정 확인:", {
    client_id: client_id ? `${client_id.substring(0, 15)}...` : undefined,
    client_secret: client_secret ? `${client_secret.substring(0, 5)}...` : undefined,
    redirect_uri
  });
  
  if (!client_id || !client_secret || !redirect_uri) {
    throw new Error("환경 변수에 OAuth 설정 정보가 부족합니다.");
  }
  
  return new google.auth.OAuth2(client_id, client_secret, redirect_uri);
}

// OAuth 인증 정보를 MCP 서버 형식으로 변환하여 저장
function convertOAuthForMCPServer() {
  try {
    if (fs.existsSync(OAUTH_PATH)) {
      const content = fs.readFileSync(OAUTH_PATH, 'utf8');
      const data = JSON.parse(content);
      
      // tokens 객체가 직접 저장되었거나 credentials.tokens로 저장되었을 수 있음
      const tokens = data.tokens || data;
      
      if (tokens && (tokens.access_token || tokens.refresh_token)) {
        // MCP 서버에서 필요로 하는 형식으로 변환
        const mcpOAuthData = {
          "installed": {
            "client_id": process.env.GCP_CLIENT_ID,
            "client_secret": process.env.GCP_CLIENT_SECRET,
            "redirect_uris": ["http://localhost:3000/oauth2callback"],
            "auth_uri": "https://accounts.google.com/o/oauth2/auth",
            "token_uri": "https://oauth2.googleapis.com/token"
          },
          "refresh_token": tokens.refresh_token,
          "access_token": tokens.access_token,
          "expiry_date": tokens.expiry_date || Date.now() + 3600000
        };
        
        // 디렉토리 생성
        fs.mkdirSync(path.dirname(MCP_OAUTH_PATH), { recursive: true });
        
        // 파일 저장
        fs.writeFileSync(MCP_OAUTH_PATH, JSON.stringify(mcpOAuthData, null, 2));
        console.log(`MCP 서버용 OAuth 정보 저장 완료: ${MCP_OAUTH_PATH}`);
        return true;
      }
    }
    console.log("변환할 OAuth 정보를 찾을 수 없습니다.");
    return false;
  } catch (err) {
    console.error("OAuth 정보 변환 중 오류:", err.message);
    return false;
  }
}

// 인증 상태 확인 함수
function isAuthenticated() {
  try {
    if (fs.existsSync(OAUTH_PATH)) {
      const content = fs.readFileSync(OAUTH_PATH, 'utf8');
      const data = JSON.parse(content);
      
      // tokens 객체가 직접 저장되었거나 credentials.tokens로 저장되었을 수 있음
      const tokens = data.tokens || data;
      
      if (tokens && (tokens.access_token || tokens.refresh_token)) {
        console.log("인증 정보 존재함:", OAUTH_PATH);
        
        // MCP 서버용 파일이 없으면 변환
        if (!fs.existsSync(MCP_OAUTH_PATH)) {
          console.log("MCP 서버용 OAuth 파일이 없습니다. 변환합니다...");
          convertOAuthForMCPServer();
        }
        
        return true;
      }
    }
    console.log("인증 정보가 없거나 유효하지 않음:", OAUTH_PATH);
    return false;
  } catch (err) {
    console.error("인증 정보 확인 중 오류:", err.message);
    return false;
  }
}

// 리디렉션 서버 시작 함수
function startRedirectServer() {
  return new Promise((resolve, reject) => {
    try {
      // URL에서 포트 번호 추출
      const redirectUrl = new URL(process.env.GCP_REDIRECT_URI);
      const port = parseInt(redirectUrl.port) || 3000;
      
      // 이미 서버가 실행 중이면 종료
      if (redirectServer) {
        redirectServer.close();
      }
      
      // 리디렉션 처리할 HTTP 서버 생성
      redirectServer = http.createServer((req, res) => {
        const url = new URL(req.url, `http://${req.headers.host}`);
        
        if (url.pathname === '/oauth2callback') {
          // 응답 설정
          res.writeHead(200, { 'Content-Type': 'text/html' });
          res.end(`
            <html>
              <body>
                <h1>인증 완료</h1>
                <p>이 창은 닫으셔도 됩니다.</p>
                <script>window.close();</script>
              </body>
            </html>
          `);
          
          // 코드 파라미터 추출
          const code = url.searchParams.get('code');
          
          if (code) {
            // 이벤트 emit - 비동기로 처리하고 즉시 반환
            setImmediate(() => {
              redirectServer.emit('code', code);
            });
          }
        }
      });
      
      // 서버 시작
      redirectServer.listen(port, () => {
        console.log(`리디렉션 서버가 포트 ${port}에서 시작되었습니다.`);
        resolve(redirectServer);
      });
      
      // 오류 처리
      redirectServer.on('error', (err) => {
        if (err.code === 'EADDRINUSE') {
          console.error(`포트 ${port}가 이미 사용 중입니다.`);
          reject(new Error(`포트 ${port}가 이미 사용 중입니다. 다른 포트를 사용하세요.`));
        } else {
          reject(err);
        }
      });
    } catch (err) {
      reject(err);
    }
  });
}

// 리디렉션 서버 종료 함수
function stopRedirectServer() {
  if (redirectServer && redirectServer.listening) {
    try {
      redirectServer.close();
      console.log("리디렉션 서버가 종료되었습니다.");
    } catch (err) {
      console.error("리디렉션 서버 종료 중 오류:", err.message);
    }
    redirectServer = null;
  }
}

// Google OAuth 인증 처리 함수
async function doGoogleOAuth() {
  let authWindow = null;
  authWindowClosed = false;
  
  try {
    // 리디렉션 서버 시작
    await startRedirectServer();
    
    return new Promise((resolve, reject) => {
      try {
        const oauth2Client = getOAuth2Client();
        
        // 인증 URL 생성
        const authUrl = oauth2Client.generateAuthUrl({
          access_type: "offline",
          scope: ["https://www.googleapis.com/auth/drive.readonly"],
          prompt: "consent",
          include_granted_scopes: true
        });
        
        console.log("Google OAuth 인증 시작...");
        console.log("인증 URL:", authUrl);
        
        // 인증 창 생성
        authWindow = new BrowserWindow({
          width: 600, 
          height: 700,
          webPreferences: { 
            nodeIntegration: false, 
            contextIsolation: true
          },
          title: "Google 계정 로그인"
        });
        
        // 인증 URL 로드
        authWindow.loadURL(authUrl);
        
        // 코드 수신 이벤트 처리
        redirectServer.once('code', async (code) => {
          console.log("인증 코드 수신:", `${code.substring(0, 10)}...`);
          
          try {
            // 토큰 교환
            const { tokens } = await oauth2Client.getToken(code);
            console.log("토큰 획득 성공!");
            
            // 인증 정보 저장
            fs.mkdirSync(path.dirname(OAUTH_PATH), { recursive: true });
            fs.writeFileSync(OAUTH_PATH, JSON.stringify({ tokens }, null, 2));
            console.log("인증 정보 저장 완료:", OAUTH_PATH);
            
            // MCP 서버용으로 변환
            convertOAuthForMCPServer();
            
            // 창이 이미 닫히지 않았다면 닫기
            if (authWindow && !authWindow.isDestroyed()) {
              authWindow.close();
            }
            
            // 서버 정리는 resolve 전에
            stopRedirectServer();
            resolve({ tokens });
          } catch (err) {
            console.error("토큰 획득 실패:", err.message);
            if (err.response?.data) {
              console.error("오류 세부정보:", JSON.stringify(err.response.data, null, 2));
            }
            
            // 창이 이미 닫히지 않았다면 닫기
            if (authWindow && !authWindow.isDestroyed()) {
              authWindow.close();
            }
            
            // 서버 정리는 reject 전에
            stopRedirectServer();
            reject(err);
          }
        });
        
        // 창 닫힘 처리
        authWindow.on("closed", () => {
          authWindowClosed = true;
          
          // 이미 인증이 완료되었는지 확인
          if (fs.existsSync(OAUTH_PATH)) {
            // 인증이 이미 완료되었으면 서버만 정리
            stopRedirectServer();
          } else {
            // 인증이 완료되지 않았으면 에러 발생
            stopRedirectServer();
            reject(new Error("인증 창이 닫혔습니다. 인증이 완료되지 않았습니다."));
          }
        });
        
      } catch (err) {
        // 창이 이미 닫히지 않았다면 닫기
        if (authWindow && !authWindow.isDestroyed()) {
          authWindow.close();
        }
        
        stopRedirectServer();
        console.error("OAuth 초기화 오류:", err.message);
        reject(err);
      }
    });
  } catch (err) {
    stopRedirectServer();
    
    // 창이 이미 닫히지 않았다면 닫기
    if (authWindow && !authWindow.isDestroyed()) {
      authWindow.close();
    }
    
    throw err;
  }
}

// 인증 정보 강제 삭제 함수
function clearAuthentication() {
  if (fs.existsSync(OAUTH_PATH)) {
    fs.unlinkSync(OAUTH_PATH);
    console.log("인증 정보가 삭제되었습니다.");
    return true;
  }
  return false;
}

// 앱 종료 시 리소스 정리
function cleanup() {
  stopRedirectServer();
}

module.exports = { 
  isAuthenticated, 
  doGoogleOAuth, 
  clearAuthentication,
  cleanup,
  convertOAuthForMCPServer
};