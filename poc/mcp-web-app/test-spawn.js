// test-spawn.js
const spawn = require('cross-spawn');
const path = require('path');

// 서버 스크립트 경로 확인 (package.json 위치 기준)
const serverModulePath = '@modelcontextprotocol/server-filesystem';
let cliPath;
try {
    // require.resolve 사용 시 모듈의 package.json을 찾고, 이를 기반으로 dist/index.js 경로 구성 시도
    const modulePackageJsonPath = require.resolve(`${serverModulePath}/package.json`);
    const moduleBasePath = path.dirname(modulePackageJsonPath);
    cliPath = path.join(moduleBasePath, 'dist', 'index.js');
} catch (e) {
    console.error(`Error resolving path for ${serverModulePath}: ${e.message}`);
    // 대체 경로 시도 (현재 작업 디렉토리 기준 node_modules) - 환경에 따라 조정 필요
    cliPath = path.join(__dirname, 'node_modules', serverModulePath.replace(/\//g, path.sep) , 'dist', 'index.js');
    console.warn(`Trying fallback path: ${cliPath}`);
}

if (!require('fs').existsSync(cliPath)) {
    console.error(`Server script not found at resolved path: ${cliPath}`);
    process.exit(1);
}

const allowedDir = process.cwd(); // 테스트할 디렉토리 (현재 디렉토리 사용)

console.log(`Spawning server: node ${cliPath} ${allowedDir}`);
const proc = spawn(process.execPath, [cliPath, allowedDir], {
    stdio: ['pipe', 'pipe', 'pipe'] // Electron과 동일하게 파이프 사용
});

console.log(`Spawned server process with PID: ${proc.pid}`);

let responseReceived = false;
const responseTimeout = setTimeout(() => {
    if (!responseReceived) {
        console.error('<<< TEST TIMEOUT: No JSON-RPC response received after 15 seconds.');
        proc.kill(); // 시간 초과 시 프로세스 종료
    }
}, 15000); // 15초 타임아웃 설정

proc.stdout.on('data', (data) => {
    const output = data.toString();
    console.log(`<<< STDOUT:\n${output}`);
    // 간단하게 JSON 응답인지 확인 (id 포함 여부 등)
    if (output.includes('"jsonrpc":') && output.includes('"id":"test_pipe"')) {
        responseReceived = true;
        clearTimeout(responseTimeout); // 응답 받으면 타임아웃 클리어
        console.log('<<< JSON-RPC response detected!');
        proc.kill(); // 테스트 완료 후 종료
    }
});

proc.stderr.on('data', (data) => {
    console.error(`<<< STDERR:\n${data.toString()}`);
});

proc.on('exit', (code, signal) => {
    clearTimeout(responseTimeout);
    console.log(`Server exited with code ${code}, signal ${signal}`);
});

proc.on('error', (err) => {
    clearTimeout(responseTimeout);
    console.error(`Failed to start server process: ${err}`);
});

// 서버가 시작될 시간을 약간 기다린 후 요청 전송
setTimeout(() => {
    // --- 호출할 메소드 이름 (이 부분은 여전히 찾아야 함) ---
    // const methodName = "mcp_list_tools"; // 예시 1
    const methodName = "tools/list";     // 예시 2 (원본 시도)
    // const methodName = "system.listMethods"; // 예시 3
    // const methodName = "rpc.discover"; // 예시 4

    const request = JSON.stringify({ jsonrpc: "2.0", id: "test_pipe", method: methodName, params: {} }) + "\n";
    console.log(`>>> Sending request: ${request.trim()}`);
    try {
        proc.stdin.write(request);
    } catch (e) {
        console.error(`Error writing to stdin: ${e}`);
    }
}, 3000); // 3초 후 요청 전송