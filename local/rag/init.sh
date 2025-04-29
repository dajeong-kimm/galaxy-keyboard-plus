#!/bin/bash
# ChromaDB 및 SQLite 초기화 스크립트

cd ~/ssafy/S12P31E201/local/rag


# ChromaDB 디렉토리 초기화
if [ -d "./chroma_db" ]; then
  rm -rf ./chroma_db
  echo "기존 ChromaDB 디렉토리 삭제됨"
fi

mkdir -p ./chroma_db
echo "새 ChromaDB 디렉토리 생성됨"

# SQLite 초기화
if [ -f "./sqlite.db" ]; then
  rm ./sqlite.db
  echo "기존 SQLite DB 삭제됨"
fi

echo "모든 DB가 초기화되었습니다. 이제 'python main.py'를 실행하여 서버를 시작하세요."
