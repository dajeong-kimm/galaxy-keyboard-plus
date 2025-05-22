-- 이미지 정보 테이블
CREATE TABLE images (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    access_id VARCHAR(255) NOT NULL UNIQUE,
    caption TEXT,
    image_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 이미지 키워드 테이블
CREATE TABLE image_keywords (
    id SERIAL PRIMARY KEY,
    image_id INTEGER NOT NULL REFERENCES images(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    keyword VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 추가 (검색 성능 향상 목적)
CREATE INDEX idx_image_keywords_keyword ON image_keywords(keyword);
CREATE INDEX idx_images_user_id ON images(user_id);
CREATE INDEX idx_image_keywords_user_id ON image_keywords(user_id);
