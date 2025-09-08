-- Tạo schema nếu chưa tồn tại
CREATE SCHEMA IF NOT EXISTS todo_schema;

-- ===========================
-- BẢNG NGƯỜI DÙNG & OTP
-- ===========================

-- Bảng users
CREATE TABLE IF NOT EXISTS todo_schema.users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    avatar VARCHAR(500)          -- có thể null, lưu URL hoặc base64
);

-- Bảng otps (tham chiếu email người dùng)
CREATE TABLE IF NOT EXISTS todo_schema.otps (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    otp_code VARCHAR(6) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    FOREIGN KEY (email) REFERENCES todo_schema.users(email)
);

-- ===========================
-- BẢNG TASK & TASK_DETAILS
-- ===========================

-- Bảng chính: tasks
CREATE TABLE IF NOT EXISTS todo_schema.tasks (
    id SERIAL PRIMARY KEY,
    completed BOOLEAN DEFAULT FALSE,
    title VARCHAR(255) NOT NULL,
    description TEXT,            -- thay cho note
    type VARCHAR(255),           -- thay cho project
    completed_at TIMESTAMP       -- thời điểm hoàn thành
);

-- Bảng phụ: task_details
CREATE TABLE IF NOT EXISTS todo_schema.task_details (
    id SERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES todo_schema.tasks(id) ON DELETE CASCADE,
    due_date TIMESTAMP,          -- ngày đến hạn
    time TIMESTAMP,              -- giờ cụ thể
    duration VARCHAR(50),        -- thời lượng ("30m", "1h", "none")
    repeat VARCHAR(50),          -- lặp lại ("daily", "weekly", "monthly", "none")
    priority INTEGER,            -- mức ưu tiên (1=High, 2=Medium, 3=Low, 4=None)
    reminder INTEGER             -- số phút trước sự kiện
);