/****************************************************************
 *  MCP-Web-App – 메인 프로세스 진입점 (v4 - node index.js 방식 통일)
 *
 *  ▸ 개발/배포 환경 모두 node index.js 를 spawn 하여 실행
 *  ▸ 배포 시 안정성을 위해 asarUnpack 경로 우선 탐색
 * ▸ Electron 환경 파이프 문제 해결 위해 spawn 시 shell:true 사용
 * ▸ refreshTools는 검증된 'tools/list'만 호출
 ****************************************************************/

const { app, BrowserWindow, ipcMain, dialog } = require("electron");
const path = require("path");
const fs = require("fs");

// 환경변수 주입 (기존 로직 유지)
let envPath;
if (app.isPackaged) {
  const exeEnv = path.join(path.dirname(process.execPath), ".env");
  const resEnv = path.join(process.resourcesPath, ".env");
  if (fs.existsSync(exeEnv)) {
    envPath = exeEnv;
  } else if (fs.existsSync(resEnv)) {
    envPath = resEnv;
  } else {
    envPath = null;
  }
} else {
  envPath = path.join(__dirname, ".env");
}
if (envPath) {
  console.log("Loading .env from", envPath);
  require("dotenv").config({ path: envPath });
} else {
  console.warn(".env not found; OPENAI_API_KEY must be set as system env var");
}

const spawn = require("cross-spawn"); // cross-platform child_process
const axios = require("axios"); // OpenAI REST 호출
const { v4: uuid } = require("uuid"); // JSON-RPC id 생성용

/* ───────────── Logger 헬퍼 ───────────── */
const ts = () => new Date().toISOString();
const log = (...a) => console.log(ts(), "[INFO ]", ...a);
const warn = (...a) => console.warn(ts(), "[WARN ]", ...a);
const err = (...a) => console.error(ts(), "[ERROR]", ...a);

/* ───────────── StdioRPCClient 클래스 (원본 단순 버전 + 약간의 개선) ───────────── */
class StdioRPCClient {
  constructor(proc, tag) {
    this.proc = proc;
    this.tag = tag;
    this.pending = new Map();
    this.buffer = "";

    // 'readable' 이벤트 핸들러
    proc.stdout.on("readable", () => {
      log(`[${this.tag}] stdout event: readable`); // readable 이벤트 발생 로그
      let chunk;
      try {
        // 읽을 수 있는 모든 데이터를 반복해서 읽음
        while (null !== (chunk = proc.stdout.read())) {
          log(
            `[${this.tag}] stdout.read() got chunk (${
              chunk ? chunk.length : "null"
            }) bytes`
          ); // 읽은 데이터 로그
          // #onData 메소드를 수동으로 호출하여 데이터 처리 위임
          if (chunk) {
            // null이 아닐 때만 처리
            this.#onData(chunk);
          }
        }
        // while 루프 종료: 현재 읽을 데이터 없음
        // log(`[${this.tag}] stdout.read() loop finished, no more data available for now.`);
      } catch (readError) {
        err(`[${this.tag}] Error during stdout.read():`, readError);
      }
    });

    proc.stdout.on("data", (d) => this.#onData(d));
    proc.stderr.on("data", (d) => this.#handleStdErr(d)); // 별도 함수로 분리
    proc.on("exit", (code, signal) =>
      warn(`[${tag}] exited with code ${code} signal ${signal}`)
    );
    proc.on("error", (spawnError) => err(`[${tag}] spawn error:`, spawnError)); // 스폰 에러 처리

    // --- 스트림 이벤트 로깅 추가 ---
    proc.stdout.on("readable", () =>
      log(`[${this.tag}] stdout event: readable`)
    );
    proc.stdout.on("end", () => log(`[${this.tag}] stdout event: end`)); // 스트림 종료 시
    proc.stdout.on("close", () => log(`[${this.tag}] stdout event: close`)); // 스트림 완전 종료 시
    proc.stdout.on("error", (err) =>
      err(`[${this.tag}] stdout event: error:`, err)
    ); // 스트림 에러

    proc.stdin.on("drain", () => log(`[${this.tag}] stdin event: drain`)); // 쓰기 버퍼 비워졌을 때
    proc.stdin.on("error", (err) =>
      err(`[${this.tag}] stdin event: error:`, err)
    ); // 쓰기 에러
    proc.stdin.on("close", () => log(`[${this.tag}] stdin event: close`)); // 쓰기 스트림 종료 시
    // -----------------------------
  }

  #handleStdErr(chunk) {
    chunk
      .toString()
      .split(/\r?\n/)
      .forEach((line) => {
        if (!line) return;
        const lowerLine = line.toLowerCase();
        // 서버의 정상적인 시작/정보 로그 패턴 확장
        if (
          lowerLine.startsWith("secure mcp") ||
          lowerLine.includes("allowed directories") ||
          line.trim().startsWith("[")
        ) {
          log(`[${this.tag} SERVER STDERR]`, line); // stderr로 오는 정보성 로그
        } else {
          err(`[${this.tag} SERVER ERR!]`, line); // 실제 오류 가능성
        }
      });
  }

  #onData(chunk) {
    const receivedString = chunk.toString("utf-8"); // UTF-8 명시 (한글 깨짐 방지)

    // 받은 데이터 조각 즉시 로깅
    log(
      `[<span class="math-inline">\{this\.tag\}\] Raw stdout <<< \(</span>{receivedString.length} chars): [${receivedString.replace(
        /\n/g,
        "\\n"
      )}]`
    );

    this.buffer += receivedString;

    // 버퍼 업데이트 후 상태 로깅
    log(
      `[<span class="math-inline">\{this\.tag\}\] Buffer updated to \(</span>{this.buffer.length} chars): [${this.buffer.replace(
        /\n/g,
        "\\n"
      )}]`
    );

    let idx;
    let processedLine = false; // 이번 data 이벤트에서 라인 처리 여부 플래그

    while ((idx = this.buffer.indexOf("\n")) >= 0) {
      const line = this.buffer.slice(0, idx).trim();
      const remainingBuffer = this.buffer.slice(idx + 1); // 남은 버퍼 미리 계산
      log(
        `[${this.tag}] Found newline at index <span class="math-inline">\{idx\}\. Processing line \(</span>{line.length} chars): [${line}]`
      );
      processedLine = true;

      if (!line) {
        log(`[${this.tag}] Skipping empty line.`);
        this.buffer = remainingBuffer; // 버퍼 업데이트
        continue;
      }

      try {
        if (line.startsWith("{") && line.endsWith("}")) {
          log(`[${this.tag}] Attempting JSON parse...`);
          const msg = JSON.parse(line);
          log(`[${this.tag}] Parsed JSON:`, JSON.stringify(msg)); // 파싱 결과 로깅
          const p = this.pending.get(msg.id);
          if (p) {
            log(
              `[${this.tag}] Found pending promise for ID: ${msg.id}. Resolving/Rejecting...`
            );
            this.pending.delete(msg.id); // 먼저 삭제
            msg.error ? p.reject(msg.error) : p.resolve(msg.result);
          } else {
            warn(`[${this.tag}] Response for unknown/handled ID: ${msg.id}`);
          }
        } else {
          log(
            `[<span class="math-inline">\{this\.tag\}\] Ignoring non\-JSON line\: \[</span>{line}]`
          );
        }
      } catch (e) {
        err(
          `[<span class="math-inline">\{this\.tag\}\] Error processing line\: "</span>{line}"`,
          e.message
        );
      }
      this.buffer = remainingBuffer; // 다음 루프 또는 다음 data 이벤트를 위해 버퍼 업데이트
    }

    // 이번 청크 처리 후 라인 분리가 안된 경우 로그
    if (!processedLine && receivedString.length > 0) {
      log(
        `[${this.tag}] Received data chunk but no complete line (newline) found yet.`
      );
    }
  }

  call(method, params = {}) {
    const id = uuid();
    const payload = { jsonrpc: "2.0", id, method, params };
    const payloadString = JSON.stringify(payload) + "\n";
    log(
      `[${
        this.tag
      }] Sending RPC -> ID: ${id}, Method: ${method}, Payload: ${payloadString.trim()}`
    ); // 요청 로깅

    return new Promise((resolve, reject) => {
      // 원본 resolve/reject 저장 (settle 함수에서 사용)
      const originalResolve = resolve;
      const originalReject = reject;
      let settled = false; // 중복 완료 방지 플래그

      // Promise 완료 처리 함수 (resolve/reject 공통 로직)
      const settle = (settler, value) => {
        if (!settled) {
          settled = true;
          clearTimeout(callTimeout);
          log(
            `[${this.tag}] Settling promise for ID: ${id}, Method: ${method}`
          );
          // settler(value)를 먼저 호출하고 delete를 나중에
          try {
            settler(value); // 원본 resolve 또는 reject 호출
          } finally {
            // Promise가 정상 처리되든 에러를 던지든 반드시 실행
            this.pending.delete(id); // 대기 목록에서 제거
          }
        } else {
          // 이미 완료된 Promise를 다시 완료하려고 할 때 경고 (디버깅용)
          warn(
            `[${this.tag}] Attempted to settle promise for ID ${id} more than once.`
          );
        }
      };

      // 타임아웃 또는 정상 응답 시 호출될 resolve/reject 래퍼 함수
      const wrappedResolve = (result) => settle(originalResolve, result);
      const wrappedReject = (error) => settle(originalReject, error);

      // --- 개별 호출 타임아웃 설정 (예: 15초) ---
      const TIMEOUT_DURATION = 15000; // 15초 (필요에 따라 조절)
      const callTimeout = setTimeout(() => {
        // 타임아웃 시점에도 여전히 pending 상태인지 확인
        if (this.pending.has(id)) {
          warn(
            `[${this.tag}] RPC call TIMEOUT! ID: ${id}, Method: ${method} after ${TIMEOUT_DURATION}ms.`
          );
          // 타임아웃 에러를 reject (settle 함수를 통해 클린업 포함)
          wrappedReject(
            new Error(
              `RPC call '${method}' timed out after ${
                TIMEOUT_DURATION / 1000
              } seconds`
            )
          );
        }
      }, TIMEOUT_DURATION);
      // ----------------------------------------

      // pending 맵에 래핑된 함수 저장
      this.pending.set(id, { resolve: wrappedResolve, reject: wrappedReject });

      // 서버로 요청 전송
      try {
        // stdin 스트림이 쓰기 가능한지 확인
        if (!this.proc.stdin || !this.proc.stdin.writable) {
          throw new Error("Process stdin is not writable or closed");
        }
        // 쓰기 작업 및 콜백을 통한 오류 처리
        this.proc.stdin.write(payloadString, "utf-8", (writeError) => {
          if (writeError && !settled) {
            // 쓰기 에러 발생 및 아직 Promise 완료 전
            err(
              `[${this.tag}] stdin write callback error for ID ${id}:`,
              writeError
            );
            wrappedReject(writeError); // 에러로 Promise reject
          } else if (!writeError) {
            // 쓰기 성공 로그 (필요시)
            // log(`[${this.tag}] stdin write successful for ID: ${id}`);
          }
        });
      } catch (syncWriteError) {
        // 동기적 예외 처리 (예: 스트림 종료 직후 write 시도)
        err(
          `[${this.tag}] Exception during stdin.write for ID ${id}:`,
          syncWriteError
        );
        // 즉시 Promise reject (settle 함수를 통해 클린업 포함)
        wrappedReject(syncWriteError);
      }
    });
  }
}

/* ───────────── 0. MCP 서버 정의 ───────────── */
const SERVER_DEFS = [
  {
    id: "fs",
    name: "Filesystem",
    // module 정보만 사용
    module: "@modelcontextprotocol/server-filesystem",
    allowedDir: process.cwd(), // 기본값
  },
];

/* ───────────── 1. 런타임 상태 ───────────── */
const servers = [];

/* ───────── 2. 서버 스폰 & 툴 로딩 (node index.js 방식 통일) ───────── */
async function spawnServer(def) {
  let proc;
  const serverArgs = [def.allowedDir || process.cwd()]; // 서버 스크립트에 전달될 인자
  const moduleName = def.module.replace(/\//g, path.sep); // 예: @modelcontextprotocol\server-filesystem
  let cliPath = null;

  log(`Attempting to spawn server '${def.id}' for directory: ${serverArgs[0]}`);

  try {
    if (app.isPackaged) {
      // --- 배포 환경 ---
      log(`[${def.id}] Running in packaged mode.`);
      const basePath = process.resourcesPath;
      const possiblePaths = [
        path.join(
          basePath,
          "app.asar.unpacked",
          "node_modules",
          moduleName,
          "dist",
          "index.js"
        ), // asarUnpack 우선
        path.join(basePath, "node_modules", moduleName, "dist", "index.js"), // resources 루트 바로 아래
        path.join(
          path.dirname(process.execPath),
          "node_modules",
          moduleName,
          "dist",
          "index.js"
        ), // 실행파일 옆 (덜 일반적)
      ];

      for (const p of possiblePaths) {
        if (fs.existsSync(p)) {
          cliPath = p;
          log(`[${def.id}] Found server script at: ${cliPath}`);
          break;
        } else {
          log(`[${def.id}] Script not found at: ${p}`);
        }
      }
      if (!cliPath) {
        throw new Error(
          "Server script not found in any expected packaged locations."
        );
      }
    } else {
      // --- 개발 환경 ---
      log(`[${def.id}] Running in development mode.`);
      cliPath = path.join(
        __dirname,
        "node_modules",
        moduleName,
        "dist",
        "index.js"
      );
      if (!fs.existsSync(cliPath)) {
        // 개발 환경에서는 cli.js도 찾아볼 수 있음 (선택 사항)
        const altCliPath = path.join(
          __dirname,
          "node_modules",
          moduleName,
          "dist",
          "cli.js"
        );
        if (fs.existsSync(altCliPath)) {
          cliPath = altCliPath;
        } else {
          throw new Error(
            `Server script not found at ${cliPath} or ${altCliPath}`
          );
        }
      }
      log(`[${def.id}] Found server script at: ${cliPath}`);
    }

    // --- Node.js 실행 파일 경로 변경 ---
    const nodeExePath = "node"; // 시스템 PATH에 있는 'node' 사용
    log(
      `[${def.id}] Spawning using System Node: ${nodeExePath} (instead of Electron's Node)`
    );
    // --------------------------------

    // --- spawn 호출 부분 ---
    proc = spawn(nodeExePath, [cliPath, ...serverArgs], {
      cwd: def.allowedDir || process.cwd(),
      stdio: ["pipe", "pipe", "pipe"],
      windowsHide: true, // Windows에서 콘솔 창 숨김
      // shell: true, // <--- shell: true 옵션 사용
    });
  } catch (spawnErr) {
    err(`[${def.id}] Failed to find or spawn server process:`, spawnErr);
    // 시스템 node가 PATH에 없는 경우 에러 발생 가능
    if (spawnErr.code === "ENOENT") {
      err(
        `[${def.id}] 'node' command not found in system PATH. Make sure Node.js is installed and in PATH.`
      );
    }
    return null; // 스폰 실패 시 null 반환
  }

  // --- 공통 로직: RPC 클라이언트 생성 및 도구 로드 ---
  if (!proc) {
    // proc 생성 실패 시 처리
    err(`[${def.id}] Server process could not be spawned.`);
    return null;
  }

  log(
    `[${def.id}] Process spawned successfully (PID: ${proc.pid}). Setting up RPC client...`
  );
  const rpc = new StdioRPCClient(proc, def.id);
  const srv = { ...def, proc, rpc, tools: [] };

  // --- 요청 지연 ---
  // log(`[${def.id}] Waiting 2 seconds before calling refreshTools...`);
  // await new Promise((resolve) => setTimeout(resolve, 2000)); // 2초 지연
  // --------------------

  log(`[${def.id}] Now calling refreshTools...`);

  // refreshTools는 검증된 'tools/list'만 호출
  await refreshTools(srv);

  if (srv.tools.length === 0) {
    warn(
      `[${def.id}] Tool list is empty after refreshTools. Server might not be fully functional.`
    );
  }

  servers.push(srv);
  aliasMap.clear();
  return srv;
}

/* 서버에서 지원하는 툴 목록 가져와서 (서버별) 저장 (tools/list 만 사용) */
async function refreshTools(srv) {
  try {
    log(`[${srv.id}] Trying 'tools/list' to get tool schema...`);
    // --- tools/list만 호출 ---
    const raw = await srv.rpc.call("tools/list"); // 검증된 메소드 이름 사용
    log(`[${srv.id}] 'tools/list' call succeeded.`);

    // applyTools에서 에러 발생 시 catch 블록으로 넘어감
    applyTools(srv, raw);
  } catch (e) {
    // tools/list 호출 실패 또는 applyTools 실패 시
    err(`[${srv.id}] Failed to load tools using 'tools/list'`, e?.message || e);
    srv.tools = []; // 실패 시 빈 배열 보장
  }
}

/* applyTools 함수 (기존 코드 유지, 약간의 방어 코드 추가) */
function applyTools(srv, raw) {
  let arr = [];
  // raw 데이터 로깅 (디버깅용)
  // log(`[${srv.id}] Raw data received for tools:`, JSON.stringify(raw));

  if (raw && Array.isArray(raw.tools)) {
    // 가장 표준적인 MCP 응답 형태 우선 체크
    arr = raw.tools;
  } else if (Array.isArray(raw)) {
    arr = raw;
  } else if (typeof raw === "object" && raw !== null) {
    // 객체 형태일 경우 (예: { tool1: {...}, tool2: {...} })
    arr = Object.values(raw);
  }

  if (!Array.isArray(arr) || arr.length === 0) {
    warn(`[${srv.id}] Received empty or invalid tool list structure:`, raw);
    srv.tools = [];
    throw new Error(
      "No tools found or invalid structure received from server."
    ); // 에러 발생
  }

  srv.tools = arr
    .map((t) => {
      // 서버 응답에 name 필드가 없는 경우 대비
      if (!t || typeof t.name !== "string") {
        warn(`[${srv.id}] Invalid tool structure found:`, t);
        return null; // 잘못된 항목은 null 처리
      }
      return {
        ...t,
        name: `${srv.id}_${t.name}`, // alias 생성
        _origMethod: t.name, // 원본 메소드 이름 저장
      };
    })
    .filter(Boolean); // null 항목 제거

  if (srv.tools.length === 0) {
    throw new Error(
      "Processed tool list is empty after filtering invalid entries."
    );
  }

  log(
    `Tools[${srv.id}] loaded:`,
    srv.tools.map((t) => t.name)
  );
}

/* 모든 서버의 툴 평탄화 (기존 유지) */
function allTools() {
  return servers.flatMap((s) => s.tools);
}

/* OpenAI ChatGPT v2 “function calling” 스펙용 변환 (기존 유지) */
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

/* ───────────── 3. OpenAI → 어떤 툴 쓸지 결정 (기존 유지, 약간의 개선) ───────────── */
async function decideCall(prompt) {
  const key = process.env.OPENAI_API_KEY;
  if (!key) return { type: "text", content: "OPENAI_API_KEY is not set." };

  const availableTools = allTools();
  if (availableTools.length === 0) {
    warn("[LLM] No tools available from servers. Replying directly.");
    // 도구가 없을 때 사용자에게 직접 응답하도록 유도 (예: 파일 시스템 접근 불가 안내)
    // 이 부분은 시나리오에 맞게 조정 필요
  }

  log(`[LLM] Deciding call with ${availableTools.length} tools available.`);

  const res = await axios
    .post(
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
          }, // 시스템 프롬프트 유지
          { role: "user", content: prompt },
        ],
        tools: availableTools.map(formatToolV2),
        tool_choice: availableTools.length > 0 ? "auto" : "none", // 도구 유무에 따른 설정
      },
      { headers: { Authorization: `Bearer ${key}` } }
    )
    .catch((llmError) => {
      // LLM 호출 에러 처리
      err(
        "[LLM] API call failed:",
        llmError?.response?.data || llmError?.message || llmError
      );
      return { error: "Failed to contact AI service." }; // 오류 객체 반환
    });

  if (res.error) {
    // 위 catch 블록에서 반환된 오류 처리
    return { type: "text", content: res.error };
  }
  if (!res?.data?.choices?.length) {
    // 응답 구조 유효성 검사
    err("[LLM] Invalid response structure:", res?.data);
    return {
      type: "text",
      content: "Received an invalid response from AI service.",
    };
  }

  log("[LLM] raw response:", JSON.stringify(res.data, null, 2));
  const msg = res.data.choices[0].message;

  let fc = null;
  if (Array.isArray(msg.tool_calls) && msg.tool_calls.length)
    fc = msg.tool_calls[0].function;
  else if (msg.function_call)
    // Legacy support
    fc = msg.function_call;

  if (!fc || !fc.arguments) return { type: "text", content: msg.content ?? "" };

  let parsed;
  try {
    parsed = JSON.parse(fc.arguments);
  } catch (e) {
    err("Failed to parse tool arguments:", fc.arguments, e);
    return {
      type: "text",
      content: msg.content ?? "Error parsing tool arguments.",
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
      content: `Internal error: Tool alias '${alias}' configuration not found.`,
    };
  }

  return { type: "rpc", srvId: map.srvId, method: map.method, params };
}

/* ───────────── 4. Electron 윈도우 생성 (기존 유지) ───────────── */
let mainWindow;
function createWindow() {
  log("createWindow");
  mainWindow = new BrowserWindow({
    width: 1000,
    height: 700,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false, // 보안 강화
      devTools: !app.isPackaged, // 개발 모드에서만 활성화
    },
  });
  mainWindow.loadFile(path.join(__dirname, "renderer", "index.html"));
}

/* ───────────── 5. IPC 라우팅 (기존 유지, 약간의 개선) ───────────── */
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
  const newSrvInstance = await spawnServer({ ...serverDef, allowedDir: dir });
  if (!newSrvInstance) {
    err(`Failed to restart server for directory: ${dir}`);
    dialog.showErrorBox("Server Error", `Could not start server for ${dir}.`); // 사용자 알림
    return null;
  }
  log(`Server restarted successfully for directory: ${dir}`);
  return dir;
});

ipcMain.handle("run-command", async (_e, prompt) => {
  log("[IPC] run-command:", prompt); // 프롬프트 내용 로깅
  try {
    const d = await decideCall(prompt);
    if (d.type === "text") return { result: d.content };

    // decideCall에서 에러 발생 시 처리 (예: { error: '...' })
    if (d.error) {
      err("[IPC] Error during decision phase:", d.error);
      return { error: d.error };
    }
    // RPC 타입이 아닐 경우 처리 (이론상 발생하면 안됨)
    if (d.type !== "rpc") {
      err("[IPC] Unexpected decision type:", d.type);
      return { error: "Internal error processing command." };
    }

    const srv = servers.find((s) => s.id === d.srvId);
    if (!srv || !srv.proc || srv.proc.killed) {
      err(`[IPC] Server ${d.srvId} not found or not running.`);
      return { error: `Server ${d.srvId} is not available.` };
    }
    if (srv.tools.length === 0) {
      err(`[IPC] Server ${d.srvId} has no tools loaded.`);
      return { error: `Server ${d.srvId} is not ready (no tools).` };
    }

    const payload = { name: d.method, arguments: d.params };
    log("[RPC] Calling tool via RPC:", JSON.stringify(payload));

    let rpcRes;
    try {
      // 서버 코드 확인 결과, call_tool/tools/call 이 아닌 도구 이름 직접 호출 시도 필요 가능성 있음
      // 우선순위: 1. call_tool, 2. tools/call, 3. 직접 메소드 이름 (d.method)
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
          if (err2 && err2.code === -32601) {
            log(
              `[RPC] 'tools/call' also not found. Falling back to direct method call '${d.method}'...`
            );
            try {
              // 인자 구조가 다를 수 있음에 유의 (payload.arguments 또는 d.params 직접 전달)
              rpcRes = await srv.rpc.call(d.method, d.params);
              log(`[RPC] Direct method call '${d.method}' succeeded.`);
            } catch (err3) {
              err(
                `[RPC] Direct call to '${d.method}' failed. Last error:`,
                err3
              );
              throw err3; // 최종 실패 에러 전달
            }
          } else {
            throw err2; // 'tools/call'에서 다른 에러 발생
          }
        }
      } else {
        throw err; // 'call_tool'에서 다른 에러 발생
      }
    }

    log("[RPC] Raw Response from server:", JSON.stringify(rpcRes));

    let rawResult;
    if (rpcRes && Array.isArray(rpcRes.content)) {
      rawResult = rpcRes.content
        .filter((c) => c.type === "text")
        .map((c) => c.text)
        .join("\n");
    } else {
      warn("[RPC] Unexpected response format:", rpcRes);
      rawResult = JSON.stringify(rpcRes ?? "No valid content received");
    }
    log(
      "[RPC] Extracted rawResult (first 500 chars):",
      rawResult.substring(0, 500) + (rawResult.length > 500 ? "..." : "")
    );

    /* ⑤ 결과를 한글 자연어로 요약(2차 OpenAI 호출) */
    const MAX_RAW_RESULT_LENGTH = 10000; // 예시: 10k chars
    const truncatedResult =
      rawResult.length > MAX_RAW_RESULT_LENGTH
        ? rawResult.substring(0, MAX_RAW_RESULT_LENGTH) + "\n... (truncated)"
        : rawResult;

    let friendly = "결과를 요약하는 데 문제가 발생했습니다."; // 기본값 설정
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
        }, // 메시지 내용은 이전과 동일
        { headers: { Authorization: `Bearer ${process.env.OPENAI_API_KEY}` } }
      );
      friendly = postRes.data.choices[0].message.content.trim();
      log("[POST-PROCESS] final friendly answer:", friendly);
    } catch (postProcessError) {
      err(
        "[POST-PROCESS] OpenAI summarization failed:",
        postProcessError?.response?.data ||
          postProcessError?.message ||
          postProcessError
      );
      // 실패 시 원본 결과라도 보여주거나, 에러 메시지 전달
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
      ? `명령 실행 중 오류 발생: ${e.message}`
      : "알 수 없는 오류가 발생했습니다.";
    return { error: userErrorMessage };
  }
});

/* ───────────── 6. Electron App 생명주기 (기존 유지, 약간 개선) ───────────── */
app.whenReady().then(async () => {
  log("Electron ready");
  createWindow();
  log("Window created. Spawning initial servers...");
  try {
    for (const def of SERVER_DEFS) {
      await spawnServer(def);
    }
    log("Initial servers spawned.");
  } catch (serverSpawnError) {
    err("Error spawning initial servers:", serverSpawnError);
    dialog.showErrorBox(
      "초기화 오류",
      "백그라운드 서비스 시작 중 오류가 발생했습니다. 앱을 재시작해주세요."
    );
    // 앱 종료 또는 기능 제한 등 추가 처리 가능
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
        s.proc.kill();
      } catch (killError) {
        err(`Error killing process for ${s.id}:`, killError);
      }
    }
  });
  log("All server processes signaled to kill.");
});

// 전역 예외 처리 (선택 사항, 디버깅에 도움)
process.on("uncaughtException", (error) => {
  err("Unhandled Exception:", error);
  // 프로덕션에서는 에러 로그 기록 후 앱 종료 권장
  // dialog.showErrorBox('Critical Error', `An unhandled error occurred: ${error.message}`);
  // app.quit();
});

process.on("unhandledRejection", (reason, promise) => {
  err("Unhandled Rejection at:", promise, "reason:", reason);
  // dialog.showErrorBox('Critical Error', `An unhandled promise rejection occurred: ${reason}`);
  // app.quit();
});
