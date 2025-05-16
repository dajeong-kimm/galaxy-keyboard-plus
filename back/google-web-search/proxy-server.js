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
      GOOGLE_API_KEY: process.env.GOOGLE_SEARCH_API_KEY,
      GOOGLE_SEARCH_ENGINE_ID: process.env.GOOGLE_SEARCH_ENGINE_ID,
    },
  });

  childProcess.stdout.on("data", (data) => {
    console.log(`Google MCP Output: ${data}`);
  });

  // 수정 필요 코드 (로그 레벨 조정)
  childProcess.stderr.on("data", (data) => {
    console.log(`Google MCP Log: ${data}`);
  });

  childProcess.on("close", (code) => {
    console.log(`Google MCP 종료 (코드: ${code})`);
    if (code !== 0 && !isShuttingDown) {
      onsole.log("프로세스 비정상 종료, 5초 후 재시작 시도...");
      setTimeout(startMCPProcess, 5000);
    }
  });

  childProcess.on("error", (err) => {
    console.error("Google MCP 프로세스 오류:", err);
  });

  return childProcess;
}
// 초기 프로세스 시작
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
    console.log("[Google MCP] Received HTTP request:", JSON.stringify(request));

    // 요청 형식 변환
    let mcpRequest;

    // 메서드 이름 변환 로직 추가
    if (request.method === "call_tool") {
      // Brave 검색 도구 처리
      mcpRequest = {
        jsonrpc: "2.0",
        id: request.id,
        method: "tools/call",
        params: request.params,
      };
    } else if (request.method === "list_tools") {
      mcpRequest = {
        jsonrpc: "2.0",
        id: request.id,
        method: "tools/list",
        params: request.params || {},
      };
    } else {
      mcpRequest = request;
    }

    console.log(
      "[Google MCP] Transformed request:",
      JSON.stringify(mcpRequest)
    );

    // MCP 서버로 요청 전송
    childProcess.stdin.write(JSON.stringify(mcpRequest) + "\n");

    // 응답 대기 (ID 기반 매칭으로 개선)
    const responsePromise = new Promise((resolve, reject) => {
      const requestId = mcpRequest.id;
      const timeout = setTimeout(() => {
        console.error("[Google MCP] MCP 요청 시간 초과");
        removeResponseHandler();
        reject(new Error("Request timed out"));
      }, 50000);

      let responseBuffer = "";

      const responseHandler = (data) => {
        responseBuffer += data.toString();
        try {
          // 완전한 JSON 응답을 찾기 위한 시도
          let response;
          let validJsonFound = false;

          try {
            response = JSON.parse(responseBuffer);
            validJsonFound = true;
          } catch (err) {
            // 아직 완전한 JSON이 아님, 더 많은 데이터 기다리기
          }
          if (validJsonFound && response.id === requestId) {
            clearTimeout(timeout);
            removeResponseHandler();
            console.log(
              "[Google MCP] Received MCP response:",
              JSON.stringify(response)
            );
            resolve(response);
          } else if (validJsonFound) {
            // 유효한 JSON이지만 다른 요청에 대한 응답일 수 있음
            console.log(
              "[Google MCP] Received response for different request, still waiting"
            );
            responseBuffer = "";
          }
        } catch (e) {
          console.error("[Google MCP] Error parsing response:", e);
        }
      };

      const removeResponseHandler = () => {
        childProcess.stdout.removeListener("data", responseHandler);
      };

      childProcess.stdout.on("data", responseHandler);
    });
    const response = await responsePromise;
    console.log(
      "[Google MCP] Sending HTTP response:",
      JSON.stringify(response)
    );
    res.json(response);
  } catch (error) {
    console.error("[Google MCP] Error processing request:", error);
    res.status(500).json({
      jsonrpc: "2.0",
      id: req.body.id || null,
      error: {
        code: -32000,
        message: `Internal server error: ${error.message}`,
      },
    });
  }
});

// 헬스 체크 엔드포인트
app.get("/health", (req, res) => {
  console.log(`[${new Date().toISOString()}] /health 요청 옴`);
  res.status(200).json({ status: "OK" });
});

// API 키 상태 확인
console.log(
  `GOOGLE_API_KEY is ${
    process.env.GOOGLE_SEARCH_API_KEY ? "set" : "NOT set"
  }, GOOGLE_SEARCH_ENGINE_ID is ${
    process.env.GOOGLE_SEARCH_ENGINE_ID ? "set" : "NOT set"
  }`
);

app.listen(port, () => {
  console.log(`Google Search MCP Proxy running on port ${port}`);
});
