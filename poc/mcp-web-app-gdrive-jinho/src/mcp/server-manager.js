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
      const msg = JSON.parse(line);
      const p = this.pending.get(msg.id);
      if (p) { this.pending.delete(msg.id); p.resolve(msg.result); }
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
  const binPath = path.join(__dirname, "../../node_modules/.bin", def.bin);
  if (!fs.existsSync(binPath)) {
    console.error(`Binary not found: ${binPath}`); return;
  }
  console.log(`Spawning server: ${def.id}`);
  const proc = spawn(binPath, def.args || [], {
    cwd: process.cwd(),
    stdio: ["pipe","pipe","pipe"],
    env: {...process.env, ...def.env},
  });
  const rpc = new StdioRPCClient(proc, def.id);
  const srv = { id: def.id, proc, rpc, tools: [] };
  servers.push(srv);
  try {
    const list = await rpc.call("list_tools").catch(() => rpc.call("tools/list"));
    srv.tools = Array.isArray(list) ? list : list.tools;
    console.log(`[${def.id}] tools loaded`);
  } catch (e) {
    console.warn(`[${def.id}] failed to load tools`, e.message);
  }
}

function killAllServers() {
  servers.forEach(s => s.proc.kill());
}

async function selectProjectFolder() {
  const r = await dialog.showOpenDialog({ properties: ["openDirectory"] });
  return r.canceled ? null : r.filePaths[0];
}

function getServers() { return servers; }

module.exports = { spawnAllServers, killAllServers, selectProjectFolder, getServers };
