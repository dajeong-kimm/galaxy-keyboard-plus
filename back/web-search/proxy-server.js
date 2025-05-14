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

        // 요청 형식 변환
        let mcpRequest;

        // 메서드 이름 변환 로직 추가
        if (request.method === 'callTool') {
            // SDK 내부에서 사용하는 메서드 이름으로 변환
            const toolName = request.params.name; // 예: "search"
            mcpRequest = {
                jsonrpc: "2.0",
                id: request.id,
                method: toolName, // 도구 이름을 직접 메서드로 사용
                params: request.params.arguments // arguments 객체를 직접 전달
            };
        } else if (request.method === 'listTools') {
            // listTools를 ListToolsRequestSchema로 변환
            mcpRequest = {
                jsonrpc: "2.0",
                id: request.id,
                method: "listTools", // 대문자 시작이나 RequestSchema 붙이지 않음
                params: request.params || {}
            };
        } else if (request.method === 'search') {
            // 'search' 메서드를 SDK 내부 형식으로 변환
            mcpRequest = {
                jsonrpc: "2.0",
                id: request.id || Math.random().toString(36).substring(2, 9),
                method: "search", // 직접 search 호출
                params: request.params
            };
        } else {
            // 다른 메서드는 그대로 전달
            mcpRequest = request;
        }

        console.log('Transformed request:', JSON.stringify(mcpRequest));

        // MCP 서버로 요청 전송
        childProcess.stdin.write(JSON.stringify(mcpRequest) + '\n');

        // 응답 대기
        const responsePromise = new Promise((resolve, reject) => {
            const timeout = setTimeout(() => {
                reject(new Error('Request timed out'));
            }, 30000);

            childProcess.stdout.once('data', (data) => {
                clearTimeout(timeout);
                try {
                    const mcpResponse = JSON.parse(data.toString());
                    console.log('Received MCP response:', JSON.stringify(mcpResponse));

                    // 응답 형식 조정 - 필요한 경우
                    let apiResponse = mcpResponse;
                    if (mcpResponse.result && mcpResponse.result.content && mcpResponse.result.content[0] && mcpResponse.result.content[0].text) {
                        try {
                            // 텍스트에서 JSON 결과 추출
                            const results = JSON.parse(mcpResponse.result.content[0].text);
                            apiResponse = {
                                jsonrpc: "2.0",
                                id: mcpResponse.id,
                                result: results
                            };
                        } catch (e) {
                            // JSON 파싱 실패 시 원래 응답 유지
                            console.warn('Failed to parse content as JSON:', e);
                        }
                    }

                    resolve(apiResponse);
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

// 도구 목록 확인용 엔드포인트
app.get('/tools', async (req, res) => {
    try {
        // 도구 목록 요청 생성 - 메서드 이름 변환
        const listToolsRequest = {
            jsonrpc: "2.0",
            id: "tools-request",
            method: "ListToolsRequestSchema", // 올바른 내부 메서드 이름으로 변경
            params: {}
        };

        // MCP 서버로 요청 전송
        childProcess.stdin.write(JSON.stringify(listToolsRequest) + '\n');

        // 응답 대기
        const responsePromise = new Promise((resolve, reject) => {
            const timeout = setTimeout(() => {
                reject(new Error('Request timed out'));
            }, 10000);

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
        res.json(response);
    } catch (error) {
        console.error('Error fetching tools list:', error);
        res.status(500).json({
            error: `Failed to fetch tools list: ${error.message}`
        });
    }
});

// 서버 시작
app.listen(port, () => {
    console.log(`Web Search HTTP Proxy running on port ${port}`);
});