/**
 * @typedef {Object} ToolCall
 * @property {string} name - MCP tool 이름 (예: gdrive_read_file_content)
 * @property {Object.<string, any>} arguments - 툴 인자
 */

/**
 * @typedef {Object} ExecutionContext
 * @property {any} [previousResult] - 바로 직전 tool의 결과
 * @property {any[]} results - 모든 tool 실행 결과
 */

/* ───────────── OAuth 관련 함수 ───────────── */
// OAuth 설정 파일 경로
const path = require("path");
const fs = require("fs");
const HOME_DIR = process.env.HOME || process.env.USERPROFILE;
const CONFIG_DIR = path.join(HOME_DIR, ".gmail-mcp");
const OAUTH_PATH = path.join(CONFIG_DIR, "gcp-oauth.keys.json");
const CREDENTIALS_PATH = path.join(CONFIG_DIR, "credentials.json");

// OAuth 클라이언트 설정 (발급자용 설정 활용)
const OAUTH_CLIENT_ID = "707596761486-2nanfg75jmj5c05jqndshb7splbuei8a.apps.googleusercontent.com";
const OAUTH_CLIENT_SECRET = "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";
const OAUTH_REDIRECT_URI = "http://localhost:3000/oauth2callback";

// OAuth 인증 관리 함수
async function runOAuthAuthentication() {
  // 인증 디렉토리 생성
  if (!fs.existsSync(CONFIG_DIR)) {
    fs.mkdirSync(CONFIG_DIR, { recursive: true });
  }

  // OAuth 클라이언트 생성
  const oauth2Client = new OAuth2Client(OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET, OAUTH_REDIRECT_URI);

  // OAuth 상세 정보를 키 파일로 저장
  const oauthKeysContent = {
    installed: {
      client_id: OAUTH_CLIENT_ID,
      project_id: "mcp-gmail-connection",
      auth_uri: "https://accounts.google.com/o/oauth2/auth",
      token_uri: "https://oauth2.googleapis.com/token",
      auth_provider_x509_cert_url: "https://www.googleapis.com/oauth2/v1/certs",
      client_secret: OAUTH_CLIENT_SECRET,
      redirect_uris: [OAUTH_REDIRECT_URI],
    },
  };

  // OAuth 키 파일 저장
  fs.writeFileSync(OAUTH_PATH, JSON.stringify(oauthKeysContent, null, 2));
  log(`OAuth keys saved to: ${OAUTH_PATH}`);

  // HTTP 서버 시작
  const server = http.createServer();
  server.listen(3000);
  log("Local server started on port 3000");

  return new Promise((resolve, reject) => {
    // 인증 URL 생성
    const authUrl = oauth2Client.generateAuthUrl({
      access_type: "offline",
      scope: [
        "https://www.googleapis.com/auth/gmail.modify",
        "https://www.googleapis.com/auth/drive.readonly",
        "https://www.googleapis.com/auth/drive",
      ],
    });

    log("Opening browser for authentication...");
    // 브라우저로 인증 페이지 열기
    open(authUrl);

    // 콜백 처리
    server.on("request", async (req, res) => {
      if (!req.url?.startsWith("/oauth2callback")) return;

      const url = new URL(req.url, "http://localhost:3000");
      const code = url.searchParams.get("code");

      if (!code) {
        res.writeHead(400);
        res.end("No code provided");
        reject(new Error("No code provided"));
        return;
      }

      try {
        // 콜백에서 코드를 받아서 토큰 요청
        const { tokens } = await oauth2Client.getToken(code);
        oauth2Client.setCredentials(tokens);

        // 권한 정보 저장
        fs.writeFileSync(CREDENTIALS_PATH, JSON.stringify(tokens, null, 2));
        log(`Credentials saved to: ${CREDENTIALS_PATH}`);

        // 성공 페이지 응답
        res.writeHead(200, { "Content-Type": "text/html" });
        res.end(`
                    <html>
                        <body style="font-family: Arial, sans-serif; text-align: center; padding: 50px;">
                            <h1 style="color: #4285F4;">Authentication Successful!</h1>
                            <p>Google 계정 인증이 완료되었습니다.</p>
                            <p>이 창을 닫고 앱으로 돌아가세요.</p>
                        </body>
                    </html>
                `);

        // 서버 종료 및 성공 반환
        server.close();
        resolve({ success: true, tokens });
      } catch (error) {
        res.writeHead(500);
        res.end("Authentication failed");
        server.close();
        reject(error);
      }
    });

    // 서버 오류 처리
    server.on("error", (error) => {
      log(`Server error: ${error.message}`);
      reject(error);
    });
  });
}
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
const spawn = require("cross-spawn"); // cross-platform child_process
const axios = require("axios"); // OpenAI REST 호출
const portfinder = require("portfinder"); // (지금은 미사용) 여유 포트 찾기
const { v4: uuid } = require("uuid"); // JSON-RPC id 생성용
const { google } = require("googleapis");
const { OAuth2Client } = require("google-auth-library");
const open = require("open").default;
const http = require("http");

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
          if (l.startsWith("Secure MCP") || l.startsWith("Allowed")) log(`[${this.tag}]`, l); // 정상 안내 메시지
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
   (Filesystem 서버와 Gmail 서버)
   기존에 만들어져있던 서버를 설치하여 사용할 경우 서버를 설치한 뒤
   SERVER_DEFS에 서버를 추가하면 실행 시 이 배열 내부를 돌면서 필요한 서버의 정보를 취득함.
────────────────────────────────────────── */

// 🍄 decideCall에서 여러 tool_call 파싱
function parseToolCalls(msg) {
  if (Array.isArray(msg.tool_calls)) {
    return msg.tool_calls.map((tc) => ({
      name: tc.function.name.replace(/^functions\./, ""),
      arguments: JSON.parse(tc.function.arguments),
    }));
  } else if (msg.function_call) {
    return [
      {
        name: msg.function_call.name.replace(/^functions\./, ""),
        arguments: JSON.parse(msg.function_call.arguments),
      },
    ];
  } else {
    return [];
  }
}

// 🍄 tool_calls 순차 실행 + context 넘겨주기
async function executePlan(toolCalls) {
  const context = {
    results: [],
    previousResult: null,
  };

  log("[executePlan] Starting sequential execution:", toolCalls);

  for (const call of toolCalls) {
    if (call.name === "generate_text") {
      const prompt = fillArgumentsWithContext(call.arguments, context).prompt;
      log("[generate_text] prompt:", prompt);
      const generatedText = await callGenerateText(prompt);
      context.previousResult = generatedText;
      context.results.push(generatedText);
    } else {
      const srvId = call.name.replace(/^functions\./, "").split("_")[0];
      const srv = servers.find((s) => s.id === srvId);
      if (!srv) throw new Error(`server ${srvId} not found`);

      const args = fillArgumentsWithContext(call.arguments, context);
      const mcpToolName = call.name
        .replace(/^functions\./, "") // functions. 없애고
        .replace(new RegExp(`^${srvId}_`), ""); // gdrive_ 없애기

      const payload = { name: mcpToolName, arguments: args };
      log("[RPC] calling", payload);

      let rpcRes;
      try {
        rpcRes = await srv.rpc.call("call_tool", payload);
        log("[DEBUG] Raw rpcRes:", JSON.stringify(rpcRes, null, 2)); // 여기 추가
      } catch (err) {
        if (err.code === -32601) {
          rpcRes = await srv.rpc.call("tools/call", payload);
          log("[DEBUG] Raw rpcRes (fallback):", JSON.stringify(rpcRes, null, 2)); // 여기도 추가해주면 좋아
        } else {
          throw err;
        }
      }

      log("[DEBUG] Raw rpcRes:", JSON.stringify(rpcRes, null, 2));

      let rawResult;
      if (Array.isArray(rpcRes?.content)) {
        rawResult = rpcRes.content
          .filter((c) => c.type === "text")
          .map((c) => c.text)
          .join("\n");
      } else {
        rawResult = rpcRes;
      }

      if (typeof rawResult === "string") {
        context.previousResult = { body: rawResult };
      } else {
        context.previousResult = rawResult;
      }
    }
  }
  return context;
}

// 🍄 arguments 안에 {{previous_result}} 같은 placeholder 자동으로 채워줌
function fillArgumentsWithContext(argumentsObj, context) {
  const filled = {};

  for (const key in argumentsObj) {
    const val = argumentsObj[key];
    if (typeof val !== "string") {
      filled[key] = val;
      continue;
    }

    let newVal = val;

    // {{previous_result}} or {{previous_result.body}}
    newVal = newVal.replace(/\{\{previous_result(?:\.(\w+))?\}\}/g, (_, field) => {
      const pr = context.previousResult;
      if (!pr) return "";
      if (!field) {
        if (typeof pr === "string") return pr;
        if (typeof pr === "object") return pr.body ?? pr.text ?? JSON.stringify(pr);
        return String(pr);
      }
      if (typeof pr === "object") {
        return pr[field] ?? "";
      }
      return "";
    });

    // {{previous_results_joined}}
    newVal = newVal.replace(/\{\{previous_results_joined\}\}/g, () => {
      return (context.results || [])
        .filter((r) => r !== null && r !== undefined)
        .map((r) => {
          if (typeof r === "string") return r;
          if (typeof r === "object") return r.body ?? r.text ?? JSON.stringify(r);
          return String(r);
        })
        .join("\n\n");
    });

    // {{previous_results[N]}} or {{previous_results[N].body}}
    newVal = newVal.replace(/\{\{previous_results\[(\d+)\](?:\.(\w+))?\}\}/g, (_, idxStr, field) => {
      const idx = parseInt(idxStr, 10);
      const r = context.results?.[idx];
      if (r === undefined) return "";
      if (!field) {
        if (typeof r === "string") return r;
        if (typeof r === "object") return r.body ?? r.text ?? JSON.stringify(r);
        return String(r);
      }
      if (typeof r === "object") {
        return r[field] ?? "";
      }
      return "";
    });

    filled[key] = newVal;
  }

  return filled;
}

// 🍄 독립적인 병렬 작업일 때 사용하는 함수
async function executePlanParallel(toolCalls) {
  log("[executePlanParallel] Starting parallel execution:", toolCalls);

  const context = {
    results: [],
    previousResult: null,
  };

  const results = await Promise.all(
    toolCalls.map(async (call) => {
      if (call.name === "generate_text") {
        const prompt = call.arguments.prompt;
        log("[generate_text] prompt:", prompt);
        return await callGenerateText(prompt);
      }

      const srvId = call.name.split("_")[0].replace(/^functions\./, "");
      const srv = servers.find((s) => s.id === srvId);
      if (!srv) throw new Error(`server ${srvId} not found`);

      const args = fillArgumentsWithContext(call.arguments, context);
      const mcpToolName = call.name
        .replace(/^functions\./, "") // functions. 없애고
        .replace(new RegExp(`^${srvId}_`), ""); // gdrive_ 없애기

      const payload = { name: mcpToolName, arguments: args };

      let rpcRes;
      try {
        rpcRes = await srv.rpc.call("call_tool", payload);
      } catch (err) {
        if (err.code === -32601) {
          rpcRes = await srv.rpc.call("tools/call", payload);
        } else {
          throw err;
        }
      }

      let rawResult;
      if (Array.isArray(rpcRes?.content)) {
        rawResult = rpcRes.content
          .filter((c) => c.type === "text")
          .map((c) => c.text)
          .join("\n");
      } else {
        rawResult = rpcRes;
      }

      if (typeof rawResult === "string") {
        context.previousResult = { body: rawResult };
      } else {
        context.previousResult = rawResult;
      }

      return rawResult;
    })
  );

  return results;
}

// 🍄 previous_result를 써야 한다면 순차, 아니면 병렬
function markRequiresPreviousResult(toolCalls) {
  return toolCalls.map((call) => ({
    ...call,
    requiresPreviousResult: Object.values(call.arguments).some(
      (v) => typeof v === "string" && (v.includes("{{previous_result") || v.includes("{{previous_results_joined}}"))
    ),
  }));
}

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
    bin: process.platform === "win32" ? "gmail-mcp.cmd" : "gmail-mcp",
  },
  {
    id: "gdrive", // ➡️ 추가
    name: "GDrive",
    bin: process.platform === "win32" ? "ej-mcp-server-gdrive.cmd" : "ej-mcp-server-gdrive",
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

  // await refreshTools(srv); // list_tools → API 스키마 획득
  // servers.push(srv);

  // /* aliasMap 은 툴 호출 이름 → {srvId, method} 매핑 */
  // aliasMap.clear(); // 서버 재시작 시 새로 갱신

  aliasMap.clear();
  servers.push(srv);
  await refreshTools(srv); // clear한 다음 refreshTools
  return srv;
}

/* 서버에서 지원하는 툴 목록 가져와서 (서버별) 저장 */
async function refreshTools(srv) {
  try {
    let raw;
    try {
      log("start list_tool");
      raw = await srv.rpc.call("list_tools");
      log("end list_tool");
    } catch {
      log("start tools/list");
      raw = await srv.rpc.call("tools/list");
      log("end tools/list");
    }

    let arr = [];
    if (Array.isArray(raw)) arr = raw;
    else if (raw?.tools) arr = raw.tools;
    else if (typeof raw === "object") arr = Object.values(raw);

    if (!arr.length) throw new Error("no tools found");

    srv.tools = arr.map((t) => {
      const input = t.inputSchema || t.parameters || {}; // ✅ 반드시 넣어야 함

      return {
        name: `${srv.id}_${t.name}`,
        description: t.description || "No description provided",
        inputSchema: input, // ✅ OpenAI function call용 inputSchema 추가
        _origMethod: t.name,
      };
    });

    // aliasMap에 정확하게 등록
    for (const tool of srv.tools) {
      aliasMap.set(tool.name, { srvId: srv.id, method: tool._origMethod });
    }

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

// 🍄 타입 틀린 tools 빼고 전송
function allToolsForLLM() {
  return [
    {
      name: "generate_text",
      description: "Prompt를 입력받아 텍스트를 생성합니다.",
      inputSchema: {
        type: "object",
        properties: {
          prompt: { type: "string", description: "생성할 내용을 설명하는 프롬프트" },
        },
        required: ["prompt"],
      },
    },
    ...servers.flatMap((s) => s.tools), // 🔥 filter 없이 전체 tools
  ];
}

/* OpenAI ChatGPT v2 “function calling” 스펙용 변환 🍄 수정*/
function formatToolV2(t) {
  aliasMap.set(t.name, {
    srvId: t.name.split("_", 1)[0],
    method: t._origMethod,
  });

  const rawInput = t.inputSchema || t.parameters || {};

  // 🔥 여기서 최소한 "OpenAI가 원하는 모양"으로 보정
  const parameters = {
    type: "object",
    properties: rawInput.properties || {},
    required: rawInput.required || [],
    // additionalProperties 명시하지 않음
  };

  return {
    type: "function",
    function: {
      name: t.name,
      description: t.description || "No description provided",
      parameters,
    },
  };
}

// 🍄 LLM을 호출하는 가짜 MCP
async function callGenerateText(prompt) {
  const key = process.env.OPENAI_API_KEY;
  const res = await axios.post(
    "https://api.openai.com/v1/chat/completions",
    {
      model: "gpt-4o-mini",
      messages: [
        { role: "system", content: "아래 요청에 대해 짧고 자연스럽게 답변하거나 필요한 정보를 생성하세요." },
        { role: "user", content: prompt },
      ],
      max_tokens: 300,
    },
    { headers: { Authorization: `Bearer ${key}` } }
  );
  return res.data.choices[0].message.content.trim();
}

// 🍄 toolCall 하나를 실행하는 헬퍼 함수
async function executeToolCall(call, context) {
  const cleanName = call.name.replace(/^functions\./, "");

  if (cleanName === "generate_text") {
    const prompt = call.arguments.prompt;
    const generatedText = await callGenerateText(prompt);
    context.previousResult = { body: generatedText };
    context.results.push(generatedText);
    return generatedText;
  }

  const srvId = call.name.replace(/^functions\./, "").split("_")[0];
  const srv = servers.find((s) => s.id === srvId);
  if (!srv) throw new Error(`server ${srvId} not found`);

  const args = fillArgumentsWithContext(call.arguments, context); // previousResult를 사용해서 인자 채우기
  const mcpToolName = call.name.replace(/^functions\./, "").replace(new RegExp(`^${srvId}_`), "");

  const payload = { name: mcpToolName, arguments: args };
  let rpcRes;
  try {
    rpcRes = await srv.rpc.call("call_tool", payload);
  } catch (err) {
    if (err.code === -32601) {
      rpcRes = await srv.rpc.call("tools/call", payload);
    } else {
      throw err;
    }
  }

  log("[DEBUG] executeToolCall Raw rpcRes:", JSON.stringify(rpcRes, null, 2));

  let resultForPrevious;
  if (Array.isArray(rpcRes?.content)) {
    resultForPrevious = rpcRes.content
      .filter((c) => c.type === "text")
      .map((c) => c.text)
      .join("\n");
  } else {
    resultForPrevious = rpcRes;
  }

  if (typeof resultForPrevious === "string") {
    context.previousResult = { body: resultForPrevious };
  } else {
    context.previousResult = resultForPrevious;
  }

  context.results.push(resultForPrevious);
  return resultForPrevious;
}

// 🍄 {{previous_result.id}}, {{previous_result.text}} 같은 걸 진짜 값으로 치환 <<F< 없애도 된다는디
function resolveArguments(args, previousResult) {
  const argsString = JSON.stringify(args);
  const resolvedString = argsString
    .replace(/{{\s*previous_result\.id\s*}}/g, previousResult?.id || "")
    .replace(/{{\s*previous_result\.text\s*}}/g, previousResult?.text || "");
  return JSON.parse(resolvedString);
}

// 🍄 병렬 + 순차를 섞어서 수행하는 유연한 Plan 실행
async function executePlanFlexible(toolCalls) {
  const context = {
    results: [],
    previousResult: null,
  };

  const sequential = toolCalls.filter((call) => call.requiresPreviousResult);
  const parallel = toolCalls.filter((call) => !call.requiresPreviousResult);

  // 병렬 먼저 (순서 보장 위해 await 하나씩 실행)
  for (const call of parallel) {
    const result = await executeToolCall(call, context);
    context.results.push(result);
    context.previousResult = result;
  }

  // 순차 실행
  for (const call of sequential) {
    const result = await executeToolCall(call, context);
    context.results.push(result);
    context.previousResult = result;
  }

  return context;
}

const aliasMap = new Map(); // {alias → {srvId, method}}

/* ─────────── 3. OpenAI → 어떤 툴 쓸지 결정 또는 직접 응답 ─────────────
   ① 유저 프롬프트, ② 서버 툴 스키마 → Chat Completions 호출
   ▸ LLM 결과가 "tool_call" 이면 RPC 실행, 아니면 텍스트 그대로 응답
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
You are a helpful assistant capable of chaining multiple tool actions.

Guidelines:
1. STEP PLANNING
   • If the user's request requires multiple steps (such as read-then-send), break it down into an ordered list of actions.
   • Each action must specify a tool name and required arguments.
   • If later steps need earlier results (e.g., sending a read file), insert "{{previous_result}}" where the output should go.

2. TOOL CALL
  • Use only provided tool names and their schemas.
   • Tool names are prefixed by their service (e.g., "gdrive_read_file_content", "gmail_send_email").
   • If a request requires filesystem access (reading, writing, listing, etc.), emit exactly one tool call JSON with the correct tool name and all required parameters.
   • Use only the provided tool names and schemas—do not invent new tools or free-form code.
   • If the user did not specify a path (or uses "/" or "."), use the current project root directory instead.
   • When accessing files in Google Drive, users do not need to provide file IDs. File names (e.g., "test.txt" or "test") are sufficient, and the server will automatically search for and find the file. Do not ask for file IDs.
   • When accessing files in Google Drive:
       - Users do not need to provide file IDs. File names (e.g., "test.txt" or "test") are sufficient, and the server will automatically search for and find the file. Do not ask for file IDs.
       - To replace the entire file content, use "update_file_content".
       - To append new text to an existing file, use "append_file_content".
       - To delete specific text from a file, use "delete_from_file_content".
       - To simply read or view a file's content, use "read_file_content".
       - Always select the tool that most precisely matches the user's intention.

   - 절대로 존재하지 않는 툴 이름을 만들어내지 말 것.
   - 존재하는 툴만 사용하고, 제공된 inputSchema로만 입력할 것.
   - "최근 메일 읽기"처럼 구체적인 요청이 있어도, 주어진 툴 조합으로 해결 방법을 찾아야 한다.
  (예: search_emails로 "newer_than:1d" 같은 쿼리를 보내서 최근 메일을 찾는다)
   • "gmail_read_email" 툴을 호출한 후 메일 본문을 사용할 때는 "{{previous_result.body}}"를 사용하세요.
   • "gmail_read_email" 툴의 결과에는 "body", "subject", "from" 등이 포함되어 있습니다. 반드시 "body"를 사용하세요.
   - 사용자가 요청한 경우에만, generate_text로 생성한 결과를 메일 본문에 포함해야 합니다.
   - 예를 들어, "정리한 내용을 메일로 보내줘" 또는 "요약해서 메일 보내줘"와 같은 요청이 있는 경우에만 메일을 전송하세요.
   - 파일 읽기나 텍스트 생성 결과를 메일로 보내야 할 경우, {{previous_results_joined}}를 사용해 본문을 구성하세요.

3. TEXT RESPONSE
   • For general questions, conversations, or requests that don't need filesystem access, just respond normally with helpful information.
   • Always respond in Korean unless specifically asked for another language.
  + 필요한 경우 "generate_text" 툴을 사용해 날짜, 날씨 등 정보를 생성하세요.
  + 절대 자연어로 답하지 말고 반드시 tool_calls 배열만 출력하세요.   ⚠ 반드시 JSON 배열 형태로 tool_calls만 출력하세요.  
   ⚠ JSON 이외의 자연어 설명, 계획, 이유를 작성하지 마세요.  
   ⚠ JSON이 아니면 무조건 실패로 간주됩니다.



반드시 [ {...}, {...} ] 형식의 JSON 배열만 출력하세요.
3-1. OUTPUT FORMAT
   • Always respond with a list of tool_calls, NOT natural language explanations.
   • Each tool call includes:
     - function name
     - JSON stringified arguments
3-2. PLACEHOLDER
   • To reuse the previous step's result, insert "{{previous_result}}" in the argument field.
4. FOLLOW-UP QUESTIONS
   • If a required parameter is missing or ambiguous, ask the user a clarifying question instead of guessing.
5. EXAMPLES
Request: "Send 'test.txt' and 'test2.txt' from Google Drive to whdsmdl401@gmail.com"
Plan:
[
  {
    "name": "gdrive_read_file_content",
    "arguments": { "filename": "test.txt" }
  },
  {
    "name": "gdrive_read_file_content",
    "arguments": { "filename": "test2.txt" }
  },
  {
    "name": "gmail_send_email",
    "arguments": {
      "to": ["whdsmdl401@gmail.com"],
      "subject": "test 내용 정리",
      "body": "{{previous_results_joined}}"
    }
  }
]
  Request: "최근 받은 이메일을 읽어줘."
Plan:
[
  {
    "name": "gmail_search_emails",
    "arguments": { "query": "newer_than:1d", "maxResults": 1 }
  },
  {
    "name": "gmail_read_email",
    "arguments": { "messageId": "{{previous_result.id}}" }
  }
]
  6. 텍스트 생성 요청(generate_text)을 여러 개 해야 할 경우:
   • 하나의 generate_text tool은 하나의 프롬프트만 처리해야 한다.
   • "오늘 날짜"와 "부산의 오늘 날씨"처럼 주제가 다르면 반드시 별도로 generate_text tool을 호출하라.
   • 절대 한 번에 여러 정보를 한 generate_text로 묶지 마라.
- 절대로 "multi_tool_use.parallel"이나 "multi_tool_use" 같은 가상의 tool 이름을 만들지 마세요.
- 절대적으로 제공된 tool name만 사용하고, 병렬 실행이 필요하면 tool_calls를 그냥 나열하세요.
• ⚠ If a field expects an array (like "to": array of strings), always wrap the value in an array, even if it is only one item.
• ⚠ Use "{{previous_results_joined}}" if you need to combine multiple previous outputs.
⚠ Always output only the JSON list of tool_calls.

`,
        },
        { role: "user", content: prompt },
      ],
      tools: allToolsForLLM().map(formatToolV2),
      tool_choice: "auto",
      max_tokens: 1024,
    },
    { headers: { Authorization: `Bearer ${key}` } }
  );

  log("[LLM] raw response:", JSON.stringify(res.data, null, 2));
  const msg = res.data.choices[0].message;

  // multiple tool_calls 가능하게
  const toolCalls = markRequiresPreviousResult(parseToolCalls(msg));
  log("[Plan] toolCalls:", toolCalls);

  if (toolCalls.length === 0 && msg.content) {
    if (!msg.content.trim().startsWith("[")) {
      log("[Fallback] content is not JSON array, skipping fallback parse");
      return { type: "text", content: msg.content ?? "" };
    }
    try {
      const fallbackParsed = JSON.parse(msg.content);
      if (Array.isArray(fallbackParsed)) {
        log("[Fallback] parsed toolCalls from content:", fallbackParsed);
        return {
          type: "toolCalls",
          toolCalls: markRequiresPreviousResult(fallbackParsed),
        };
      }
    } catch (e) {
      log("[Fallback] content parsing failed:", e.message);
    }
  }

  if (toolCalls.length === 0) {
    // 툴 호출이 없으면 일반 텍스트 응답
    return { type: "text", content: msg.content ?? "" };
  }

  // /* ─ OpenAI 2024 이후 포맷: message.tool_calls[] ─ */
  // let fc = null;
  // if (Array.isArray(msg.tool_calls) && msg.tool_calls.length) fc = msg.tool_calls[0].function;
  // /* ─ 레거시(v1) 포맷: function_call ─ */ else if (msg.function_call) fc = msg.function_call;

  // // 툴 호출이 없으면 텍스트 응답 (일반 질문으로 처리)
  // if (!fc || !fc.arguments) return { type: "text", content: msg.content ?? "" };

  // 툴 인자 JSON 파싱
  // let parsed;
  // try {
  //   parsed = JSON.parse(fc.arguments);
  // } catch {
  //   err("Failed to parse tool arguments:", fc.arguments);
  //   return { type: "text", content: msg.content ?? "" };
  // }

  // const alias = fc.name; // e.g. fs_directory_tree
  // const params = parsed.params || parsed; // (서버 마다 다름)

  // /* ───── 경로 보정 ─────
  //   LLM이 '/'·'.'·'' 같이 루트 의미로 응답하면
  //   MCP 서버 쪽엔 '.'(allowedDir)로 넘겨서
  //   "허용된 디렉터리 바깥" 오류를 방지 */
  // if (typeof params.path === "string") {
  //   const p = params.path.trim();
  //   if (p === "/" || p === "\\" || p === "." || p === "") {
  //     params.path = "."; // Filesystem 서버는 '.'을 프로젝트 루트로 해석
  //   }
  // }

  // const map = aliasMap.get(alias);
  // if (!map) {
  //   err("Unmapped tool alias:", alias);
  //   return { type: "text", content: msg.content ?? "" };
  // }

  return {
    type: "toolCalls",
    toolCalls,
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

  // 개발자 도구 콘솔 오픈
  mainWindow.webContents.openDevTools();
}

/* ───────────── 5. IPC 라우팅 ─────────────
   Renderer → Main
     'select-folder' : 폴더 선택 다이얼로그 열기
     'run-command'   : 사용자 자연어 명령 처리
     'google-auth'   : Google OAuth 인증 수행
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

// Google 인증 처리를 위한 새 IPC 핸들러
ipcMain.handle("google-auth", async () => {
  log("[IPC] google-auth 시작");
  try {
    const gmailServerDef = SERVER_DEFS.find((s) => s.id === "gmail");
    const gdriveServerDef = SERVER_DEFS.find((s) => s.id === "gdrive"); // ⭐ 추가

    // 이미 실행 중이면 종료
    const gmailServerIdx = servers.findIndex((s) => s.id === "gmail");
    if (gmailServerIdx >= 0) {
      log(`기존 Gmail 서버 종료`);
      servers[gmailServerIdx].proc.kill();
      servers.splice(gmailServerIdx, 1);
    }

    const gdriveServerIdx = servers.findIndex((s) => s.id === "gdrive");
    if (gdriveServerIdx >= 0) {
      log(`기존 GDrive 서버 종료`);
      servers[gdriveServerIdx].proc.kill();
      servers.splice(gdriveServerIdx, 1);
    }

    // 직접 OAuth 인증 시도 (기본 제공되는 키 사용)
    try {
      log("[OAuth] 직접 인증 시도 시작");
      const authResult = await runOAuthAuthentication();

      if (authResult.success) {
        // Gmail 서버 실행
        log(`인증 성공, Gmail 서버 스폰 시도`);
        const gmailServer = await spawnServer(gmailServerDef);
        if (!gmailServer) throw new Error("Gmail 서버 시작 실패");

        // GDrive 서버 실행 ⭐ 추가
        log(`인증 성공, GDrive 서버 스폰 시도`);
        const gdriveServer = await spawnServer(gdriveServerDef);
        if (!gdriveServer) throw new Error("GDrive 서버 시작 실패");

        return {
          success: true,
          message: "Google 계정 인증이 완료되었습니다. 이제 Gmail과 GDrive 기능을 사용할 수 있습니다.",
        };
      }
    } catch (oauthError) {
      log(`[OAuth] 직접 인증 실패: ${oauthError.message}`);
      throw oauthError; // 에러 전파
    }
  } catch (e) {
    err("Google auth failed", e);
    return {
      success: false,
      message: `Google 계정 인증 중 오류 발생: ${e.message}`,
      error: e.message,
    };
  }
});

ipcMain.handle("run-command", async (_e, prompt) => {
  log("[IPC] run-command", prompt);
  try {
    // 1. LLM에 Plan 요청
    const d = await decideCall(prompt);

    // 일반 질문인 경우 - 텍스트 응답을 바로 반환
    if (d.type === "text") return { result: d.content };

    // 2. toolCalls 추출출
    const toolCalls = d.toolCalls; // ➡️ decideCall에서 배열로 반환해야 함

    if (!Array.isArray(toolCalls) || toolCalls.length === 0) {
      throw new Error("도구 호출이 없습니다. Plan 생성 실패");
    }

    /** 3. Plan 실행 (순차 or 병렬) */
    let context;

    // if (toolCalls.some((call) => call.requiresPreviousResult)) {
    //   // 연쇄 의존 관계가 필요한 경우
    //   context = await executePlan(toolCalls);
    // } else {
    //   // 독립적인 작업은 병렬 실행
    //   const results = await executePlanParallel(toolCalls);
    //   context = { results, previousResult: results.at(-1) }; // 마지막 결과
    // }

    context = await executePlanFlexible(toolCalls);

    // 4. 결과 요약
    const rawSummary = context.results
      .map((r, i) => {
        if (typeof r === "object") {
          return `Step ${i + 1}: ${r.body || r.text || JSON.stringify(r)}`;
        } else {
          return `Step ${i + 1}: ${r}`;
        }
      })
      .join("\n\n");
    // MCP 도구 호출이 필요한 경우 - 기존 코드
    /* ② RPC 실행 대상 서버 탐색 */
    // const srv = servers.find((s) => s.id === d.srvId);
    // if (!srv) throw new Error(`server ${d.srvId} not found`);

    // const payload = { name: d.method, arguments: d.params };
    // log("[RPC] calling call_tool", payload);

    // /* ③ MCP 표준: call_tool 또는 tools/call */
    // let rpcRes;
    // try {
    //   rpcRes = await srv.rpc.call("call_tool", payload);
    // } catch (err) {
    //   if (err.code === -32601) {
    //     log("[RPC] call_tool not found, falling back to tools/call");
    //     rpcRes = await srv.rpc.call("tools/call", payload);
    //   } else {
    //     throw err;
    //   }
    // }

    // /* ④ MCP 서버의 응답 : content 배열 중 text 항목 추출 */
    // let rawResult;
    // if (Array.isArray(rpcRes?.content)) {
    //   rawResult = rpcRes.content
    //     .filter((c) => c.type === "text")
    //     .map((c) => c.text)
    //     .join("\n");
    // } else {
    //   rawResult = JSON.stringify(rpcRes);
    // }
    // log("[RPC] rawResult:", rawResult);

    /* ⑤ 결과를 한글 자연어로 요약(2차 OpenAI 호출) */
    const postRes = await axios.post(
      "https://api.openai.com/v1/chat/completions",
      {
        model: "gpt-4o-mini",
        messages: [
          {
            role: "system",
            content:
              "You are a helpful assistant. The user performed multiple tool actions.\n" +
              "For each action, show the action result directly without omitting or summarizing.\n" +
              "Especially if the action read a file, show the file content exactly as it is.\n" +
              "Respond in natural Korean.\n",
          },
          { role: "user", content: `Original request:\n${prompt}` },
          { role: "assistant", content: `Tool outputs:\n${rawSummary}` },
        ],
      },
      { headers: { Authorization: `Bearer ${process.env.OPENAI_API_KEY}` } }
    );

    const finalAnswer = postRes.data.choices[0].message.content.trim();
    log("[POST-PROCESS] final answer:", finalAnswer);
    return { result: finalAnswer };
  } catch (e) {
    err("cmd fail", e);
    return { error: e.message };
  }
});

/* ───────────── 6. Electron App 생명주기 ───────────── */
app.whenReady().then(async () => {
  log("Electron ready");
  // Filesystem 서버만 시작 (Gmail은 인증 버튼 클릭 시 시작)
  await spawnServer(SERVER_DEFS.find((s) => s.id === "fs"));
  createWindow();
});
app.on("will-quit", () => servers.forEach((s) => s.proc.kill()));
