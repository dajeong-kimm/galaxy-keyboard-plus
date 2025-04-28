from langchain.chains import RetrievalQA

def get_rag_chain(llm, vectorstore, chat_id: str, k: int):
    retriever = vectorstore.as_retriever(
        search_kwargs={"k": k},
        metadata_filter={"chat_id": chat_id},
    )
    return RetrievalQA.from_chain_type(
        llm=llm,
        chain_type="stuff",
        retriever=retriever,
    )
