CREATE TABLE images (
    id SERIAL PRIMARY KEY,
    user_id TEXT NOT NULL,
    access_id TEXT UNIQUE NOT NULL,
    caption TEXT,
    image_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE image_keywords (
    id SERIAL PRIMARY KEY,
    image_id INT REFERENCES images(id) ON DELETE CASCADE,
    keyword TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 검색 최적화
CREATE INDEX idx_keyword ON image_keywords(keyword);
