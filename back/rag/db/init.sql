-- 기존 테이블 삭제 (존재할 경우만)
DROP TABLE IF EXISTS image_keywords;
DROP TABLE IF EXISTS images;

-- 테이블 재생성

CREATE TABLE images (
    id SERIAL PRIMARY KEY,
    user_id TEXT NOT NULL,
    access_id TEXT UNIQUE NOT NULL,
    caption TEXT,
    image_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE image_keywords (
    id SERIAL PRIMARY KEY,
    image_id TEXT REFERENCES images(access_id) ON DELETE CASCADE,
    keyword TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DROP TABLE IF EXISTS clipboard_items;

CREATE TABLE clipboard_items (
    id SERIAL PRIMARY KEY,
    user_id TEXT NOT NULL,
    image_id TEXT REFERENCES images(access_id) ON DELETE CASCADE,
    type TEXT NOT NULL,       -- 예: '와이파이', '계좌번호'
    value TEXT NOT NULL,      -- 예: 'olleh_WiFi 12345678'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- 인덱스 생성 (검색 최적화용)
CREATE INDEX idx_keyword ON image_keywords(keyword);
