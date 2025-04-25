#!/usr/bin/env node
require('dotenv').config();               // NOTION_API_KEY 로드
const { Client } = require('@notionhq/client');
const rpc = require('json-rpc-stdio');    // JSON-RPC stdin/stdout 라이브러리

const notion = new Client({ auth: process.env.NOTION_API_KEY });

// 1) 툴 메타데이터 정의
const tools = [
  {
    name: 'createPage',
    description: '새로운 노션 페이지를 생성합니다.',
    input: {
      type: 'object',
      properties: {
        parentId: { type: 'string' },
        title:    { type: 'string' },
        content:  { type: 'array', items: { type: 'string' } }
      },
      required: ['parentId','title']
    }
  },
  {
    name: 'updatePage',
    description: '기존 노션 페이지의 속성이나 내용을 수정합니다.',
    input: {
      type: 'object',
      properties: {
        pageId: { type: 'string' },
        title:  { type: 'string' },
        content:{ type: 'array', items: { type: 'string' } }
      },
      required: ['pageId']
    }
  },
  {
    name: 'deletePage',
    description: '노션 페이지를 아카이브(삭제) 처리합니다.',
    input: {
      type: 'object',
      properties: {
        pageId: { type: 'string' }
      },
      required: ['pageId']
    }
  },
  {
    name: 'listTools',        // MCP 규격용 리스트
    description: '툴 목록을 반환합니다.',
    input: { type: 'object', properties: {} }
  }
];

// 2) JSON-RPC 서버 등록
rpc(
  {
    list_tools: async () => tools,
    // 노션 API 호출
    call_tool: async ({ name, arguments: params }) => {
      switch (name) {
        case 'createPage':
          return notion.pages.create({
            parent: { page_id: params.parentId },
            properties: {
              title: { title: [{ text: { content: params.title } }] }
            },
            children: params.content?.map(text => ({
              object: 'block',
              type: 'paragraph',
              paragraph: { text: [{ type: 'text', text: { content: text } }] }
            }))
          });
        case 'updatePage':
          // 필요에 따라 속성 업데이트 + 블록 편집 로직 추가
          await notion.pages.update({ page_id: params.pageId, properties: {
            title: { title:[{ text:{ content: params.title }}] }
          }});
          // …블록 교체 로직 등…
          return { success: true };
        case 'deletePage':
          // 노션은 실제 삭제가 아니라 archive 처리
          return notion.pages.update({ page_id: params.pageId, archived: true });
        default:
          throw new Error(`Unknown tool: ${name}`);
      }
    }
  },
  {
    // 옵션: 자동으로 list_tools 메서드 매핑
    methods: { list_tools: 'list_tools', call_tool: 'call_tool' }
  }
);
