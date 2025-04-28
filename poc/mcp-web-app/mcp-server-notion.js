#!/usr/bin/env node
// MCP-Notion 서버: Notion API를 JSON-RPC로 노출
require('dotenv').config();
const { Client } = require('@notionhq/client');
const notion = new Client({ auth: process.env.NOTION_API_KEY });

// 환경변수
const DEFAULT_PARENT_ID = process.env.NOTION_PARENT_ID;
const useDatabase = process.env.NOTION_PARENT_IS_DATABASE === 'true';
// DB 생성 시 제목 속성명 (데이터베이스 스키마에 맞춰 설정)
const DB_TITLE_PROPERTY = process.env.NOTION_DB_TITLE_PROPERTY || 'Name';

// MCP 툴 메타데이터 정의
const tools = [
  {
    name: 'createPage',
    description: '노션에 새 페이지를 생성합니다.',
    input: {
      type: 'object',
      properties: {
        parentId: { type: 'string' },
        title:    { type: 'string' },
        content:  { type: ['array', 'string'], items: { type: 'string' } }
      }
    }
  },
  {
    name: 'updatePage',
    description: '노션 페이지의 제목 또는 내용을 수정합니다.',
    input: {
      type: 'object',
      properties: {
        pageId:  { type: 'string' },
        title:   { type: 'string' },
        content: { type: ['array', 'string'], items: { type: 'string' } }
      },
      required: ['pageId']
    }
  },
  {
    name: 'deletePage',
    description: '노션 페이지를 아카이브(삭제) 처리합니다.',
    input: { type: 'object', properties: { pageId: { type: 'string' } }, required: ['pageId'] }
  },
  { name: 'list_tools', description: '툴 목록 반환', input: { type: 'object', properties: {} } }
];

let buffer = '';
process.stdin.on('data', chunk => {
  buffer += chunk.toString();
  let idx;
  while ((idx = buffer.indexOf('\n')) !== -1) {
    const line = buffer.slice(0, idx).trim();
    buffer = buffer.slice(idx + 1);
    if (!line) continue;

    let req;
    try { req = JSON.parse(line); } catch { continue; }
    const { id, method, params } = req;

    (async () => {
      let result, error;
      try {
        if (method === 'list_tools') {
          result = tools;

        } else if (method === 'call_tool') {
          const { name: toolName, arguments: args } = params;
          switch (toolName) {
            case 'createPage': {
              // 인자 확인
              const rawParent = args.parentId || DEFAULT_PARENT_ID;
              if (!rawParent) throw new Error('parentId가 필요합니다.');
              const parentId = rawParent.replace(/^.*-/, '');

              // 제목, 내용 준비
              const titleText = args.title != null ? String(args.title) : '';
              const rawContent = args.content;
              const contents = Array.isArray(rawContent)
                ? rawContent.map(String)
                : rawContent != null
                  ? [String(rawContent)]
                  : [];

              if (useDatabase) {
                // 데이터베이스 아래에 생성: properties만, children 별도 추가
                const page = await notion.pages.create({
                  parent: { database_id: parentId },
                  properties: {
                    [DB_TITLE_PROPERTY]: {
                      title: [ { text: { content: titleText || '제목 없음' } } ]
                    }
                  }
                });
                if (contents.length) {
                  await notion.blocks.children.append({
                    block_id: page.id,
                    children: contents.map(text => ({
                      object: 'block', type: 'paragraph',
                      paragraph: { text: [ { type: 'text', text: { content: text } } ] }
                    }))
                  });
                }
                result = { page, content: contents };

              } else {
                // 페이지 아래에 생성: properties + children 직접 포함
                const page = await notion.pages.create({
                  parent: { page_id: parentId },
                  properties: {
                    title: { title: [ { text: { content: titleText || '제목 없음' } } ] }
                  },
                  children: contents.map(text => ({
                    object: 'block', type: 'paragraph',
                    paragraph: { text: [ { type: 'text', text: { content: text } } ] }
                  }))
                });
                result = { page, content: contents };
              }
              break;
            }

            case 'updatePage': {
              const pageId = args.pageId;
              if (!pageId) throw new Error('pageId가 필요합니다.');
              const properties = {};
              if (args.title != null) properties.title = { title: [ { text: { content: String(args.title) } } ] };
              result = await notion.pages.update({ page_id: pageId, properties });
              break;
            }

            case 'deletePage': {
              const pageId = args.pageId;
              if (!pageId) throw new Error('pageId가 필요합니다.');
              result = await notion.pages.update({ page_id: pageId, archived: true });
              break;
            }

            default:
              throw new Error(`Unknown tool: ${toolName}`);
          }
        } else {
          throw new Error(`Unknown method: ${method}`);
        }
      } catch (e) {
        error = { code: -32000, message: e.message };
      }
      process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id, result, error }) + '\n');
    })();
  }
});
