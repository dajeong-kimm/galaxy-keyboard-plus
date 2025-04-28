const express = require("express");
const cors = require("cors");
const fs = require("fs");
const path = require("path");
const { exec } = require("child_process");
require("dotenv").config();

const { askLLM } = require("./lib/llm-utils");
const {
  directoryTree,
  readFiles,
  applyEditFile,
  renderMarkdownTree,
} = require("./lib/fs-utils");

const app = express();
app.use(cors());
app.use(express.json());

console.log("mcp-server.js started");

// MCP JSON-RPC 엔드포인트
app.post("/rpc", async (req, res) => {
  const { method, params } = req.body;

  console.log(`[REQUEST] method: ${method}`);
  console.log(`[PARAMS]`, params);

  try {
    switch (method) {
      case "directory_tree": {
        if (!params.path) throw new Error("Missing param: path");
        const tree = directoryTree(params.path);
        const body = renderMarkdownTree(tree);
        const markdown = path.basename(params.path) + "/\n" + body;
        console.log(`directory_tree rendered as markdown`);
        return res.json({ result: markdown });
      }

      case "read_files": {
        if (!params.path) throw new Error("Missing param: path");
        // params.ext 가 문자열이든 배열이든 위 함수에서 다 처리됨
        const files = readFiles(params.path, params.ext);
        console.log(`read_files returned ${files.length} files`);
        return res.json({ result: files });
      }

      case "edit_file": {
        if (!params.path || !params.new_content)
          throw new Error("Missing param: path or new_content");
        console.log(`Editing file: ${params.path}`);
        applyEditFile(params);
        console.log(`File edit completed`);
        return res.json({ result: "ok" });
      }

      case "execute_command": {
        if (!params.cwd || !params.command)
          throw new Error("Missing param: cwd or command");
        console.log(`Executing command: ${params.command} in ${params.cwd}`);
        const result = await runCommand(params.cwd, params.command);
        console.log(
          `Command execution ${result.success ? "succeeded" : "failed"}`
        );
        return res.json({ result });
      }

      case "ask_llm": {
        if (!params.prompt) throw new Error("Missing param: prompt");
        console.log(`Calling Claude LLM...`);
        const result = await askLLM(params.prompt, "claude");
        console.log(`Claude responded`);
        return res.json({ result });
      }

      default:
        console.warn(`Unknown method: ${method}`);
        return res.status(400).json({ error: `Unknown method: ${method}` });
    }
  } catch (err) {
    console.error("[MCP ERROR]", err);
    return res.status(500).json({ error: err.message });
  }
});

// MCP 명령 실행 도구
function runCommand(cwd, command) {
  return new Promise((resolve) => {
    exec(command, { cwd }, (error, stdout, stderr) => {
      const result = {
        success: !error,
        output: stdout + stderr,
      };
      console.log(`[COMMAND OUTPUT]`);
      console.log(result.output);
      resolve(result);
    });
  });
}

const PORT = process.env.MCP_PORT || 3000;
app.listen(PORT, () => {
  console.log(`MCP server running at http://localhost:${PORT}`);
});
