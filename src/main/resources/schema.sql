-- Tạo schema
CREATE SCHEMA IF NOT EXISTS todo_schema;

-- Tạo bảng trong schema
CREATE TABLE IF NOT EXISTS todo_schema.tasks (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    completed BOOLEAN DEFAULT FALSE,
    note TEXT,
    project VARCHAR(255),
    completed_at TIMESTAMP,
    reminder INTEGER  -- Thêm cột reminder (số phút trước event)
);