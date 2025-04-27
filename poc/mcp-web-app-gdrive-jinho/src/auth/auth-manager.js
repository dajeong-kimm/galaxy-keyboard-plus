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
let redirectServer = null;

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
            // 이벤트 emit
            redirectServer.emit('code', code);
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
  if (redirectServer) {
    redirectServer.close();
    redirectServer = null;
    console.log("리디렉션 서버가 종료되었습니다.");
  }
}

// Google OAuth 인증 처리 함수
async function doGoogleOAuth() {
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
      const authWindow = new BrowserWindow({
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
          
          // 인증 창 닫기
          authWindow.close();
          resolve({ tokens });
        } catch (err) {
          console.error("토큰 획득 실패:", err.message);
          if (err.response?.data) {
            console.error("오류 세부정보:", JSON.stringify(err.response.data, null, 2));
          }
          authWindow.close();
          reject(err);
        }
      });
      
      // 창 닫힘 처리
      authWindow.on("closed", () => {
        if (!fs.existsSync(OAUTH_PATH)) {
          stopRedirectServer();
          reject(new Error("인증 창이 닫혔습니다. 인증이 완료되지 않았습니다."));
        }
      });
      
    } catch (err) {
      stopRedirectServer();
      console.error("OAuth 초기화 오류:", err.message);
      reject(err);
    }
  });
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
  cleanup
};