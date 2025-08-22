-- Tạo schema
CREATE SCHEMA IF NOT EXISTS todo_schema;

-- Tạo bảng trong schema
CREATE TABLE IF NOT EXISTS todo_schema.tasks (
    id SERIAL PRIMARY KEY,
    completed BOOLEAN DEFAULT FALSE,
    title VARCHAR(255) NOT NULL,
    description TEXT,          -- thay cho note
    reminder INTEGER,          -- số phút trước sự kiện
    type VARCHAR(255),         -- thay cho project
    completed_at TIMESTAMP
);