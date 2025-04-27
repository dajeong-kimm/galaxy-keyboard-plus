const spawn = require("cross-spawn");
const path = require("path");
const fs = require("fs");
const { dialog } = require("electron");
const { v4: uuid } = require("uuid");
const SERVER_DEFS = require("../config/mcp-servers.json");

const servers = [];

class StdioRPCClient {
  constructor(proc, tag) {
    this.proc = proc; this.tag = tag;
    this.pending = new Map(); this.buffer = "";
    proc.stdout.on("data", d => this.#onData(d));
    proc.stderr.on("data", d => console.error(`[${tag} stderr]`, d.toString()));
  }
  #onData(chunk) {
    this.buffer += chunk.toString();
    let idx;
    while ((idx = this.buffer.indexOf("\n")) >= 0) {
      const line = this.buffer.slice(0, idx).trim();
      this.buffer = this.buffer.slice(idx + 1);
      if (!line) continue;
      try {
        const msg = JSON.parse(line);
        const p = this.pending.get(msg.id);
        if (p) { this.pending.delete(msg.id); p.resolve(msg.result); }
      } catch (err) {
        console.error(`[${this.tag}] JSON 파싱 오류:`, err.message, "Line:", line);
      }
    }
  }
  call(method, params = {}) {
    const id = uuid();
    const payload = { jsonrpc: "2.0", id, method, params };
    return new Promise((res, rej) => {
      this.pending.set(id, { resolve: res, reject: rej });
      this.proc.stdin.write(JSON.stringify(payload) + "\n");
    });
  }
}

async function spawnAllServers() {
  for (const def of SERVER_DEFS) {
    await spawnServer(def);
  }
}

async function spawnServer(def) {
  // 바이너리가 node_modules/.bin에 있는지 확인
  let binPath = path.join(__dirname, "../../node_modules/.bin", def.bin);
  
  // 없으면 servers 디렉토리에서 확인
  if (!fs.existsSync(binPath)) {
    binPath = path.join(__dirname, "../../servers", def.bin);
    
    // 그래도 없으면 에러 출력 후 리턴
    if (!fs.existsSync(binPath)) {
      console.error(`Binary not found: ${binPath}`);
      console.error(`Tried: node_modules/.bin and servers directories`);
      return;
    }
  }
  
  console.log(`Spawning server: ${def.id} (${binPath})`);
  
  // Google Drive 서버인 경우 인증 파일 경로를 명시적으로 지정
  let args = [...(def.args || [])];
  
  if (def.id === 'gdrive') {
    // 인증 파일 경로 확인
    const oauthPath = path.resolve(process.cwd(), './oauth/gcp-oauth.keys.json');
  console.log(`Google Drive 서버 인증 경로: ${oauthPath}`);
    
    // 파일 존재하는지 확인
    if (fs.existsSync(oauthPath)) {
      console.log(`인증 파일 발견: ${oauthPath}`);
      
      // 인증 파일 내용 검사
      try {
        const content = fs.readFileSync(oauthPath, 'utf8');
        const data = JSON.parse(content);
        console.log('인증 파일 구조:', Object.keys(data));
        
        // auth 인수 추가
        args.push('auth');
        args.push(oauthPath);
      } catch (err) {
        console.error('인증 파일 파싱 오류:', err.message);
      }
    } else {
      console.error('인증 파일을 찾을 수 없음:', oauthPath);
    }
  }
  
  console.log(`서버 실행 인수:`, args);
  
  const proc = spawn(binPath, args, {
    cwd: process.cwd(),
    stdio: ["pipe","pipe","pipe"],
    env: {...process.env, ...def.env},
  });
  
  // 프로세스 종료 이벤트 처리
  proc.on('exit', (code, signal) => {
    console.log(`[${def.id}] Server exited with code ${code} and signal ${signal}`);
  });
  
  // 에러 이벤트 처리
  proc.on('error', (err) => {
    console.error(`[${def.id}] Error:`, err.message);
  });
  
  // RPC 클라이언트 생성 및 서버 추가
  const rpc = new StdioRPCClient(proc, def.id);
  const srv = { id: def.id, proc, rpc, tools: [] };
  servers.push(srv);
  
  // 도구 목록 로드 시도
  try {
    let list = null;
    try {
      list = await rpc.call("list_tools");
    } catch (e) {
      console.log(`[${def.id}] list_tools failed, trying tools/list...`);
      try {
        list = await rpc.call("tools/list");
      } catch (e2) {
        console.warn(`[${def.id}] tools/list also failed:`, e2.message);
        throw e2;
      }
    }
    
    // 응답 형식 처리
    if (list) {
      srv.tools = Array.isArray(list) ? list : (list.tools || []);
      console.log(`[${def.id}] ${srv.tools.length} tools loaded`);
    } else {
      console.warn(`[${def.id}] Server returned empty tools list`);
      srv.tools = [];
    }
  } catch (e) {
    console.warn(`[${def.id}] failed to load tools:`, e.message);
    srv.tools = []; // 빈 배열로 초기화해서 오류 방지
  }
}

function killAllServers() {
  servers.forEach(s => {
    try {
      if (s.proc && s.proc.kill) {
        s.proc.kill();
        console.log(`[${s.id}] Server stopped`);
      }
    } catch (err) {
      console.error(`[${s.id}] Error stopping server:`, err.message);
    }
  });
}

async function selectProjectFolder() {
  const r = await dialog.showOpenDialog({ properties: ["openDirectory"] });
  return r.canceled ? null : r.filePaths[0];
}

function getServers() { return servers; }

module.exports = { spawnAllServers, killAllServers, selectProjectFolder, getServers };