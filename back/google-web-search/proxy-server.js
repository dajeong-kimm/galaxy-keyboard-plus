import express from "express";
import { spawn } from "child_process";
import bodyParser from "body-parser";

const app = express();
app.use(bodyParser.json());
const port = 8150;

let childProcess;
let isShuttingDown = false;

function startMCPProcess() {
  childProcess = spawn("npm", ["run", "start"], {
    cwd: "/app",
    env: {
      ...process.env,
      GOOGLE_SEARCH_API_KEY: process.env.GOOGLE_SEARCH_API_KEY,
      GOOGLE_SEARCH_ENGINE_ID: process.env.GOOGLE_SEARCH_ENGINE_ID
    }
  });

  childProcess.stdout.on("data", (data) => {
    console.log(`Google MCP Output: ${data}`);
  });

  // 수정 필요 코드 (로그 레벨 조정)
    childProcess.stderr.on("data", (data) => {
    console.log(`[MCP Server] ${data}`); 
    });

  childProcess.on("close", (code) => {
    console.log(`Google MCP 종료 (코드: ${code})`);
    if (code !== 0 && !isShuttingDown) {
      setTimeout(startMCPProcess, 5000);
    }
  });

  return childProcess;
}

childProcess = startMCPProcess();

// 종료 시그널 처리
process.on("SIGINT", () => {
  isShuttingDown = true;
  childProcess.kill();
  process.exit(0);
});

// JSON-RPC 엔드포인트
app.post("/", async (req, res) => {
  try {
    const request = req.body;
    const mcpRequest = {
      jsonrpc: "2.0",
      id: request.id,
      method: "tools/call",
      params: {
        tool: request.method,
        ...request.params
      }
    };

    childProcess.stdin.write(JSON.stringify(mcpRequest) + "\n");

    const response = await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error("Timeout")), 30000);
      
      const listener = (data) => {
        try {
          const response = JSON.parse(data.toString());
          if (response.id === request.id) {
            clearTimeout(timeout);
            resolve(response);
          }
        } catch (e) {}
      };

      childProcess.stdout.once('data', listener);
    });

    res.json(response);
  } catch (error) {
    res.status(500).json({
      error: {
        code: -32000,
        message: error.message
      }
    });
  }
});

// 헬스 체크 엔드포인트
app.get("/health", (req, res) => {
  res.status(200).json({ status: "OK" });
});

app.listen(port, () => {
  console.log(`Google Search MCP Proxy running on port ${port}`);
});
