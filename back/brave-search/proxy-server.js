import express from "express";
import { spawn } from "child_process";
import bodyParser from "body-parser";

import dotenv from "dotenv";

// 서버 설정
const app = express();
app.use(bodyParser.json());
const port = 8100;
dotenv.config({ path: ".env.prod" });

// Brave Search MCP 서버 프로세스 시작 및 관리
let childProcess;
let isShuttingDown = false;

function startMCPProcess() {
  childProcess = spawn("npx", [
    "@modelcontextprotocol/server-brave-search",
    "--foreground",
  ]);

  // 로그 처리
  childProcess.stdout.on("data", (data) => {
    console.log(`Brave MCP Output: ${data}`);
  });

  childProcess.stderr.on("data", (data) => {
    console.log(`Brave MCP Log: ${data}`);
  });

  // 프로세스 종료 처리 및 자동 재시작
  childProcess.on("close", (code) => {
    console.log(`Brave MCP 프로세스 종료 (코드: ${code})`);
    if (code !== 0 && !isShuttingDown) {
      console.log("프로세스 비정상 종료, 5초 후 재시작 시도...");
      setTimeout(startMCPProcess, 5000);
    }
  });

  childProcess.on("error", (err) => {
    console.error("Brave MCP 프로세스 오류:", err);
  });

  return childProcess;
}

// 초기 프로세스 시작
childProcess = startMCPProcess();

// 앱 종료 처리
process.on("SIGINT", () => {
  isShuttingDown = true;
  childProcess.kill();
  process.exit(0);
});

process.on("SIGTERM", () => {
  isShuttingDown = true;
  childProcess.kill();
  process.exit(0);
});

// JSON-RPC 요청 처리
app.post("/", async (req, res) => {
  try {
    const request = req.body;
    console.log("Received HTTP request:", JSON.stringify(request));

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
        method: "listTools",
        params: request.params || {},
      };
    } else {
      mcpRequest = request;
    }

    console.log("Transformed request:", JSON.stringify(mcpRequest));

    // MCP 서버로 요청 전송
    childProcess.stdin.write(JSON.stringify(mcpRequest) + "\n");

    // 응답 대기 (ID 기반 매칭으로 개선)
    const responsePromise = new Promise((resolve, reject) => {
      const requestId = mcpRequest.id;
      const timeout = setTimeout(() => {
        console.error("MCP 요청 시간 초과");
        removeResponseHandler();
        reject(new Error("Request timed out"));
      }, 30000);

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
            console.log("Received MCP response:", JSON.stringify(response));
            resolve(response);
          } else if (validJsonFound) {
            // 유효한 JSON이지만 다른 요청에 대한 응답일 수 있음
            console.log(
              "Received response for different request, still waiting"
            );
            responseBuffer = "";
          }
        } catch (e) {
          console.error("Error parsing response:", e);
        }
      };

      const removeResponseHandler = () => {
        childProcess.stdout.removeListener("data", responseHandler);
      };

      childProcess.stdout.on("data", responseHandler);
    });

    const response = await responsePromise;
    console.log("Sending HTTP response:", JSON.stringify(response));
    res.json(response);
  } catch (error) {
    console.error("Error processing request:", error);
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
  res.status(200).send("OK");
});

// 도구 목록 확인용 엔드포인트
app.get("/tools", async (req, res) => {
  try {
    const listToolsRequest = {
      jsonrpc: "2.0",
      id: "tools-request",
      method: "listTools",
      params: {},
    };

    // MCP 서버로 요청 전송
    childProcess.stdin.write(JSON.stringify(listToolsRequest) + "\n");

    // 응답 대기
    const responsePromise = new Promise((resolve, reject) => {
      const requestId = listToolsRequest.id;
      const timeout = setTimeout(() => {
        removeResponseHandler();
        reject(new Error("Request timed out"));
      }, 10000);

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
            resolve(response);
          } else if (validJsonFound) {
            // 유효한 JSON이지만 다른 요청에 대한 응답일 수 있음
            responseBuffer = "";
          }
        } catch (e) {
          console.error("Error parsing response:", e);
        }
      };

      const removeResponseHandler = () => {
        childProcess.stdout.removeListener("data", responseHandler);
      };

      childProcess.stdout.on("data", responseHandler);
    });

    const response = await responsePromise;
    res.json(response);
  } catch (error) {
    console.error("Error fetching tools list:", error);
    res.status(500).json({
      error: `Failed to fetch tools list: ${error.message}`,
    });
  }
});

// API 키 상태 확인
console.log(
  `BRAVE_API_KEY is ${process.env.BRAVE_API_KEY ? "set" : "NOT set"}`
);

// 서버 시작
app.listen(port, () => {
  console.log(`Brave Search HTTP Proxy running on port ${port}`);
});
