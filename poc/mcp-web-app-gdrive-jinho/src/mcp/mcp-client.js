const axios = require("axios");
const { getServers } = require("./server-manager");
require("dotenv").config();

async function runCommand(text) {
  const key = process.env.OPENAI_API_KEY;
  const { data } = await axios.post(
    "https://api.openai.com/v1/chat/completions",
    {
      model: "gpt-4o-mini",
      messages: [
        { role: "system", content: "You can call tools if needed." },
        { role: "user", content: text }
      ],
      tools: getServers()
        .filter(s => Array.isArray(s.tools) && s.tools.length)
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
    const params = JSON.parse(call.arguments).params || {};
    const rpcRes = await srv.rpc.call("call_tool", { name: method, arguments: params });
    return { result: JSON.stringify(rpcRes) };
  }
  return { result: msg.content };
}

module.exports = { runCommand };
