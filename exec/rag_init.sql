-- 기존 테이블 삭제 (존재할 경우만)
-- 기존 테이블 삭제
DROP TABLE IF EXISTS image_keywords;
DROP TABLE IF EXISTS clipboard_items;
DROP TABLE IF EXISTS images;

-- 테이블 재생성
CREATE TABLE images (
    id SERIAL PRIMARY KEY,
    user_id TEXT NOT NULL,
    access_id TEXT NOT NULL,
    caption TEXT,
    image_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, access_id)  -- ✅ 복합 유니크 제약
);

CREATE TABLE image_keywords (
    id SERIAL PRIMARY KEY,
    image_id TEXT,  -- access_id 역할
    user_id TEXT NOT NULL,
    keyword TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id, image_id) REFERENCES images(user_id, access_id) ON DELETE CASCADE
);

CREATE TABLE clipboard_items (
    id SERIAL PRIMARY KEY,
    image_id TEXT,
    user_id TEXT NOT NULL,
    type TEXT NOT NULL,
    value TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id, image_id) REFERENCES images(user_id, access_id) ON DELETE CASCADE
);


-- 인덱스 생성 (검색 최적화용)
CREATE INDEX idx_keyword ON image_keywords(keyword);
