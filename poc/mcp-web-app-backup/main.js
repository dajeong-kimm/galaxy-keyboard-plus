require('dotenv').config();
const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const { spawn } = require('child_process');
const { createAgent } = require('./lib/agent');

// 1) npx로 띄울 MCP 서버 목록과 포트
const mcpServers = [
  { pkg: '@modelcontextprotocol/server-filesystem', port: 3000 },
  { pkg: '@modelcontextprotocol/server-shell', port: 3001 },
  { pkg: '@modelcontextprotocol/server-http', port: 3003 },
  { pkg: '@modelcontextprotocol/server-git', port: 3004 },
  { pkg: '@modelcontextprotocol/server-database', port: 3005 },
  { pkg: '@modelcontextprotocol/server-github', port: 3007 },
  { pkg: '@modelcontextprotocol/server-gsuite', port: 3008 },
  { pkg: '@modelcontextprotocol/server-slack', port: 3009 },
  { pkg: '@modelcontextprotocol/server-redis', port: 3011 },
  { pkg: '@modelcontextprotocol/server-quickchart', port: 3012 },
];

// 2) 각 서버를 npx로 기동
function startMcpServers() {
  for (const { pkg, port } of mcpServers) {
    const proc = spawn('npx', ['-y', pkg, '--port', String(port)], {
      shell: true,
      stdio: 'inherit',
    });
    proc.on('exit', (code, sig) =>
      console.log(`[MCP ${pkg}] exited (code=${code}, signal=${sig})`)
    );
    console.log(`[MCP ${pkg}] started on port ${port}`);
  }
}

// 3) 외부 Python 서버(EXE) 자동 기동
let gdocsProc, gdriveProc;
function startGdocs() {
  const exe = path.join(process.resourcesPath, 'gdocs-mcp-server.exe');
  gdocsProc = spawn(exe, [], { stdio: 'inherit' });
  gdocsProc.on('exit', (c, s) => console.log(`[GDOCS] exited (${c},${s})`));
  console.log('[GDOCS] server started');
}
function startGdrive() {
  const exe = path.join(process.resourcesPath, 'gdrive-mcp-server.exe');
  gdriveProc = spawn(exe, [], { stdio: 'inherit' });
  gdriveProc.on('exit', (c, s) => console.log(`[GDRIVE] exited (${c},${s})`));
  console.log('[GDRIVE] server started');
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1000,
    height: 700,
    webPreferences: { preload: path.join(__dirname, 'preload.js') },
  });
  win.loadFile('renderer/index.html');
}

app.whenReady().then(() => {
  startMcpServers(); // ← 모든 npx MCP 서버 기동
  startGdocs(); // ← Google Docs MCP 서버(EXE)
  startGdrive(); // ← Google Drive MCP 서버(EXE)
  createWindow();
});

app.on('will-quit', () => {
  // 종료 시 모든 프로세스 kill
  [gdocsProc, gdriveProc].forEach((p) => p && p.kill());
  // npx로 실행한 MCP 서버들은 shell로 띄웠기 때문에 자동 종료됩니다.
});

ipcMain.handle('run-command', async (_, { command, projectPath }) => {
  const agent = await createAgent();
  const res = await agent.run({
    instruction: command,
    context: { cwd: projectPath },
  });
  return { result: res.output ?? res };
});
