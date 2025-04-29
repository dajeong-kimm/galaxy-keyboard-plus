from langchain_community.llms import LlamaCpp

def get_llm(model_path: str, n_ctx: int, n_threads: int) -> LlamaCpp:
    return LlamaCpp(
        model_path=model_path,
        n_ctx=n_ctx,
        n_threads=n_threads,
    )
