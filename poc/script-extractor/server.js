import express from 'express';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { createServer } from 'vite';
import { spawn } from 'child_process';
import path from 'path';

// ES 모듈에서 __dirname 사용하기 위한 설정
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

async function startServer() {
  const app = express();
  
  // Vite 서버 생성
  const vite = await createServer({
    server: { middlewareMode: true },
    appType: 'spa'
  });
  
  // Vite 미들웨어 사용
  app.use(vite.middlewares);
  
  // MCP 서버를 사용하여 유튜브 트랜스크립트 가져오기
  app.get('/api/transcript', async (req, res) => {
    try {
      const videoId = req.query.url;
      const lang = req.query.lang || 'ko';
      
      if (!videoId) {
        return res.status(400).json({ error: '비디오 ID가 필요합니다.' });
      }
      
      console.log(`자막 요청: 비디오 ID ${videoId}, 언어: ${lang}`);
      
      // MCP 서버에 요청할 JSON 데이터
      const requestData = {
        jsonrpc: "2.0",
        id: 1,
        method: "callTool",
        params: {
          name: "get_transcript",
          arguments: {
            url: videoId,
            lang: lang
          }
        }
      };

      try {
        // MCP 서버 모듈 경로 확인
        const modulePath = path.resolve('./node_modules/.bin/mcp-server-youtube-transcript');
        console.log(`모듈 경로: ${modulePath}`);
        
        // MCP 서버 실행
        const mcpProcess = spawn('node', [modulePath], {
          stdio: ['pipe', 'pipe', 'pipe']
        });
        
        let responseData = '';
        let errorData = '';
        
        mcpProcess.stdout.on('data', (data) => {
          responseData += data.toString();
          console.log("MCP 서버 응답:", data.toString());
        });
        
        mcpProcess.stderr.on('data', (data) => {
          errorData += data.toString();
          console.log("MCP 서버 로그:", data.toString());
        });
        
        // 오류 처리
        mcpProcess.on('error', (error) => {
          console.error("MCP 서버 실행 오류:", error);
          return res.status(500).json({
            error: `MCP 서버 실행 오류: ${error.message}`
          });
        });
        
        // 프로세스 종료 처리
        mcpProcess.on('close', (code) => {
          console.log(`MCP 서버 프로세스 종료, 코드: ${code}`);
          
          if (code !== 0) {
            console.error("MCP 서버 오류:", errorData);
            return res.status(500).json({
              error: `MCP 서버가 오류 코드 ${code}로 종료됨`
            });
          }
          
          try {
            // JSON 응답 파싱
            const jsonResponse = JSON.parse(responseData);
            
            // 오류 체크
            if (jsonResponse.error) {
              return res.status(500).json({
                error: jsonResponse.error.message || "MCP 서버 오류"
              });
            }
            
            // 결과 처리
            if (jsonResponse.result && 
                jsonResponse.result.toolResult && 
                jsonResponse.result.toolResult.content && 
                jsonResponse.result.toolResult.content[0] && 
                jsonResponse.result.toolResult.content[0].text) {
              // 트랜스크립트 추출 성공
              return res.json({
                transcript: jsonResponse.result.toolResult.content[0].text,
                metadata: jsonResponse.result.toolResult.content[0].metadata
              });
            } else {
              return res.status(404).json({
                error: "트랜스크립트를 찾을 수 없습니다."
              });
            }
          } catch (error) {
            console.error("응답 파싱 오류:", error);
            return res.status(500).json({
              error: `응답 파싱 오류: ${error.message}`,
              rawResponse: responseData.substring(0, 200) // 디버깅용
            });
          }
        });
        
        // 요청 전송
        console.log("MCP 서버에 요청 전송:", JSON.stringify(requestData));
        mcpProcess.stdin.write(JSON.stringify(requestData) + '\\n');
        mcpProcess.stdin.end();
        
      } catch (error) {
        console.error("MCP 서버 통신 오류:", error);
        return res.status(500).json({
          error: `MCP 서버 통신 오류: ${error.message}`
        });
      }
    } catch (error) {
      console.error("API 요청 처리 오류:", error);
      return res.status(500).json({
        error: `API 요청 처리 오류: ${error.message}`
      });
    }
  });
  
  // 서버 시작
  const PORT = 5173;
  app.listen(PORT, () => {
    console.log(`서버가 http://localhost:${PORT} 에서 실행 중입니다.`);
    console.log(`브라우저에서 http://localhost:${PORT} 을 열어 애플리케이션을 사용하세요.`);
  });
}

startServer().catch(err => {
  console.error('서버 시작 중 오류 발생:', err);
});
