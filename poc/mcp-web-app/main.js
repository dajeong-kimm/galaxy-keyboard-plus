/****************************************************************
 *  MCP-Web-App – 메인 프로세스 진입점 (v7.1 - 최종 권장 방식 적용 - 전체 코드)
 *
 *  ▸ 개발 환경: node_modules/.bin 스크립트 실행 (원본 방식)
 *  ▸ 배포 환경: extraResources로 복사된 bin 폴더 내 스크립트 실행
 *  ▸ refreshTools 등 다른 로직은 원본 유지
 ****************************************************************/

const { app, BrowserWindow, ipcMain, dialog } = require("electron");
const path = require("path");
const fs = require("fs");

// --- 환경변수 주입 ---
let envPath;
if (app.isPackaged) {
  const exeEnv = path.join(path.dirname(process.execPath), ".env"); // 수정: 패키지에서는 resources/.env 우선 (extraResources로 복사됨)
  const resEnv = path.join(process.resourcesPath, ".env");
  if (fs.existsSync(resEnv)) {
    envPath = resEnv;
  } else if (fs.existsSync(exeEnv)) {
    // 차선책: exe 옆
    envPath = exeEnv;
  } else {
    envPath = null;
  }
} else {
  envPath = path.join(__dirname, ".env"); // 개발 환경
}
if (envPath) {
  console.log("Loading .env from", envPath);
  require("dotenv").config({ path: envPath });
} else {
  console.warn(".env not found; OPENAI_API_KEY must be set as system env var");
}
// --------------------

const spawn = require("cross-spawn"); // 원본 코드에서 사용한 cross-spawn 유지
const axios = require("axios");
const { v4: uuid } = require("uuid");

/* ───────────── Logger 헬퍼 ───────────── */
const ts = () => new Date().toISOString();
const log = (...a) => console.log(ts(), "[INFO ]", ...a);
const warn = (...a) => console.warn(ts(), "[WARN ]", ...a);
const err = (...a) => console.error(ts(), "[ERROR]", ...a);

/* ───────────── StdioRPCClient 클래스 (원본 버전) ───────────── */
class StdioRPCClient {
  constructor(proc, tag) {
    this.proc = proc;
    this.tag = tag;
    this.pending = new Map();
    this.buffer = "";
    proc.stdout.setEncoding("utf8"); // 인코딩 설정 추가

    proc.stdout.on("data", (d) => this.#onData(d));
    proc.stderr.on("data", (d) => this.#handleStdErr(d));
    proc.on("exit", (code, signal) =>
      warn(`[${tag}] exited with code ${code} signal ${signal}`)
    );
    proc.on("error", (spawnError) => err(`[${tag}] spawn error:`, spawnError));
  }

  #handleStdErr(chunk) {
    chunk
      .toString()
      .split(/\r?\n/)
      .forEach((line) => {
        if (!line) return;
        // 원본 로그 로직 유지 (필요 시 수정)
        if (line.startsWith("Secure MCP") || line.startsWith("Allowed")) {
          log(`[${this.tag}]`, line);
        } else {
          err(`[${this.tag}!]`, line);
        }
      });
  }

  #onData(chunk) {
    this.buffer += chunk.toString("utf-8");
    let idx;
    while ((idx = this.buffer.indexOf("\n")) >= 0) {
      const line = this.buffer.slice(0, idx).trim();
      this.buffer = this.buffer.slice(idx + 1);
      if (!line) continue;
      try {
        // 비 JSON 로그는 무시 (서버가 stdout으로 로그 출력 시)
        if (!line.startsWith("{")) {
          // log(`[${this.tag} SERVER STDOUT]`, line); // 필요 시 로그 활성화
          continue;
        }
        const msg = JSON.parse(line);
        const p = this.pending.get(msg.id);
        if (p) {
          this.pending.delete(msg.id);
          msg.error ? p.reject(msg.error) : p.resolve(msg.result);
        }
      } catch (e) {
        err(`[${this.tag}] broken JSON`, line, e.message);
      }
    }
  }

  call(method, params = {}) {
    const id = uuid();
    const payload = { jsonrpc: "2.0", id, method, params };
    const payloadString = JSON.stringify(payload) + "\n";
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      try {
        if (!this.proc.stdin || !this.proc.stdin.writable) {
          throw new Error("Process stdin is not writable");
        }
        this.proc.stdin.write(payloadString, "utf-8", (writeError) => {
          // Node.js 스트림 쓰기 콜백은 보통 에러만 전달
          if (writeError) {
            err(
              `[${this.tag}] stdin write callback error for ID ${id}:`,
              writeError
            );
            // 쓰기 실패 시 해당 Promise를 reject 해야 함
            if (this.pending.has(id)) {
              // 이미 다른 이유로 처리되지 않았다면
              this.pending.get(id).reject(writeError);
              this.pending.delete(id);
            }
          }
        });
      } catch (syncWriteError) {
        err(
          `[${this.tag}] Exception during stdin.write for ID ${id}:`,
          syncWriteError
        );
        if (this.pending.has(id)) {
          // 이미 다른 이유로 처리되지 않았다면
          this.pending.get(id).reject(syncWriteError);
          this.pending.delete(id);
        }
      }
    });
  }
}

/* ───────────── 0. MCP 서버 정의 (원본 방식) ───────────── */
const SERVER_DEFS = [
  {
    id: "fs",
    name: "Filesystem",
    bin:
      process.platform === "win32"
        ? "mcp-server-filesystem.cmd"
        : "mcp-server-filesystem",
    allowedDir: process.cwd(),
    // module 정보는 사용 안 함
  },
];

/* ───────────── 1. 런타임 상태 (변경 없음) ───────────── */
const servers = [];

/* ───────── 2. 서버 스폰 & 툴 로딩 (.bin 방식 + 배포 경로 처리) ───────── */
async function spawnServer(def) {
  let proc;
  const serverArgs = [def.allowedDir || process.cwd()];
  const binName = def.bin;
  let binPath = null;

  log(
    `Attempting to spawn server '${def.id}' via .bin for directory: ${serverArgs[0]}`
  );

  try {
    if (app.isPackaged) {
      // --- 배포 환경: resources/bin 폴더에서 .bin 스크립트 찾기 ---
      log(
        `[${def.id}] Running in packaged mode. Searching for .bin script in resources/bin...`
      );
      const baseResourcePath = process.resourcesPath;
      // extraResources로 'bin' 폴더에 복사된 스크립트 경로
      binPath = path.join(baseResourcePath, "bin", binName); // 수정된 경로

      log(`[${def.id}] Checking packaged path: ${binPath}`);
      if (!fs.existsSync(binPath)) {
        // 대체 경로 탐색 시도 (덜 권장)
        const altPath = path.join(baseResourcePath, binName); // resources 루트
        log(`[${def.id}] Checking alternative packaged path: ${altPath}`);
        if (fs.existsSync(altPath)) {
          binPath = altPath;
          log(`[${def.id}] Found .bin script at alternative path: ${binPath}`);
        } else {
          throw new Error(
            `.bin script '${binName}' not found in packaged resources. Check 'extraResources' in package.json and ensure it copies to 'bin/' or root.`
          );
        }
      }
      log(`[${def.id}] Found .bin script at: ${binPath}`);
    } else {
      // --- 개발 환경: 프로젝트 루트의 node_modules/.bin ---
      log(`[${def.id}] Running in development mode.`);
      binPath = path.join(__dirname, "node_modules", ".bin", binName);
      if (!fs.existsSync(binPath)) {
        throw new Error(`.bin script not found at: ${binPath}`);
      }
      log(`[${def.id}] Found .bin script at: ${binPath}`);
    }

    log(`[${def.id}] Spawning script: ${binPath}`);
    // --- .bin 스크립트 실행 ---
    proc = spawn(binPath, serverArgs, {
      cwd: def.allowedDir || process.cwd(),
      stdio: ["pipe", "pipe", "pipe"],
      windowsHide: true, // Windows에서 콘솔 창 숨김
    });
  } catch (spawnErr) {
    err(`[${def.id}] Failed to find or spawn server process:`, spawnErr);
    if (app.isPackaged && spawnErr.message.includes(".bin script not found")) {
      dialog.showErrorBox(
        "실행 오류",
        `필수 실행 파일(${binName})을 찾을 수 없습니다. 앱 설치 상태를 확인하거나 재설치해주세요.`
      );
    }
    return null;
  }

  if (!proc) {
    return null;
  }

  log(
    `[${def.id}] Process spawned successfully (PID: ${proc.pid}). Setting up RPC client...`
  );
  const rpc = new StdioRPCClient(proc, def.id); // 원본 StdioRPCClient 사용
  const srv = { ...def, proc, rpc, tools: [] };

  // --- 지연 시간 제거 ---
  log(`[${def.id}] Calling refreshTools immediately...`);
  // refreshTools는 원본 로직 ('list_tools' -> 'tools/list') 사용
  await refreshTools(srv);

  if (srv.tools.length === 0) {
    warn(`[${def.id}] Tool list is empty after refreshTools.`);
  }
  servers.push(srv);
  aliasMap.clear();
  return srv;
}

/* 서버에서 지원하는 툴 목록 가져와서 (원본 로직) */
async function refreshTools(srv) {
  try {
    let raw;
    log(`[${srv.id}] Trying list_tools...`);
    try {
      raw = await srv.rpc.call("list_tools");
      log(`[${srv.id}] list_tools succeeded.`);
    } catch (e1) {
      log(
        `[${srv.id}] list_tools failed: ${
          e1?.message || e1
        }. Trying tools/list...`
      );
      raw = await srv.rpc.call("tools/list");
      log(`[${srv.id}] tools/list succeeded.`);
    }
    applyTools(srv, raw);
  } catch (e) {
    err(`[${srv.id}] tool load failed definitively`, e?.message || e);
    srv.tools = [];
  }
}

/* applyTools 함수 (원본 버전 + 방어 코드) */
function applyTools(srv, raw) {
  let arr = [];
  if (Array.isArray(raw)) arr = raw;
  else if (raw?.tools) arr = raw.tools;
  else if (typeof raw === "object" && raw !== null) arr = Object.values(raw);

  if (!Array.isArray(arr) || arr.length === 0) {
    warn(`[${srv.id}] Received empty or invalid tool list structure:`, raw);
    srv.tools = [];
    throw new Error("no tools found or invalid structure");
  }

  srv.tools = arr
    .map((t) => {
      if (!t || typeof t.name !== "string") {
        warn(`[${srv.id}] Invalid tool structure found:`, t); // 경고 로그 추가
        return null;
      }
      return { ...t, name: `${srv.id}_${t.name}`, _origMethod: t.name };
    })
    .filter(Boolean); // null 제거

  if (srv.tools.length === 0) {
    throw new Error("Processed tool list is empty after filtering.");
  }

  log(
    `Tools[${srv.id}] loaded:`,
    srv.tools.map((t) => t.name)
  );
}

/* 모든 서버의 툴 평탄화 (원본 유지) */
function allTools() {
  return servers.flatMap((s) => s.tools);
}

/* OpenAI ChatGPT v2 “function calling” 스펙용 변환 (원본 유지) */
function formatToolV2(t) {
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
        t.parameters || { type: "object", properties: {} },
    },
  };
}

const aliasMap = new Map(); // {alias → {srvId, method}}

/* ───────────── 3. OpenAI → 어떤 툴 쓸지 결정 (원본 유지 + 도구 없을때 처리) ───────────── */
async function decideCall(prompt) {
  const key = process.env.OPENAI_API_KEY;
  if (!key) return { type: "text", content: "OPENAI_API_KEY is not set." };

  const availableTools = allTools();
  // 도구가 로드되지 않았을 경우 처리 추가
  if (availableTools.length === 0) {
    warn("[LLM] No tools available from servers. Cannot perform tool call.");
    return {
      type: "text",
      content:
        "파일 시스템 도구를 현재 사용할 수 없습니다. 잠시 후 다시 시도해주세요.",
    };
  }

  try {
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
          }, // 시스템 프롬프트 원본 유지
          { role: "user", content: prompt },
        ],
        tools: availableTools.map(formatToolV2), // 도구 목록 전달
        tool_choice: "auto", // 도구가 있으므로 auto
      },
      { headers: { Authorization: `Bearer ${key}` } }
    );

    log("[LLM] raw response:", JSON.stringify(res.data, null, 2));
    const msg = res.data.choices[0].message;

    let fc = null;
    if (Array.isArray(msg.tool_calls) && msg.tool_calls.length)
      fc = msg.tool_calls[0].function;
    else if (msg.function_call) fc = msg.function_call;

    if (!fc || !fc.arguments)
      return { type: "text", content: msg.content ?? "" };

    let parsed;
    try {
      parsed = JSON.parse(fc.arguments);
    } catch (e) {
      err("Failed to parse tool arguments:", fc.arguments, e);
      return {
        type: "text",
        content: msg.content ?? "Error parsing arguments.",
      };
    }

    const alias = fc.name;
    const params = parsed.params || parsed;

    if (typeof params.path === "string") {
      const p = params.path.trim();
      if (p === "/" || p === "\\" || p === "." || p === "") {
        params.path = ".";
      }
    }

    const map = aliasMap.get(alias);
    if (!map) {
      err("Unmapped tool alias:", alias);
      return {
        type: "text",
        content: `Internal error: Tool alias '${alias}' not found.`,
      };
    }
    return { type: "rpc", srvId: map.srvId, method: map.method, params };
  } catch (llmError) {
    err(
      "[LLM] API call failed:",
      llmError?.response?.data || llmError?.message || llmError
    );
    return { type: "text", content: "AI 서비스 연결 중 오류가 발생했습니다." };
  }
}

/* ───────────── 4. Electron 윈도우 생성 (원본 유지, devTools 옵션 추가) ───────────── */
let mainWindow;
function createWindow() {
  log("createWindow");
  mainWindow = new BrowserWindow({
    width: 1000,
    height: 700,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      devTools: !app.isPackaged, // 개발 중에만 DevTools 활성화
    },
  });
  mainWindow.loadFile(path.join(__dirname, "renderer", "index.html"));
}

/* ───────────── 5. IPC 라우팅 (원본 유지, 에러 처리 강화) ───────────── */
ipcMain.handle("select-folder", async () => {
  const r = await dialog.showOpenDialog({ properties: ["openDirectory"] });
  if (r.canceled || r.filePaths.length === 0) return null;

  const dir = r.filePaths[0];
  log("folder selected", dir);

  const idx = servers.findIndex((s) => s.id === "fs");
  if (idx >= 0) {
    if (servers[idx].proc && !servers[idx].proc.killed) {
      log(
        `Killing existing server process for 'fs' (PID: ${servers[idx].proc.pid})`
      );
      servers[idx].proc.kill();
    }
    servers.splice(idx, 1);
  }

  const serverDef = SERVER_DEFS.find((def) => def.id === "fs");
  if (!serverDef) {
    err("Server definition for 'fs' not found.");
    return null;
  }

  // allowdDir만 업데이트하여 spawnServer 호출
  const newSrvInstance = await spawnServer({ ...serverDef, allowedDir: dir });
  if (!newSrvInstance) {
    err(`Failed to restart server for directory: ${dir}`);
    dialog.showErrorBox("Server Error", `Could not restart server for ${dir}.`);
    return null;
  }
  log(`Server restarted successfully for directory: ${dir}`);
  return dir;
});

ipcMain.handle("run-command", async (_e, prompt) => {
  log("[IPC] run-command:", prompt);
  try {
    const d = await decideCall(prompt);
    if (d.type === "text") return { result: d.content };
    if (d.error) {
      err("[IPC] Error during decision:", d.error);
      return { error: d.error };
    }
    if (d.type !== "rpc") {
      err("[IPC] Unexpected decision:", d.type);
      return { error: "Internal error." };
    }

    const srv = servers.find((s) => s.id === d.srvId);
    if (!srv || !srv.proc || srv.proc.killed) {
      err(`[IPC] Server ${d.srvId} not running.`);
      return { error: `Server ${d.srvId} not available.` };
    }
    // 서버에 도구가 로드되었는지 확인 (refreshTools 실패 시 대비)
    if (srv.tools.length === 0) {
      err(`[IPC] Server ${d.srvId} not ready (no tools).`);
      return { error: `Server ${d.srvId} is not ready.` };
    }

    const payload = { name: d.method, arguments: d.params };
    log("[RPC] Calling tool via RPC:", JSON.stringify(payload));

    let rpcRes;
    try {
      // MCP 표준 호출 방식 (call_tool -> tools/call)
      log(`[RPC] Attempting 'call_tool' for ${d.method}...`);
      rpcRes = await srv.rpc.call("call_tool", payload);
      log(`[RPC] 'call_tool' succeeded.`);
    } catch (err) {
      if (err && err.code === -32601) {
        // Method not found
        log("[RPC] 'call_tool' not found, falling back to 'tools/call'...");
        try {
          rpcRes = await srv.rpc.call("tools/call", payload);
          log(`[RPC] 'tools/call' succeeded.`);
        } catch (err2) {
          err("[RPC] 'tools/call' also failed.", err2);
          // 직접 메소드 호출 시도는 여기서는 제거 (표준 방식 실패 시 에러 반환)
          throw err2; // 최종 에러 전달
        }
      } else {
        throw err; // 'call_tool'에서 다른 종류의 에러 발생
      }
    }

    log("[RPC] Raw Response:", JSON.stringify(rpcRes));
    let rawResult;
    if (rpcRes && Array.isArray(rpcRes.content)) {
      rawResult = rpcRes.content
        .filter((c) => c.type === "text")
        .map((c) => c.text)
        .join("\n");
    } else {
      warn("[RPC] Unexpected response format:", rpcRes);
      rawResult = JSON.stringify(rpcRes ?? "No valid content");
    }
    log(
      "[RPC] Extracted Result (first 500):",
      rawResult.substring(0, 500) + (rawResult.length > 500 ? "..." : "")
    );

    // --- 2차 OpenAI 호출 (요약) ---
    let friendly = "(요약 중 오류 발생)";
    try {
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
      // 응답 구조 유효성 검사 추가
      if (
        postRes?.data?.choices?.length > 0 &&
        postRes.data.choices[0].message?.content
      ) {
        friendly = postRes.data.choices[0].message.content.trim();
        log("[POST-PROCESS] final friendly answer:", friendly);
      } else {
        err(
          "[POST-PROCESS] Invalid response structure from OpenAI summarization:",
          postRes?.data
        );
        friendly = `(요약 실패) 원본 결과 전체:\n${rawResult}`;
      }
    } catch (postProcessError) {
      err(
        "[POST-PROCESS] OpenAI summarization failed:",
        postProcessError?.response?.data ||
          postProcessError?.message ||
          postProcessError
      );
      friendly = `(요약 실패) 원본 결과:\n${rawResult.substring(0, 1000)}${
        rawResult.length > 1000 ? "..." : ""
      }`;
    }
    return { result: friendly };
  } catch (e) {
    err(
      "[IPC] run-command failed:",
      e?.message || e,
      e?.code ? `(Code: ${e.code})` : "",
      e?.stack || ""
    );
    const userErrorMessage = e?.message
      ? `명령 실행 오류: ${e.message}`
      : "알 수 없는 오류.";
    return { error: userErrorMessage };
  }
});

/* ───────────── 6. Electron App 생명주기 (원본 유지 + 개선) ───────────── */
app.whenReady().then(async () => {
  log("Electron ready");
  // createWindow를 먼저 호출하여 UI를 빠르게 표시
  createWindow();
  log("Window created. Spawning initial servers...");
  try {
    for (const def of SERVER_DEFS) {
      await spawnServer(def); // await 추가
    }
    log("Initial servers spawned.");
    // 서버 로딩 상태를 Renderer에 알리는 IPC 메시지 전송 (선택 사항)
    // mainWindow.webContents.send('server-status', { ready: true });
  } catch (serverSpawnError) {
    err("Error spawning initial servers:", serverSpawnError);
    dialog.showErrorBox(
      "초기화 오류",
      "백그라운드 서비스 시작 중 오류가 발생했습니다. 앱을 재시작해주세요."
    );
    // mainWindow.webContents.send('server-status', { ready: false, error: 'Server failed to start' });
  }
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});

app.on("activate", () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow();
  }
});

app.on("will-quit", () => {
  log("Application quitting. Killing server processes...");
  servers.forEach((s) => {
    if (s.proc && !s.proc.killed) {
      try {
        log(`Killing server process for '${s.id}' (PID: ${s.proc.pid})`);
        // SIGTERM (기본값)으로 먼저 시도하고, 필요시 SIGKILL 사용 고려
        const killed = s.proc.kill();
        log(
          `Kill signal sent to ${s.id} (PID: ${s.proc.pid}). Result: ${killed}`
        );
      } catch (killError) {
        err(`Error killing process for ${s.id}:`, killError);
      }
    }
  });
  log("All server processes signaled to kill.");
});

// 전역 예외 처리 (디버깅 및 안정성 강화)
process.on("uncaughtException", (error) => {
  err("!!! Unhandled Exception:", error);
  dialog.showErrorBox(
    "예기치 않은 오류",
    `처리되지 않은 오류가 발생했습니다: ${error.message}\n\n앱을 종료합니다.`
  );
  // 안전하게 종료 시도
  app.quit();
});

process.on("unhandledRejection", (reason, promise) => {
  err("!!! Unhandled Rejection at:", promise, "reason:", reason);
  dialog.showErrorBox(
    "예기치 않은 오류",
    `처리되지 않은 Promise 거부가 발생했습니다: ${reason}\n\n앱을 종료합니다.`
  );
  // 안전하게 종료 시도
  app.quit();
});
