-- Tạo schema nếu chưa tồn tại
CREATE SCHEMA IF NOT EXISTS todo_schema;

-- Tạo bảng users trong todo_schema
CREATE TABLE IF NOT EXISTS todo_schema.users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    avatar VARCHAR(500)  -- 👈 thêm cột avatar (có thể null, lưu URL hoặc base64)
);

-- Tạo bảng otps trong todo_schema
CREATE TABLE IF NOT EXISTS todo_schema.otps (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    otp_code VARCHAR(6) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    FOREIGN KEY (email) REFERENCES todo_schema.users(email)
);