# 2025-04-27 (Google Calendar)

## MCP 서버 분석

### 정보

- https://github.com/nspady/google-calendar-mcp
    
    
    | 항목 | 설명 |
    | --- | --- |
    | 인증 | Google OAuth 2.0 |
    | 주요 기능 | Google Calendar 일정 조회, 생성, 수정, 삭제 (MCP 포맷 준수) |
    | 설치 방식 | 로컬 Node.js 서버로 실행 (`npm install`, `npm run dev`) |
    | 환경설정 | `.env` 파일에 Google OAuth 정보 입력해야 함 |
    | 특징 | 매우 가볍고, 기본적인 Calendar 조작 기능을 지원 |
- 흐름
    
    ```jsx
    1. 프로젝트 클론
    2. Node.js 설치
    3. npm install
    4. GCP에서 OAuth Client 발급
    5. .env 파일 작성
    6. npm run dev
    7. 브라우저에서 OAuth 로그인
    8. Access Token 획득
    9. MCP API 요청 보내서 구글 캘린더 조작
    ```
    

### 사용

- **프로젝트 클론**

먼저 GitHub에서 코드를 클론해와야 해.

```bash
git clone https://github.com/nspady/google-calendar-mcp.git
cd google-calendar-mcp
```

---

- 의존성 설치 (`express`, `axios`, `dotenv` 등)

---

- **Google OAuth 2.0 Client 설정**

**구글 클라우드 플랫폼(GCP)에서 OAuth 2.0 Client를 직접 생성**

**순서:**

1. Google Cloud Console 접속
2. 새 프로젝트 만들기 (예: `MCP Calendar Project`)
3. API & Services > OAuth consent screen 설정
    - External(외부) 선택
    - 앱 이름, 이메일 등 작성
4. API & Services > Credentials로 이동
5. "Create Credentials" ➔ "OAuth Client ID" 생성
    - Application type은 **Web Application** 선택
    - Authorized redirect URIs에 MCP 서버 주소 추가 (예: `http://localhost:3000/oauth2callback`)

---

- .env 파일 작성

```
PORT=3000
CLIENT_ID=구글_OAUTH_CLIENT_ID
CLIENT_SECRET=구글_OAUTH_CLIENT_SECRET
REDIRECT_URI=http://localhost:3000/oauth2callback
```

※ `REDIRECT_URI`는 구글에 등록한 것과 똑같아야 함.

---

- 서버 실행

```bash
npm run dev
```

- 기본 포트는 `3000`

---

- OAuth 인증 받기
- MCP 서버를 띄운 뒤 브라우저로 이동
- `http://localhost:3000/` 접속하면 구글 로그인 창 인증
- 로그인하고 권한 허용하면 **Access Token**을 받아서 세션에 저장
- 이후부터는 Access Token을 사용해 MCP 요청을 처리

---

- MCP 방식으로 캘린더 사용

| 요청 유형 | URL 예시 | 설명 |
| --- | --- | --- |
| GET | `/calendars` | 사용자의 캘린더 목록 조회 |
| POST | `/events` | 새 이벤트 생성 |
| PATCH | `/events/:id` | 이벤트 수정 |
| DELETE | `/events/:id` | 이벤트 삭제 |

## 서비스 적용

### Google Cloud Client 설정

- Google Calendar API 사용 설정
    
    
    | 항목 | 설명 |
    | --- | --- |
    | 1. **OAuth Scope** | `google-calendar-mcp`는 **Google Calendar API** 권한(`https://www.googleapis.com/auth/calendar`)을 요청해.   |
    | 2. **API 사용 설정** | **해당 GCP 프로젝트에서 Google Calendar API가 활성화** 되어 있어야 함.  |
    | 3. **Redirect URI** | `http://localhost:3000/oauth2callback` 등록되어 있어야 함. |

---

### MCP 레포지토리 Fork

- 기존의 MCP 서버는 npm 버전 없음
- package.json 수정
    - "postinstall": "node scripts/build.js"
- oauth key 받아오는 경로 수정
    - src/auth/utils.js 수정
        
        ```jsx
        // Returns the absolute path for the saved token file.
        export function getSecureTokenPath(): string {
          const homeDir = process.env.HOME || process.env.USERPROFILE || os.homedir();
          const configDir = path.join(homeDir, ".gmail-mcp");
          const tokenPath = path.join(configDir, "credentials.json"); 
          return tokenPath;
        }
        
        // Returns the absolute path for the GCP OAuth keys file.
        export function getKeysFilePath(): string {
          const homeDir = process.env.HOME || process.env.USERPROFILE || os.homedir();
          const configDir = path.join(homeDir, ".gmail-mcp"); // 통일
          const keysPath = path.join(configDir, "gcp-oauth.keys.json");
          return keysPath;
        } 
        ```
        
- 해당 레포지토리를 서비스에서 클론하여 사용

---

### 서비스에 적용

- 흐름 구분
    - google-auth
    - gmail-spawn
    - calendar-spawn
    
    ```jsx
    1. 구글 인증 버튼 누르면 google-auth invoke
    2. google-auth가 성공하면
    	2.1 gmail mcp 서버 -> gmail-spawn invoke
    	2.2 calendar mcp 서버 -> calendar-spawn invoke
    ```
    
- preload 수정
    
    ```jsx
     /* Google OAuth 인증 수행 */
      authenticateGoogle: () => ipcRenderer.invoke("oauth-auth"),
    
      // 각각 MCP 서버 스폰 요청
      requestGmailServer: () => ipcRenderer.invoke("gmail-spawn"),
      requestCalendarServer: () => ipcRenderer.invoke("calendar-spawn"),
    ```
    
- main.js 수정
    - mcp 서버 추가
    
    ```jsx
    /* mcp 서버 목록에 추가 */
    const SERVER_DEFS = [
      {
        id: "fs", // 툴 alias 접두사
        name: "Filesystem",
        bin:
          process.platform === "win32" // OS 별 실행 파일
            ? "mcp-server-filesystem.cmd"
            : "mcp-server-filesystem",
        allowedDir: process.cwd(), // 루트 디렉터리 기본값
      },
      {
        id: "gmail", // 툴 alias 접두사
        name: "Gmail",
        bin:
          process.platform === "win32"
            ? "gmail-mcp.cmd"
            : "gmail-mcp",
      },
      {
        id: "calendar",
        name: "Google Calendar",
        bin: process.platform === "win32" 
          ? "google-calendar-mcp.cmd" 
          : "google-calendar-mcp",
      }
    ];
    ```
    
    - mcp 서버 띄우기
    
    ```jsx
    // Google Calendar 서버를 띄우는 IPC 핸들러러
    ipcMain.handle("calendar-spawn", async () => {
    
      try {
        const calendarServerDef = SERVER_DEFS.find(s => s.id === "calendar");
        const calendarServerIdx = servers.findIndex((s) => s.id === "calendar");
    
        // 만약 기존 calendar 서버가 떠있으면 종료
        if (calendarServerIdx >= 0) {
          log("기존 Calendar 서버 종료");
          servers[calendarServerIdx].proc.kill();
          servers.splice(calendarServerIdx, 1);
        }
    
        const server = await spawnServer(calendarServerDef);
    
        if (server) {
          log(`Calendar 서버 스폰 성공: ${server.id}`);
          return { 
            success: true, 
            message: "Google Calendar 인증이 완료되었습니다. 이제 Calendar 기능을 사용할 수 있습니다." 
          };
        } else {
          throw new Error("Calendar 서버 시작 실패");
        }
      } catch (e) {
        err("Calendar auth failed", e);
        return { 
          success: false, 
          message: `Calendar 인증 중 오류 발생: ${e.message}`,
          error: e.message 
        };
      }
    });
    ```