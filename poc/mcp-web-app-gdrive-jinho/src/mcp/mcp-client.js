const axios = require("axios");
const { getServers } = require("./server-manager");
require("dotenv").config();

async function runCommand(text) {
  const key = process.env.OPENAI_API_KEY;
  
  // 사용 가능한 서버와 도구 확인
  const availableServers = getServers().filter(s => s && s.rpc && Array.isArray(s.tools) && s.tools.length);
  
  if (availableServers.length === 0) {
    return { 
      error: "사용 가능한 MCP 서버가 없습니다. 서버가 실행 중인지 확인하세요." 
    };
  }
  
  const { data } = await axios.post(
    "https://api.openai.com/v1/chat/completions",
    {
      model: "gpt-4o-mini",
      messages: [
        { role: "system", content: "You can call tools if needed." },
        { role: "user", content: text }
      ],
      tools: availableServers
        .flatMap(s => s.tools.map(t => ({
          type: "function",
          function: {
            name: `${s.id}_${t.name}`,
            description: t.description,
            parameters: t.inputSchema || {}
          }
        }))),
      tool_choice: "auto"
    },
    { headers: { Authorization: `Bearer ${key}` } }
  );

  const msg = data.choices[0].message;
  if (msg.tool_calls?.length) {
    const call = msg.tool_calls[0].function;
    const [srvId, method] = call.name.split("_", 2);
    const srv = getServers().find(s => s.id === srvId);
    
    if (!srv || !srv.rpc) {
      return { error: `서버 ${srvId}를 찾을 수 없거나 RPC 클라이언트가 초기화되지 않았습니다.` };
    }
    
    const params = JSON.parse(call.arguments).params || {};
    const rpcRes = await srv.rpc.call("call_tool", { name: method, arguments: params });
    return { result: JSON.stringify(rpcRes) };
  }
  return { result: msg.content };
}

module.exports = { runCommand };
