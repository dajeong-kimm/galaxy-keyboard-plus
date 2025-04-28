const { Agent, Tools } = require('openai-agents-mcp');
require('dotenv').config();

async function createAgent() {
  const tools = [
    // 로컬 Generic MCP 서버
    Tools.filesystem({ server: process.env.MCP_SERVER }),
    Tools.shell({ server: process.env.MCP_SERVER }),
    Tools.docker({ server: process.env.MCP_SERVER }),
    Tools.http({ server: process.env.MCP_SERVER }),
    Tools.git({ server: process.env.MCP_SERVER }),
    Tools.database({ server: process.env.MCP_SERVER }),
    Tools.memory({ server: process.env.MCP_SERVER }),
    Tools.github({ server: process.env.MCP_SERVER }),
    Tools.slack({ server: process.env.MCP_SERVER }),
    Tools.pinecone({ server: process.env.MCP_SERVER }),
    Tools.redis({ server: process.env.MCP_SERVER }),
    Tools.quickchart({ server: process.env.MCP_SERVER }),
    Tools.playwright({ server: process.env.MCP_SERVER }),
    Tools.postman({ server: process.env.MCP_SERVER }),
    Tools.replicate({ server: process.env.MCP_SERVER }),
    Tools.tmdb({ server: process.env.MCP_SERVER }),
    Tools.telegram({ server: process.env.MCP_SERVER }),

    // Google Docs MCP 서버
    Tools.http({
      server: process.env.GDOCS_MCP_SERVER,
      toolName: 'googleDocs',
    }),
    // Google Drive MCP 서버
    Tools.http({
      server: process.env.GDRIVE_MCP_SERVER,
      toolName: 'googleDrive',
    }),
  ];

  return new Agent({
    apiKey: process.env.OPENAI_API_KEY,
    model: 'gpt-4o-mini',
    tools,
  });
}

module.exports = { createAgent };
