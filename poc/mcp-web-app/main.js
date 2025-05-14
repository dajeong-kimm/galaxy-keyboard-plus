/****************************************************************
 *  MCP-Web-App – 메인 프로세스 진입점
 *
 *  ▸ 이 파일은 Electron ‘메인 프로세스’에서 실행된다.
 *  ▸ 역할
 *      1) Electron 윈도우 생성 및 애플리케이션 생명주기 관리
 *      2) MCP 서버(여기서는 Filesystem 서버) 스폰 & RPC 통신
 *      3) OpenAI LLM 호출 → “어떤 MCP 툴을 쓸지” 의사결정
 *      4) Renderer(브라우저) ↔ Main 간 IPC 브리지
 *
 *  ⚠️  NOTE
 *      ─ Electron 구조
 *          • Main  : Node.js 런타임, OS 자원 접근 가능
 *          • Renderer : Chromium, DOM 렌더링 / 사용자 UI
 *          • Preload  : 둘 사이를 안전하게 중재(contextIsolation)
 *
 *      ─ MCP 서버
 *          • `@modelcontextprotocol/server-filesystem` 바이너리를
 *            자식 프로세스로 띄우고, stdin/stdout을 통해 JSON-RPC 사용
 ****************************************************************/

require("dotenv").config(); // .env 로부터 환경변수 로드
const { app, BrowserWindow, ipcMain, dialog } = require("electron");
const path = require("path");
const fs = require("fs");
const spawn = require("cross-spawn"); // cross-platform child_process
const axios = require("axios"); // OpenAI REST 호출
const portfinder = require("portfinder"); // (지금은 미사용) 여유 포트 찾기
const { v4: uuid } = require("uuid"); // JSON-RPC id 생성용

/* ───────────── Logger 헬퍼 ───────────── */
const ts = () => new Date().toISOString();
const log = (...a) => console.log(ts(), "[INFO ]", ...a);
const warn = (...a) => console.warn(ts(), "[WARN ]", ...a);
const err = (...a) => console.error(ts(), "[ERROR]", ...a);

/* ───────────── StdioRPCClient 클래스 ─────────────
   MCP 서버와의 JSON-RPC 통신을 캡슐화한다.
   ▸ stdin.write() 로 요청 전송
   ▸ stdout ‘\n’ 단위로 버퍼링하여 응답 파싱
   ▸ id-Promise 매핑을 Map 으로 관리(pending)
────────────────────────────────────────── */
class StdioRPCClient {
  constructor(proc, tag) {
    this.proc = proc; // child_process 인스턴스
    this.tag = tag; // 로그 식별용 라벨
    this.pending = new Map(); // { id → {resolve, reject} }

    /* --- 데이터 수신 핸들러 등록 --- */
    this.buffer = "";
    proc.stdout.on("data", (d) => this.#onData(d));

    /* --- STDERR → 로그로 라우팅 (서버 에러/경고) --- */
    proc.stderr.on("data", (d) =>
      d
        .toString()
        .split(/\r?\n/)
        .forEach((l) => {
          if (!l) return;
          if (l.startsWith("Secure MCP") || l.startsWith("Allowed"))
            log(`[${this.tag}]`, l); // 정상 안내 메시지
          else err(`[${this.tag}!]`, l); // 실제 오류
        })
    );
    proc.on("exit", (c) => warn(`[${tag}] exited`, c));
  }

  /* stdout 버퍼 처리 – 한 줄(JSON)씩 분해하여 Promise resolve */
  #onData(chunk) {
    this.buffer += chunk.toString();
    let idx;
    while ((idx = this.buffer.indexOf("\n")) >= 0) {
      const line = this.buffer.slice(0, idx).trim();
      this.buffer = this.buffer.slice(idx + 1);
      if (!line) continue;

      try {
        const msg = JSON.parse(line); // {"jsonrpc":"2.0", id, ...}
        const p = this.pending.get(msg.id); // 대기중인 호출 찾기
        if (p) {
          this.pending.delete(msg.id);
          msg.error ? p.reject(msg.error) : p.resolve(msg.result);
        }
      } catch (e) {
        err(`[${this.tag}] broken JSON`, line);
      }
    }
  }

  /* JSON-RPC 메서드 호출 래퍼 (Promise 반환) */
  call(method, params = {}) {
    const id = uuid();
    const payload = { jsonrpc: "2.0", id, method, params };
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.proc.stdin.write(JSON.stringify(payload) + "\n");
    });
  }
}

/* ───────────── 0. MCP 서버 정의 ─────────────
   여러 서버를 선택적으로 돌릴 수 있도록 배열로 보관
   (현재는 Filesystem 서버 하나만)
   기존에 만들어져있던 서버를 설치하여 사용할 경우 서버를 설치한 뒤뒤
   SERVER_DEFS에 서버를 추가하면 실행 시 이 배열 내부를 돌면서 필요한 서버의 정보를 취득함.
────────────────────────────────────────── */
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
];

/* ───────────── 1. 런타임 상태 ───────────── */
const servers = []; // [{ id, name, proc, rpc, tools[], allowedDir }]

/* ───────────── 2. 서버 스폰 & 툴 로딩 ───────────── */
async function spawnServer(def) {
  const binPath = path.join(__dirname, "node_modules", ".bin", def.bin);
  if (!fs.existsSync(binPath)) {
    err("not found", binPath);
    return null;
  }

  log(`Spawning ${def.id}`, binPath, def.allowedDir);
  /* child_process.spawn
     stdio = [stdin, stdout, stderr] 모두 파이프로 연결 */
  const proc = spawn(binPath, [def.allowedDir], {
    cwd: def.allowedDir,
    stdio: ["pipe", "pipe", "pipe"],
  });

  const rpc = new StdioRPCClient(proc, def.id);
  const srv = { ...def, proc, rpc, tools: [] };

  await refreshTools(srv); // list_tools → API 스키마 획득
  servers.push(srv);

  /* aliasMap 은 툴 호출 이름 → {srvId, method} 매핑 */
  aliasMap.clear(); // 서버 재시작 시 새로 갱신
  return srv;
}

/* 서버에서 지원하는 툴 목록 가져와서 (서버별) 저장 */
async function refreshTools(srv) {
  try {
    // 서버 버전에 따라 list_tools 또는 tools/list 지원
    let raw;
    try {
      raw = await srv.rpc.call("list_tools");
    } catch {
      raw = await srv.rpc.call("tools/list");
    }

    // 다양한 응답 형식을 배열로 정규화
    let arr = [];
    if (Array.isArray(raw)) arr = raw;
    else if (raw?.tools) arr = raw.tools;
    else if (typeof raw === "object") arr = Object.values(raw);

    if (!arr.length) throw new Error("no tools found");

    /* name 충돌 방지를 위해 “srvid_toolname” 으로 alias 부여 */
    srv.tools = arr.map((t) => ({
      ...t,
      name: `${srv.id}_${t.name}`,
      _origMethod: t.name, // 실제 서버 측 메서드 기억
    }));

    log(
      `Tools[${srv.id}] loaded`,
      srv.tools.map((t) => t.name)
    );
  } catch (e) {
    err("tool load failed", e.message);
  }
}

/* 모든 서버의 툴 평탄화(Prompts에서 tools 필드로 넘김) */
function allTools() {
  return servers.flatMap((s) => s.tools);
}

/* OpenAI ChatGPT v2 “function calling” 스펙용 변환 */
function formatToolV2(t) {
  // aliasMap : 호출 시 역-매핑하기 위해 보관
  aliasMap.set(t.name, {
    srvId: t.name.split("_", 1)[0],
    method: t._origMethod,
  });

  return {
    type: "function",
    function: {
      name: t.name,
      description: t.description,
      parameters: t.inputSchema ||
        t.parameters || {
          type: "object",
          properties: {},
        },
    },
  };
}

const aliasMap = new Map(); // {alias → {srvId, method}}

/* ───────────── 3. OpenAI → 어떤 툴 쓸지 결정 ─────────────
   ① 유저 프롬프트, ② 서버 툴 스키마 → Chat Completions 호출
   ▸ LLM 결과가 “tool_call” 이면 RPC 실행, 아니면 텍스트 그대로
────────────────────────────────────────── */
async function decideCall(prompt) {
  const key = process.env.OPENAI_API_KEY;
  if (!key) return { type: "text", content: "OPENAI_API_KEY is not set." };

  const res = await axios.post(
    "https://api.openai.com/v1/chat/completions",
    {
      model: "gpt-4o-mini",
      messages: [
        {
          role: "system",
          content: `
You are a specialized agent that transforms user requests into calls to the registered filesystem tools, or else returns plain-text answers.

Guidelines:
1. TOOL CALL
   • If a request requires filesystem access (reading, writing, listing, etc.), emit exactly one tool call JSON with the correct tool name and all required parameters.
   • Use only the provided tool names and schemas—do not invent new tools or free-form code.
   • If the user did not specify a path (or uses "/" or "."), use the current project root directory instead.
2. TEXT RESPONSE
   • If the request can be satisfied without filesystem access, reply with natural-language text and do not call any tool.  
3. FOLLOW-UP QUESTIONS
   • If a required parameter is missing or ambiguous, ask the user a clarifying question instead of guessing.  
4. NO EXTRA VERBIAGE
   • When calling a tool, respond with strictly the function call object—no explanatory text.
   • Any human-readable explanation should only appear in plain-text responses when no tool is invoked.
`,
        },
        { role: "user", content: prompt },
      ],
      tools: allTools().map(formatToolV2),
      tool_choice: "auto",
      max_tokens: 1024,
    },
    { headers: { Authorization: `Bearer ${key}` } }
  );

  log("[LLM] raw response:", JSON.stringify(res.data, null, 2));
  const msg = res.data.choices[0].message;

  /* ─ OpenAI 2024 이후 포맷: message.tool_calls[] ─ */
  let fc = null;
  if (Array.isArray(msg.tool_calls) && msg.tool_calls.length)
    fc = msg.tool_calls[0].function;
  /* ─ 레거시(v1) 포맷: function_call ─ */ else if (msg.function_call)
    fc = msg.function_call;

  // 툴 호출이 없으면 텍스트 응답
  if (!fc || !fc.arguments) return { type: "text", content: msg.content ?? "" };

  // 툴 인자 JSON 파싱
  let parsed;
  try {
    parsed = JSON.parse(fc.arguments);
  } catch {
    err("Failed to parse tool arguments:", fc.arguments);
    return { type: "text", content: msg.content ?? "" };
  }

  const alias = fc.name; // e.g. fs_directory_tree
  const params = parsed.params || parsed; // (서버 마다 다름)

  /* ───── 경로 보정 ─────
    LLM이 '/'·'.'·'' 같이 루트 의미로 응답하면
    MCP 서버 쪽엔 '.'(allowedDir)로 넘겨서
    "허용된 디렉터리 바깥" 오류를 방지 */
  if (typeof params.path === "string") {
    const p = params.path.trim();
    if (p === "/" || p === "\\" || p === "." || p === "") {
      params.path = "."; // Filesystem 서버는 '.'을 프로젝트 루트로 해석
    }
  }

  const map = aliasMap.get(alias);
  if (!map) {
    err("Unmapped tool alias:", alias);
    return { type: "text", content: msg.content ?? "" };
  }

  // RPC 실행 정보 반환
  return {
    type: "rpc",
    srvId: map.srvId,
    method: map.method,
    params,
  };
}

/* ───────────── 4. Electron 윈도우 생성 ───────────── */
let mainWindow;
function createWindow() {
  log("createWindow");
  mainWindow = new BrowserWindow({
    width: 1000,
    height: 700,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"), // contextBridge 코드
      contextIsolation: true, // Renderer → Main 완전 격리
    },
  });
  mainWindow.loadFile(path.join(__dirname, "renderer", "index.html"));
}

/* ───────────── 5. IPC 라우팅 ─────────────
   Renderer → Main
     'select-folder' : 폴더 선택 다이얼로그 열기
     'run-command'   : 사용자 자연어 명령 처리
────────────────────────────────────────── */
ipcMain.handle("select-folder", async () => {
  // ① OS 폴더 선택 UI
  const r = await dialog.showOpenDialog({ properties: ["openDirectory"] });
  if (r.canceled) return null;

  const dir = r.filePaths[0];
  log("folder selected", dir);

  /* ② 기존 fs 서버 종료 → 새 allowedDir 로 재시작 */
  const idx = servers.findIndex((s) => s.id === "fs");
  if (idx >= 0) {
    servers[idx].proc.kill();
    servers.splice(idx, 1);
  }
  await spawnServer({ ...SERVER_DEFS[0], allowedDir: dir });
  return dir;
});

ipcMain.handle("run-command", async (_e, prompt) => {
  log("[IPC] run-command", prompt);
  try {
    /* ① LLM에 의사결정 위임 */
    const d = await decideCall(prompt);
    if (d.type === "text") return { result: d.content }; // 툴 불필요

    /* ② RPC 실행 대상 서버 탐색 */
    const srv = servers.find((s) => s.id === d.srvId);
    if (!srv) throw new Error(`server ${d.srvId} not found`);

    const payload = { name: d.method, arguments: d.params };
    log("[RPC] calling call_tool", payload);

    /* ③ MCP 표준: call_tool 또는 tools/call */
    let rpcRes;
    try {
      rpcRes = await srv.rpc.call("call_tool", payload);
    } catch (err) {
      if (err.code === -32601) {
        log("[RPC] call_tool not found, falling back to tools/call");
        rpcRes = await srv.rpc.call("tools/call", payload);
      } else {
        throw err;
      }
    }

    /* ④ Filesystem 서버의 응답 : content 배열 중 text 항목 추출 */
    let rawResult;
    if (Array.isArray(rpcRes?.content)) {
      rawResult = rpcRes.content
        .filter((c) => c.type === "text")
        .map((c) => c.text)
        .join("\n");
    } else {
      rawResult = JSON.stringify(rpcRes);
    }
    log("[RPC] rawResult:", rawResult);

    /* ⑤ 결과를 한글 자연어로 요약(2차 OpenAI 호출) */
    const postRes = await axios.post(
      "https://api.openai.com/v1/chat/completions",
      {
        model: "gpt-4o-mini",
        messages: [
          {
            role: "system",
            content:
              "You are a helpful assistant. The user made a request, " +
              "we ran a filesystem tool and got some raw output. " +
              "Now produce a single, concise, natural-language response " +
              "that explains the result to the user." +
              "답변은 한글로 해주세요",
          },
          { role: "user", content: `Original request:\n${prompt}` },
          { role: "assistant", content: `Tool output:\n${rawResult}` },
        ],
      },
      { headers: { Authorization: `Bearer ${process.env.OPENAI_API_KEY}` } }
    );

    const friendly = postRes.data.choices[0].message.content.trim();
    log("[POST-PROCESS] final friendly answer:", friendly);
    return { result: friendly };
  } catch (e) {
    err("cmd fail", e);
    return { error: e.message };
  }
});

/* ───────────── 6. Electron App 생명주기 ───────────── */
app.whenReady().then(async () => {
  log("Electron ready");
  // 서버 사전 기동 (디폴트 allowedDir = 앱 실행 위치)
  for (const def of SERVER_DEFS) await spawnServer(def);
  createWindow();
});
app.on("will-quit", () => servers.forEach((s) => s.proc.kill()));
