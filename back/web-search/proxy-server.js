// ES 모듈 가져오기
import express from 'express';
import { spawn } from 'child_process';
import bodyParser from 'body-parser';

// 서버 설정
const app = express();
app.use(bodyParser.json());
const port = 8100;

// MCP 서버 프로세스 시작
const childProcess = spawn('node', ['build/index.js']);

// 로그 처리
childProcess.stdout.on('data', (data) => {
    console.log(`MCP Output: ${data}`);
});

childProcess.stderr.on('data', (data) => {
    console.log(`MCP Log: ${data}`);
});

// 종료 시그널 처리
process.on('SIGINT', () => {
    childProcess.kill();
    process.exit(0);
});

process.on('SIGTERM', () => {
    childProcess.kill();
    process.exit(0);
});

// JSON-RPC 요청 처리
app.post('/', async (req, res) => {
    try {
        const request = req.body;
        console.log('Received HTTP request:', JSON.stringify(request));

        // MCP 서버로 요청 전송
        childProcess.stdin.write(JSON.stringify(request) + '\n');

        // 응답 대기
        const responsePromise = new Promise((resolve, reject) => {
            const timeout = setTimeout(() => {
                reject(new Error('Request timed out'));
            }, 30000);

            childProcess.stdout.once('data', (data) => {
                clearTimeout(timeout);
                try {
                    const response = JSON.parse(data.toString());
                    resolve(response);
                } catch (e) {
                    reject(new Error(`Invalid JSON response: ${data.toString()}`));
                }
            });
        });

        const response = await responsePromise;
        console.log('Sending HTTP response:', JSON.stringify(response));
        res.json(response);
    } catch (error) {
        console.error('Error processing request:', error);
        res.status(500).json({
            jsonrpc: '2.0',
            id: req.body.id || null,
            error: {
                code: -32000,
                message: `Internal server error: ${error.message}`
            }
        });
    }
});

// 헬스 체크 엔드포인트
app.get('/health', (req, res) => {
    res.status(200).send('OK');
});

// 서버 시작
app.listen(port, () => {
    console.log(`Web Search HTTP Proxy running on port ${port}`);
});