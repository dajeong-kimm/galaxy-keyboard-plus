// main.js
require("dotenv").config();

const path = require("path");
const fs   = require("fs");
const http = require("http");
const open = require("open");
const { app, BrowserWindow, ipcMain, dialog } = require("electron");
const spawn = require("cross-spawn");
const axios = require("axios");
const { OAuth2Client } = require("google-auth-library");
const { v4: uuid } = require("uuid");

/* ────────── Logger ────────── */
const ts  = () => new Date().toISOString();
const log = (...a) => console.log(ts(), "[INFO]", ...a);
const err = (...a) => console.error(ts(), "[ERROR]", ...a);

/* ────────── OAuth 설정 ────────── */
const HOME_DIR     = process.env.HOME || process.env.USERPROFILE;
const CONFIG_DIR   = path.join(HOME_DIR, ".gdrive-mcp");
const OAUTH_PATH   = path.join(CONFIG_DIR, "gcp-oauth.keys.json");
const TOKENS_PATH  = path.join(CONFIG_DIR, "gdrive-credentials.json");

const CLIENT_ID     = process.env.GOOGLE_CLIENT_ID;
const CLIENT_SECRET = process.env.GOOGLE_CLIENT_SECRET;
const REDIRECT_URI  = process.env.GOOGLE_OAUTH_REDIRECT_URI;

// OAuth 플로우
async function runOAuthAuthentication() {
  if (!fs.existsSync(CONFIG_DIR)) fs.mkdirSync(CONFIG_DIR, { recursive: true });
  const o2c = new OAuth2Client(CLIENT_ID, CLIENT_SECRET, REDIRECT_URI);

  // 키 파일 저장
  fs.writeFileSync(OAUTH_PATH, JSON.stringify({
    installed: {
      client_id: CLIENT_ID,
      auth_uri: "https://accounts.google.com/o/oauth2/auth",
      token_uri: "https://oauth2.googleapis.com/token",
      client_secret: CLIENT_SECRET,
      redirect_uris: [REDIRECT_URI]
    }
  }, null, 2));

  // 콜백 대기용 서버
  const server = http.createServer();
  server.listen(3000);
  open(o2c.generateAuthUrl({
    access_type: "offline",
    scope: ["https://www.googleapis.com/auth/drive"],
    prompt: "consent"
  }));

  return new Promise((resolve, reject) => {
    server.on("request", async (req, res) => {
      if (!req.url.startsWith("/oauth2callback")) return;
      const code = new URL(req.url, "http://localhost:3000").searchParams.get("code");
      if (!code) {
        res.writeHead(400).end("No code");
        return reject(new Error("No code"));
      }
      try {
        const { tokens } = await o2c.getToken(code);
        o2c.setCredentials(tokens);
        fs.writeFileSync(TOKENS_PATH, JSON.stringify(tokens, null, 2));
        res.writeHead(200).end("<h1>인증 완료! 창을 닫아주세요.</h1>");
        server.close();
        resolve();
      } catch (e) {
        res.writeHead(500).end("인증 실패");
        server.close();
        reject(e);
      }
    });
    server.on("error", reject);
  });
}

/* ────────── RPC 클라이언트 ────────── */
class StdioRPCClient {
  constructor(proc, tag) {
    this.proc = proc; this.tag = tag;
    this.pending = new Map(); this.buf = "";
    proc.stdout.on("data", d => this._onData(d));
    proc.stderr.on("data", d => err(this.tag, d.toString()));
  }
  _onData(chunk) {
    this.buf += chunk.toString();
    let idx;
    while ((idx = this.buf.indexOf("\n")) >= 0) {
      const line = this.buf.slice(0, idx).trim();
      this.buf = this.buf.slice(idx + 1);
      if (!line) continue;
      const msg = JSON.parse(line);
      const p = this.pending.get(msg.id);
      if (p) {
        this.pending.delete(msg.id);
        msg.error ? p.reject(msg.error) : p.resolve(msg.result);
      }
    }
  }
  call(method, params = {}) {
    const id = uuid();
    return new Promise((res, rej) => {
      this.pending.set(id, { resolve: res, reject: rej });
      this.proc.stdin.write(JSON.stringify({ jsonrpc:"2.0", id, method, params }) + "\n");
    });
  }
}

/* ────────── MCP 서버 정의 ────────── */
const SERVER_DEFS = [
  {
    id: "fs",
    name: "Filesystem",
    bin: process.platform==="win32"
      ? "mcp-server-filesystem.cmd"
      : "mcp-server-filesystem",
    allowedDir: process.cwd()
  },
  {
    id: "gdrive",
    name: "GoogleDrive",
    bin: process.platform==="win32"
      ? "mcp-server-gdrive.cmd"
      : "mcp-server-gdrive",
    allowedDir: process.cwd(),
    args: ["--token", TOKENS_PATH]
  }
];
const servers = [];

// 툴 로드
async function refreshTools(srv) {
  let raw;
  try { raw = await srv.rpc.call("list_tools"); }
  catch { raw = await srv.rpc.call("tools/list"); }
  const arr = Array.isArray(raw)? raw: raw.tools || Object.values(raw);
  srv.tools = arr.map(t => ({ ...t, name:`${srv.id}_${t.name}`, _orig:t.name }));
}

// 서버 스폰
async function spawnServer(def) {
  const bin = path.join(__dirname,"node_modules",".bin",def.bin);
  if (!fs.existsSync(bin)) { err(`no bin: ${def.bin}`); return null; }
  const args = [...(def.args||[]), def.allowedDir];
  const proc = spawn(bin, args, { cwd:def.allowedDir, stdio:["pipe","pipe","pipe"] });
  const rpc = new StdioRPCClient(proc, def.id);
  const srv = { ...def, proc, rpc };
  await refreshTools(srv);
  servers.push(srv);
  log(`spawned ${def.id}, tools:`, srv.tools.map(t=>t.name));
  return srv;
}

/* ────────── OpenAI 호출 결정 (decideCall) ────────── */
async function decideCall(prompt) {
  const key = process.env.OPENAI_API_KEY;
  if (!key) throw new Error("OPENAI_API_KEY missing");

  const functions = servers.flatMap(s => s.tools.map(t => ({
    name: t.name,
    description: t.description,
    parameters: t.inputSchema || t.parameters || { type:"object", properties:{} }
  })));

  const { data } = await axios.post(
    "https://api.openai.com/v1/chat/completions",
    {
      model: "gpt-4o-mini",
      messages: [
        {
          role: "system",
          content: `
You are an assistant with two MCP servers:
 1) Filesystem: fs_read_*, fs_list_*, fs_write_* etc.
 2) GoogleDrive: single function 'gdrive_search' that searches files.

GUIDELINES:
- If the user needs filesystem actions, call the appropriate fs_* function.
- If the user asks for "구글 드라이브 파일 목록" or "내 구글 드라이브 파일을 보여줘", call gdrive_search with { "query": "" }.
- If the user asks to find/search by name, call gdrive_search with { "query": "<keyword>" }.
- Otherwise reply naturally in Korean without calling any function.
          `
        },
        { role: "user", content: prompt }
      ],
      functions,
      function_call: "auto"
    },
    { headers: { Authorization:`Bearer ${key}` } }
  );

  const msg = data.choices[0].message;
  if (msg.function_call) {
    const { name, arguments: args } = msg.function_call;
    const [srvId, method] = name.split("_");
    return {
      type: "rpc",
      srvId,
      method,
      params: JSON.parse(args || "{}")
    };
  }
  return { type:"text", content:msg.content };
}

/* ────────── IPC 핸들러 ────────── */
ipcMain.handle("google-auth", async () => {
  try {
    servers.find(s=>s.id==="gdrive")?.proc.kill();
    await runOAuthAuthentication();
    await spawnServer(SERVER_DEFS.find(s=>s.id==="gdrive"));
    return { success:true, message:"Google Drive 인증 완료" };
  } catch (e) {
    err("auth fail", e);
    return { success:false, message:e.message };
  }
});

ipcMain.handle("run-command", async (_e, prompt) => {
  log("run-command", prompt);
  try {
    const d = await decideCall(prompt);
    if (d.type==="text") return { result:d.content };

    const srv = servers.find(s=>s.id===d.srvId);
    if (!srv) throw new Error(`no server ${d.srvId}`);

    let rpcRes;
    try {
      rpcRes = await srv.rpc.call("call_tool", { name:d.method, arguments:d.params });
    } catch (e) {
      if (e.code===-32601) {
        rpcRes = await srv.rpc.call("tools/call", { name:d.method, arguments:d.params });
      } else throw e;
    }

    const raw = Array.isArray(rpcRes.content)
      ? rpcRes.content.filter(c=>c.type==="text").map(c=>c.text).join("\n")
      : JSON.stringify(rpcRes);

    const { data: post } = await axios.post(
      "https://api.openai.com/v1/chat/completions",
      {
        model:"gpt-4o-mini",
        messages:[
          { role:"system", content:"한글로 간단히 요약해주세요." },
          { role:"assistant", content:raw }
        ]
      },
      { headers:{ Authorization:`Bearer ${process.env.OPENAI_API_KEY}` } }
    );

    return { result: post.choices[0].message.content.trim() };
  } catch (e) {
    err("run-command fail", e);
    return { error:e.message };
  }
});

/* ────────── 창 생성 ────────── */
function createWindow() {
  const win = new BrowserWindow({
    width:1000, height:700,
    webPreferences:{ preload: path.join(__dirname,"preload.js"), contextIsolation:true }
  });
  win.loadFile(path.join(__dirname,"renderer","index.html"));
  win.webContents.openDevTools();
}

app.whenReady().then(async () => {
  log("app ready");
  await spawnServer(SERVER_DEFS.find(s=>s.id==="fs"));
  if (fs.existsSync(TOKENS_PATH)) {
    log("spawning gdrive");
    await spawnServer(SERVER_DEFS.find(s=>s.id==="gdrive"));
  } else {
    log("gdrive auth needed");
  }
  createWindow();
});

app.on("will-quit", () => servers.forEach(s=>s.proc.kill()));
