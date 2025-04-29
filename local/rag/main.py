import uvicorn
from app import app
from src.config import settings

import os
import torch

# CUDA 비활성화 (이미 CPU 버전이지만 확실히 하기 위해)
os.environ["CUDA_VISIBLE_DEVICES"] = ""

# PyTorch 메모리 사용량 제한 (CPU 모드에서도 메모리 최적화에 도움)
if hasattr(torch, 'set_num_threads'):
    torch.set_num_threads(4)  # CPU 스레드 수 제한

# 필요시 캐시 크기 제한
if hasattr(torch, 'empty_cache'):
    torch.empty_cache()

if __name__ == "__main__":
    uvicorn.run(
        app,
        host=settings.host,
        port=settings.port, 
        reload=settings.debug, 
    )