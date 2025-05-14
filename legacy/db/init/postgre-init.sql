-- users 테이블 생성
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    google_id VARCHAR(100) NOT NULL UNIQUE,  -- Google에서 받은 유저 고유 ID
    email VARCHAR(100) NOT NULL UNIQUE,       -- 구글 인증 이메일 (필수)
    name VARCHAR(100),                        -- 구글 이름
    profile_image VARCHAR(255),               -- 프로필 사진 URL
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
