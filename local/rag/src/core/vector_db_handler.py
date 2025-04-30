import chromadb
from chromadb.utils import embedding_functions
import uuid
import json
import datetime
from typing import List, Dict, Any, Optional, Tuple
from langchain.text_splitter import RecursiveCharacterTextSplitter
import os
import pickle
import hashlib
import traceback
from src.config import settings
from openai import OpenAI
from src.config import settings

class VectorDBHandler:
    def __init__(self, db_path: str = settings.chroma_persist_dir):
        """
        벡터 DB 핸들러 초기화
        
        Args:
            db_path: Chroma DB 저장 경로
        """
        # 임베딩 함수 설정 - 한국어 지원 모델 사용
        self.embedding_function = embedding_functions.SentenceTransformerEmbeddingFunction(
            model_name=settings.embed_model,
            device="cpu",  # CPU 명시적 지정
        )
        
        # 임시 저장소 디렉토리 생성
        self.temp_storage_path = settings.temp_storage_path
        os.makedirs(self.temp_storage_path, exist_ok=True)
        
        # Chroma 클라이언트 설정
        self.client = chromadb.PersistentClient(path=db_path)
        
        # 컬렉션 초기화
        self.collections = {
            "conversations": self._get_or_create_collection("conversations", "LLM과의 대화 내역"),
            "documents": self._get_or_create_collection("documents", "작업 문서 내용"),
            "workflows": self._get_or_create_collection("workflows", "MCP 작업 방법 및 순서"),
            "summaries": self._get_or_create_collection("summaries", "작업 요약")
        }
        
        # 텍스트 분할기 설정
        self.text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=settings.chunk_size,
            chunk_overlap=settings.chunk_overlap,
            length_function=len,
            separators=["\n\n", "\n", ". ", " ", ""]  # 한글 문서도 효과적으로 분할
        )

    def _sanitize_metadata(self,metadata):
        """
        ChromaDB에 저장하기 위해 메타데이터 값을 올바른 형식으로 변환
        
        Args:
            metadata: 원본 메타데이터 딕셔너리
            
        Returns:
            변환된 메타데이터 딕셔너리
        """
        if not metadata:
            return {}
            
        result = {}
        for key, value in metadata.items():
            if isinstance(value, bool):
                result[key] = str(value)  # 불리언을 문자열로 변환
            elif isinstance(value, (str, int, float)) or value is None:
                result[key] = value
            else:
                # 다른 복잡한 타입(리스트, 딕셔너리 등)을 문자열로 변환
                result[key] = str(value)
        return result
    
    def _get_or_create_collection(self, name: str, description: str):
        """컬렉션이 존재하지 않으면 생성하고, 존재하면 가져오기"""
        try:
            return self.client.get_collection(name=name, embedding_function=self.embedding_function)
        except:
            return self.client.create_collection(
                name=name,
                embedding_function=self.embedding_function,
                metadata={"description": description}
            )
    
    def _generate_filename(self, content_id: str, content_type: str) -> str:
        """임시 저장소의 파일 이름 생성"""
        return os.path.join(self.temp_storage_path, f"{content_type}_{content_id}.pkl")
    
    def _save_full_content(self, content_id: str, content: str, content_type: str) -> bool:
        """전체 내용을 임시 저장소에 저장"""
        try:
            filename = self._generate_filename(content_id, content_type)
            with open(filename, 'wb') as f:
                pickle.dump(content, f)
            return True
        except Exception as e:
            print(f"전체 내용 저장 실패: {str(e)}")
            return False
    
    def _load_full_content(self, content_id: str, content_type: str) -> Optional[str]:
        """임시 저장소에서 전체 내용 로드"""
        try:
            filename = self._generate_filename(content_id, content_type)
            if os.path.exists(filename):
                with open(filename, 'rb') as f:
                    return pickle.load(f)
            return None
        except Exception as e:
            print(f"전체 내용 로드 실패: {str(e)}")
            return None
    
    def generate_summary(self, content: str, content_type: str, max_length: int = 500) -> str:
        """
        텍스트 내용에 대한 요약 생성
        
        Args:
            content: 요약할 내용
            content_type: 컨텐츠 유형 (conversation, document, workflow)
            max_length: 최대 요약 길이
            
        Returns:
            생성된 요약 텍스트
        """
        # OpenAI API가 설정되어 있는 경우 LLM으로 요약
        if settings.openai_api_key:
            try:
                # 내용이 너무 길면 앞부분만 사용
                if len(content) > 10000:
                    truncated_content = content[:10000] + "..."
                else:
                    truncated_content = content
                    
                # 컨텐츠 타입별 프롬프트 설정
                if content_type == "conversation":
                    prompt = f"다음 대화 내용의 핵심을 간결하게 요약해주세요:\n\n{truncated_content}"
                elif content_type == "document":
                    prompt = f"다음 문서의 핵심 내용을 간결하게 요약해주세요:\n\n{truncated_content}"
                elif content_type == "workflow":
                    workflow_text = json.dumps(content, ensure_ascii=False) if isinstance(content, dict) else content
                    prompt = f"다음 워크플로우 단계의 핵심을 간결하게 요약해주세요:\n\n{workflow_text}"
                else:
                    return f"요약 생성 실패: 지원하지 않는 컨텐츠 유형 {content_type}"
                    
                # OpenAI API 호출
                client = OpenAI(api_key=settings.openai_api_key)
                response = client.chat.completions.create(
                    model=settings.openai_model_name,
                    messages=[
                        {"role": "system", "content": "당신은 텍스트를 간결하게 요약하는 AI 비서입니다."},
                        {"role": "user", "content": prompt}
                    ],
                    temperature=0.3,
                    max_tokens=max_length // 4,
                )
                
                # 응답에서 요약 추출
                summary = response.choices[0].message.content.strip()
                
                # 컨텐츠 타입별 접두어 추가
                if content_type == "conversation":
                    return f"대화 내용 요약: {summary}"
                elif content_type == "document":
                    return f"문서 내용 요약: {summary}"
                elif content_type == "workflow":
                    return f"워크플로우 요약: {summary}"
                    
            except Exception as e:
                print(f"OpenAI 요약 생성 중 오류 발생: {str(e)}")
                # 오류 발생 시 기존 방식으로 요약 진행
                
        # 기존 요약 방식 (OpenAI API가 없거나 오류 발생 시)
        # 내용이 너무 길면 앞부분만 사용
        if len(content) > 10000:
            truncated_content = content[:10000] + "..."
        else:
            truncated_content = content
            
        # 간단한 요약 방법 (기존 코드 유지)
        if content_type == "conversation":
            lines = truncated_content.split('\n')
            summary_lines = []
            for line in lines:
                if line.strip() and len(' '.join(summary_lines)) < max_length:
                    summary_lines.append(line.strip())
            
            summary = ' '.join(summary_lines)
            if len(summary) > max_length:
                summary = summary[:max_length] + "..."
                
            return f"대화 내용 요약: {summary}"
            
        elif content_type == "document":
            paragraphs = truncated_content.split('\n\n')
            summary = paragraphs[0] if paragraphs else truncated_content[:max_length]
            if len(summary) > max_length:
                summary = summary[:max_length] + "..."
                
            return f"문서 내용 요약: {summary}"
            
        elif content_type == "workflow":
            try:
                workflow_data = json.loads(truncated_content) if isinstance(truncated_content, str) else truncated_content
                step_count = len(workflow_data)
                first_step = workflow_data[0] if workflow_data else {}
                last_step = workflow_data[-1] if workflow_data else {}
                
                return f"워크플로우 요약: 총 {step_count}개 단계, 시작: {first_step.get('name', '알 수 없음')}, 종료: {last_step.get('name', '알 수 없음')}"
            except:
                return f"워크플로우 요약: 파싱 불가 데이터"
        else:
            return f"요약 생성 실패: 지원하지 않는 컨텐츠 유형 {content_type}"
    
    def _get_chunk_id(self, document_id: str, chunk_index: int) -> str:
        """문서 ID와 청크 인덱스로 고유한 청크 ID 생성"""
        return f"{document_id}_{chunk_index}"
    
    def _calculate_checksum(self, content: str) -> str:
        """컨텐츠의 체크섬 계산"""
        return hashlib.md5(content.encode('utf-8')).hexdigest()
    
    def store_conversation(
        self, 
        conversation: str, 
        chat_id: str, 
        timestamp: datetime.datetime = None, 
        metadata: Dict[str, Any] = None
    ) -> Dict[str, Any]:
        """
        대화 내역을 벡터 DB에 저장
        
        Args:
            conversation: 대화 내용
            chat_id: 채팅 ID
            timestamp: 대화 시간
            metadata: 추가 메타데이터
            
        Returns:
            저장 결과 정보를 담은 딕셔너리
        """
        if timestamp is None:
            timestamp = datetime.datetime.now()
            
        if metadata is None:
            metadata = {}
        
        if metadata:
            metadata = self._sanitize_metadata(metadata)
        # 대화 내용이 너무 길면 자동 청크화
        chunks = self.text_splitter.split_text(conversation)
        
        # 요약 생성
        conversation_summary = self.generate_summary(conversation, "conversation")
        
        # 전체 내용 백업 저장
        self._save_full_content(chat_id, conversation, "conversation")
        
        # 메타데이터 기본값 설정
        base_metadata = {
            "chat_id": chat_id,
            "timestamp": timestamp.isoformat(),
            "total_chunks": len(chunks),
            "checksum": self._calculate_checksum(conversation),
            "summary": conversation_summary
        }
        
        # 사용자 제공 메타데이터 병합
        if metadata:
            base_metadata.update(metadata)
        
        # 메타데이터 정제 추가
        base_metadata = self._sanitize_metadata(base_metadata)

        ids = []
        metadatas = []
        
        # 각 청크에 대한 ID와 메타데이터 생성
        for i, chunk in enumerate(chunks):
            chunk_id = self._get_chunk_id(chat_id, i)
            ids.append(chunk_id)
            
            chunk_metadata = base_metadata.copy()
            chunk_metadata.update({
                "chunk_index": i,
                "is_summary": i == 0  # 첫 번째 청크에 요약 정보 포함
            })
            
            metadatas.append(chunk_metadata)
        
        # 첫 번째 청크에 요약 정보 추가
        if chunks:
            chunks[0] = f"{conversation_summary}\n\n{chunks[0]}"
        
        batch_size = 10
    
        # 배치 처리로 변경
        for i in range(0, len(chunks), batch_size):
            batch_chunks = chunks[i:i+batch_size]
            batch_ids = ids[i:i+batch_size]
            batch_metadatas = metadatas[i:i+batch_size]
            
            self.collections["conversations"].add(
                documents=batch_chunks,
                ids=batch_ids,
                metadatas=batch_metadatas
            )
            
            # 메모리 정리
            import gc
            gc.collect()
        
        # 요약 정보 별도 저장
        summary_id = f"summary_chat_{chat_id}"
        self.collections["summaries"].add(
            documents=[conversation_summary],
            ids=[summary_id],
            metadatas=[{
                "source_id": chat_id,
                "source_type": "conversation",
                "timestamp": timestamp.isoformat(),
                "summary_type": "auto"
            }]
        )
        
        return {
            "status": "success",
            "chat_id": chat_id,
            "stored_chunks": len(chunks),
            "summary": conversation_summary
        }
    
    def store_document(
        self, 
        document_content: str, 
        document_path: str, 
        document_name: str,
        metadata: Dict[str, Any] = None
    ) -> Dict[str, Any]:
        """
        문서 내용을 벡터 DB에 저장
        
        Args:
            document_content: 문서 내용
            document_path: 문서 파일 경로
            document_name: 문서 이름
            metadata: 추가 메타데이터
            
        Returns:
            저장 결과 정보를 담은 딕셔너리
        """
        if metadata is None:
            metadata = {}

        if metadata:
            metadata = self._sanitize_metadata(metadata)
            
        # 문서를 청크로 분할
        chunks = self.text_splitter.split_text(document_content)
        
        # 문서 요약 생성
        document_summary = self.generate_summary(document_content, "document")
        
        # 전체 내용 백업 저장
        self._save_full_content(document_name, document_content, "document")
        
        # 메타데이터 기본값 설정
        base_metadata = {
            "document_name": document_name,
            "document_path": document_path,
            "total_chunks": len(chunks),
            "checksum": self._calculate_checksum(document_content),
            "timestamp": datetime.datetime.now().isoformat(),
            "summary": document_summary
        }
        
        # 사용자 제공 메타데이터 병합
        base_metadata.update(metadata)
        
        ids = []
        metadatas = []
        
        # 각 청크에 대한 ID와 메타데이터 생성
        for i, chunk in enumerate(chunks):
            chunk_id = self._get_chunk_id(document_name, i)
            ids.append(chunk_id)
            
            chunk_metadata = base_metadata.copy()
            chunk_metadata.update({
                "chunk_index": i,
                "is_summary": i == 0  # 첫 번째 청크에 요약 정보 포함
            })
            
            metadatas.append(chunk_metadata)
        
        # 첫 번째 청크에 요약 정보 추가
        if chunks:
            chunks[0] = f"{document_summary}\n\n{chunks[0]}"
        
        # 배치 처리로 변경 - conversation 저장과 동일한 방식 적용
        batch_size = 10
        
        # 배치 처리로 Chroma에 저장
        for i in range(0, len(chunks), batch_size):
            batch_chunks = chunks[i:i+batch_size]
            batch_ids = ids[i:i+batch_size]
            batch_metadatas = metadatas[i:i+batch_size]
            
            self.collections["documents"].add(
                documents=batch_chunks,
                ids=batch_ids,
                metadatas=batch_metadatas
            )
            
            # 메모리 정리
            import gc
            gc.collect()
        
        # 요약 정보 별도 저장
        summary_id = f"summary_doc_{document_name}"
        self.collections["summaries"].add(
            documents=[document_summary],
            ids=[summary_id],
            metadatas=[{
                "source_id": document_name,
                "source_type": "document",
                "document_path": document_path,
                "timestamp": datetime.datetime.now().isoformat(),
                "summary_type": "auto"
            }]
        )
        
        return {
            "status": "success",
            "document_name": document_name,
            "stored_chunks": len(chunks),
            "summary": document_summary
        }
    
    def store_workflow(
        self, 
        workflow_steps: List[Dict[str, Any]], 
        mcp_name: str, 
        timestamp: datetime.datetime = None,
        metadata: Dict[str, Any] = None
    ) -> Dict[str, Any]:
        """
        MCP 작업 방법 및 순서를 벡터 DB에 저장
        
        Args:
            workflow_steps: 작업 단계 목록
            mcp_name: MCP 이름
            timestamp: 작업 시간
            metadata: 추가 메타데이터
            
        Returns:
            저장 결과 정보를 담은 딕셔너리
        """
        if timestamp is None:
            timestamp = datetime.datetime.now()
            
        if metadata is None:
            metadata = {}
            
        # 고유 워크플로우 ID 생성
        workflow_id = f"workflow_{mcp_name}_{timestamp.strftime('%Y%m%d%H%M%S')}"
        
        # 워크플로우 JSON 변환
        workflow_json = json.dumps(workflow_steps, ensure_ascii=False)
        
        # 워크플로우 요약 생성
        workflow_summary = self.generate_summary(workflow_steps, "workflow")
        
        # 전체 내용 백업 저장
        self._save_full_content(workflow_id, workflow_json, "workflow")
        
        # 메타데이터 설정
        workflow_metadata = {
            "mcp_name": mcp_name,
            "timestamp": timestamp.isoformat(),
            "step_count": len(workflow_steps),
            "summary": workflow_summary
        }
        
        # 사용자 제공 메타데이터 병합
        workflow_metadata.update(metadata)
        
        # Chroma에 저장
        self.collections["workflows"].add(
            documents=[workflow_json],
            ids=[workflow_id],
            metadatas=[workflow_metadata]
        )
        
        # 요약 정보 별도 저장
        summary_id = f"summary_{workflow_id}"
        self.collections["summaries"].add(
            documents=[workflow_summary],
            ids=[summary_id],
            metadatas=[{
                "source_id": workflow_id,
                "source_type": "workflow",
                "mcp_name": mcp_name,
                "timestamp": timestamp.isoformat(),
                "summary_type": "auto"
            }]
        )
        
        return {
            "status": "success",
            "workflow_id": workflow_id,
            "mcp_name": mcp_name,
            "summary": workflow_summary
        }
    
    def store_summary(
        self, 
        summary: str, 
        date: str, 
        task_name: str,
        metadata: Dict[str, Any] = None
    ) -> Dict[str, Any]:
        """
        작업 요약을 벡터 DB에 저장
        
        Args:
            summary: 요약 내용
            date: 날짜
            task_name: 작업 이름
            metadata: 추가 메타데이터
            
        Returns:
            저장 결과 정보를 담은 딕셔너리
        """
        if metadata is None:
            metadata = {}
            
        # 고유 요약 ID 생성
        summary_id = f"summary_task_{task_name}_{date}"
        
        # 메타데이터 설정
        summary_metadata = {
            "date": date,
            "task_name": task_name,
            "timestamp": datetime.datetime.now().isoformat(),
            "summary_type": "manual"
        }
        
        # 사용자 제공 메타데이터 병합
        summary_metadata.update(metadata)
        
        # Chroma에 저장
        self.collections["summaries"].add(
            documents=[summary],
            ids=[summary_id],
            metadatas=[summary_metadata]
        )
        
        return {
            "status": "success",
            "summary_id": summary_id,
            "task_name": task_name
        }
    
    def update_chunk(
        self, 
        document_id: str, 
        chunk_id: str, 
        new_content: str
    ) -> Dict[str, Any]:
        """
        특정 청크 내용 업데이트
        
        Args:
            document_id: 문서 ID
            chunk_id: 청크 ID
            new_content: 새 내용
            
        Returns:
            업데이트 결과 정보를 담은 딕셔너리
        """
        try:
            # 청크가 속한 컬렉션 결정
            collection_name = None
            for name, collection in self.collections.items():
                try:
                    # 청크 ID로 검색
                    result = collection.get(ids=[chunk_id])
                    if result and result["ids"]:
                        collection_name = name
                        break
                except:
                    continue
            
            if not collection_name:
                return {"status": "error", "message": "청크를 찾을 수 없습니다"}
            
            # 청크 업데이트
            self.collections[collection_name].update(
                ids=[chunk_id],
                documents=[new_content]
            )
            
            # 필요시 전체 내용 재구성 및 업데이트
            if collection_name in ["conversations", "documents"]:
                # 모든 청크 가져오기
                all_chunks = self.collections[collection_name].get(
                    where={"document_name": document_id} if collection_name == "documents" else {"chat_id": document_id}
                )
                
                if all_chunks and all_chunks["ids"]:
                    # 청크를 인덱스 순으로 정렬
                    chunks_with_indices = [(meta["chunk_index"], doc, id) 
                                          for meta, doc, id in zip(all_chunks["metadatas"], all_chunks["documents"], all_chunks["ids"])]
                    chunks_with_indices.sort(key=lambda x: x[0])
                    
                    # 전체 내용 재구성
                    full_content = "\n".join([chunk[1] for chunk in chunks_with_indices])
                    
                    # 백업 업데이트
                    self._save_full_content(
                        document_id, 
                        full_content, 
                        "document" if collection_name == "documents" else "conversation"
                    )
            
            return {
                "status": "success",
                "updated": True,
                "chunk_id": chunk_id,
                "collection": collection_name
            }
        except Exception as e:
            return {
                "status": "error",
                "message": f"청크 업데이트 실패: {str(e)}",
                "updated": False
            }
    
    def retrieve_full_content(self, content_id: str, content_type: str) -> str:
        """
        전체 내용 검색
        
        Args:
            content_id: 컨텐츠 ID
            content_type: 컨텐츠 유형 (conversation, document, workflow)
            
        Returns:
            전체 컨텐츠 내용
        """
        # 먼저 임시 저장소에서 찾기
        full_content = self._load_full_content(content_id, content_type)
        if full_content:
            return full_content
        
        # 임시 저장소에 없으면 Chroma에서 모든 청크를 검색하여 재구성
        collection_name = None
        id_field = None
        
        if content_type == "conversation":
            collection_name = "conversations"
            id_field = "chat_id"
        elif content_type == "document":
            collection_name = "documents"
            id_field = "document_name"
        elif content_type == "workflow":
            collection_name = "workflows"
            id_field = "workflow_id"
            # 워크플로우는 청크화되지 않으므로 바로 검색
            result = self.collections[collection_name].get(
                where={id_field: content_id}
            )
            if result and result["documents"] and len(result["documents"]) > 0:
                return result["documents"][0]
            else:
                return f"컨텐츠를 찾을 수 없습니다: {content_id}"
        else:
            return f"지원하지 않는 컨텐츠 유형: {content_type}"
        
        # 모든 청크 검색
        where_clause = {id_field: content_id}
        results = self.collections[collection_name].get(
            where=where_clause
        )
        
        if not results or not results["documents"]:
            return f"컨텐츠를 찾을 수 없습니다: {content_id}"
        
        # 청크 인덱스 기준 정렬 후 재조합
        chunks = [(meta["chunk_index"], doc) for meta, doc in zip(results["metadatas"], results["documents"])]
        chunks.sort(key=lambda x: x[0])
        
        reconstructed_content = "\n".join([chunk[1] for chunk in chunks])
        
        # 재구성된 내용을 임시 저장소에 저장
        self._save_full_content(content_id, reconstructed_content, content_type)
        
        return reconstructed_content
    
    def enhanced_search(
        self, 
        query: str, 
        collections: List[str] = None, 
        limit: int = 5,
        filters: Dict[str, Any] = None
    ) -> List[Dict[str, Any]]:
        """
        향상된 검색 함수
        
        Args:
            query: 검색 쿼리
            collections: 검색할 컬렉션 목록 (None이면 모든 컬렉션)
            limit: 각 컬렉션별 최대 결과 수
            filters: 검색 필터
            
        Returns:
            검색 결과 목록
        """
        if collections is None:
            collections = list(self.collections.keys())
            
        if filters is None:
            filters = {}
            
        combined_results = []
        
        for collection_name in collections:
            try:
                # 컬렉션에 쿼리 실행
                results = self.collections[collection_name].query(
                    query_texts=[query],
                    n_results=limit,
                    where=filters.get(collection_name, {})
                )
                
                if results and results["documents"] and len(results["documents"]) > 0:
                    for i, (doc, metadata, distance) in enumerate(zip(
                        results["documents"][0], 
                        results["metadatas"][0],
                        results["distances"][0]
                    )):
                        # 관련 정보 추출
                        source_id = metadata.get("chat_id") or metadata.get("document_name") or metadata.get("workflow_id") or metadata.get("task_name")
                        source_type = None
                        
                        if "chat_id" in metadata:
                            source_type = "conversation"
                        elif "document_name" in metadata:
                            source_type = "document"
                        elif "workflow_id" in metadata or "mcp_name" in metadata:
                            source_type = "workflow"
                        elif "task_name" in metadata:
                            source_type = "summary"
                        
                        # 요약 정보 가져오기
                        summary = metadata.get("summary", "")
                        
                        # 결과 정보 구성
                        result_item = {
                            "content": doc,
                            "metadata": metadata,
                            "source_id": source_id,
                            "source_type": source_type,
                            "collection": collection_name,
                            "relevance_score": 1.0 - float(distance),  # 거리를 유사도 점수로 변환
                            "summary": summary
                        }
                        
                        # 청크 정보가 있으면 전체 내용의 일부임을 표시
                        if "chunk_index" in metadata and "total_chunks" in metadata:
                            result_item["is_chunk"] = True
                            result_item["chunk_index"] = metadata["chunk_index"]
                            result_item["total_chunks"] = metadata["total_chunks"]
                        else:
                            result_item["is_chunk"] = False
                        
                        combined_results.append(result_item)
            except Exception as e:
                # 오류 발생시 계속 진행
                print(f"컬렉션 {collection_name} 검색 중 오류 발생: {str(e)}")
                traceback.print_exc()
                continue
        
        # 관련성 기준 정렬
        combined_results.sort(key=lambda x: x["relevance_score"], reverse=True)
        
        # 중복 제거 (같은 소스에서 여러 청크가 검색된 경우)
        seen_sources = set()
        unique_results = []
        
        for result in combined_results:
            # 중복 체크를 위한 키 생성
            source_key = f"{result['source_type']}_{result['source_id']}"
            
            # 이미 해당 소스의 결과가 포함되어 있다면 건너뜀
            if source_key in seen_sources:
                continue
                
            seen_sources.add(source_key)
            unique_results.append(result)
            
            # 지정된 제한에 도달하면 중단
            if len(unique_results) >= limit:
                break
        
        # 결과 강화 - 전체 내용에 대한 링크 추가
        for result in unique_results:
            if result["is_chunk"]:
                result["full_content_available"] = True
                
                # 예시 URL 형식 (실제 구현에 맞게 조정 필요)
                if result["source_type"] == "conversation":
                    result["full_content_url"] = f"/conversations/{result['source_id']}"
                elif result["source_type"] == "document":
                    result["full_content_url"] = f"/documents/{result['source_id']}"
                elif result["source_type"] == "workflow":
                    result["full_content_url"] = f"/workflows/{result['source_id']}"
        
        return unique_results
    
    def list_collections(self) -> List[Dict[str, Any]]:
        """
        사용 가능한 컬렉션 목록 조회
        
        Returns:
            컬렉션 정보 목록
        """
        collections_info = []
        
        for name, collection in self.collections.items():
            try:
                count = collection.count()
                collections_info.append({
                    "name": name,
                    "count": count,
                    "description": collection.metadata.get("description", "")
                })
            except Exception as e:
                collections_info.append({
                    "name": name,
                    "error": str(e)
                })
        
        return collections_info