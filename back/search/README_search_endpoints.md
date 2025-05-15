# 검색 API 엔드포인트

이 문서는 사진 검색과 정보 검색을 위한 새로운 엔드포인트를 설명합니다.

**주의**: 캐싱 기능은 `/search/answer` 엔드포인트에만 적용됩니다. `/search/photo`와 `/search/info`는 캐싱되지 않습니다.

## 엔드포인트

### 1. 사진 검색 - `/search/photo` (캐싱 없음)

사진 벡터에서 검색하여 관련 사진들을 반환합니다.

**요청 형식:**
```
POST /search/photo
Content-Type: application/x-www-form-urlencoded

user_id=user123&query=빨간색 인형&top_k=5
```

**응답 형식:**
```json
{
  "photos": [
    {
      "id": "photo_123",
      "text": "빨간색 인형이 침대 위에 놓여 있는 사진",
      "score": 0.95
    },
    {
      "id": "photo_456", 
      "text": "빨간 드레스를 입은 인형",
      "score": 0.89
    }
  ],
  "query": "빨간색 인형",
  "expanded_queries": ["빨간색 인형", "빨간 인형", "적색 인형", "빨간색 장난감"],
  "total_time": 0.523
}
```

### 2. 정보 검색 - `/search/info` (캐싱 없음)

정보 벡터에서 검색하여 관련 정보와 근거 사진을 반환합니다.

**요청 형식:**
```
POST /search/info
Content-Type: application/x-www-form-urlencoded

user_id=user123&query=에어컨 청소 방법&top_k=5&include_related_photos=true
```

**응답 형식:**
```json
{
  "info": [
    {
      "id": "info_001",
      "text": "에어컨은 정기적으로 필터를 청소하고, 실외기를 점검해야 합니다...",
      "score": 0.92,
      "related_photos": [
        {
          "id": "photo_789",
          "text": "에어컨 필터 청소하는 사진",
          "score": 0.85
        },
        {
          "id": "photo_012",
          "text": "에어컨 실외기 점검 사진", 
          "score": 0.78
        }
      ]
    }
  ],
  "query": "에어컨 청소 방법",
  "expanded_queries": ["에어컨 청소 방법", "에어컨 관리", "에어컨 필터 청소"],
  "total_time": 1.234
}
```

### 3. 종합 검색 - `/search/answer` (캐싱 적용)

사진과 정보 벡터를 모두 검색하여 종합적인 답변을 제공합니다. **이 엔드포인트만 캐싱이 적용됩니다.**

**요청 형식:**
```
POST /search/answer
Content-Type: application/x-www-form-urlencoded

user_id=user123&query=빨간색 인형 찾아줘&top_k_photo=5&top_k_info=5
```

**응답 형식:**
```json
{
  "answer": "빨간색 인형이 있는 사진을 찾았습니다. 침실에 빨간색 인형이 놓여 있는 사진과 빨간 드레스를 입은 인형 사진이 있습니다.",
  "photo_results": [...],
  "info_results": [...],
  "query_intent": "photo_search",
  "_timings": {
    "query_expansion": 0.231,
    "intent_detection": 0.012,
    "vector_search": 0.543,
    "filtering": 0.089,
    "answer_generation": 0.321,
    "total": 1.196
  },
  "_from_cache": false
}
```

## 캐싱 관리 (/search/answer 전용)

### 캐시 상태 확인
```http
GET /search/cache/status
```

응답:
```json
{
  "total_items": 42,
  "valid_items": 35,
  "expired_items": 7,
  "max_size": 1000,
  "ttl_seconds": 3600,
  "memory_usage_mb": 2.34
}
```

### 캐시 초기화
```http
DELETE /search/cache/clear
```

응답:
```json
{
  "cleared_items": 42,
  "message": "캐시가 초기화되었습니다."
}
```

### 만료된 캐시 삭제
```http
DELETE /search/cache/expired
```

응답:
```json
{
  "deleted_items": 7,
  "message": "7개의 만료된 항목이 삭제되었습니다."
}
```

## 캐싱 정책 (/search/answer 전용)

- **TTL (Time To Live)**: 1시간 (3600초)
- **최대 캐시 크기**: 1000개 항목
- **캐시 키**: user_id + query + top_k_photo + top_k_info 조합의 MD5 해시
- **제거 정책**: LRU (Least Recently Used) - 캐시가 가득 찰 경우 가장 오래된 항목 제거
- **캐시 플래그**: 응답에 `_from_cache` 필드가 포함되어 캐시된 결과인지 구분 가능

## 특징

- **쿼리 확장**: 모든 검색에서 자동으로 쿼리를 확장하여 더 정확한 결과를 제공합니다.
- **색상/객체 분석**: 색상과 객체를 자동으로 인식하여 검색 정확도를 향상시킵니다.
- **관련 사진 연결**: 정보 검색 시 관련 사진도 함께 제공할 수 있습니다.
- **비동기 처리**: 모든 검색은 비동기로 처리되어 빠른 응답을 제공합니다.
- **선택적 캐싱**: `/search/answer` 엔드포인트만 캐싱을 사용하여 반복적인 종합 검색에 대해 높은 성능을 제공합니다.

## 성능 특징

1. **빠른 응답**: `/search/answer`에서 동일한 검색 요청에 대해 즉시 응답 (캐시 히트 시)
2. **서버 부하 감소**: 반복되는 종합 검색 연산 방지
3. **메모리 효율성**: 자동 만료 및 크기 제한으로 메모리 사용량 관리
4. **선택적 적용**: 필요한 엔드포인트에만 캐싱을 적용하여 효율성 극대화

## 사용 예시

```python
import requests

# 사진 검색 (캐싱 없음)
response = requests.post(
    "http://localhost:8091/search/photo",
    data={
        "user_id": "user123",
        "query": "파란색 에어컨",
        "top_k": 5
    }
)
photos = response.json()

# 정보 검색 (캐싱 없음)
response = requests.post(
    "http://localhost:8091/search/info",
    data={
        "user_id": "user123",
        "query": "에어컨 청소 주기",
        "top_k": 3,
        "include_related_photos": True
    }
)
info = response.json()

# 종합 검색 (캐싱 적용)
response = requests.post(
    "http://localhost:8091/search/answer",
    data={
        "user_id": "user123",
        "query": "빨간색 인형 찾아줘",
        "top_k_photo": 5,
        "top_k_info": 5
    }
)
answer = response.json()
# '_from_cache' 필드로 캐시 여부 확인 가능
if answer.get('_from_cache'):
    print("캐시된 결과입니다")
```
