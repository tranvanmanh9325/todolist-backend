-- Tạo schema
CREATE SCHEMA IF NOT EXISTS todo_schema;

-- Tạo bảng trong schema
CREATE TABLE IF NOT EXISTS todo_schema.tasks (
    id SERIAL PRIMARY KEY,
    completed BOOLEAN DEFAULT FALSE,
    title VARCHAR(255) NOT NULL,
    description TEXT,           -- thay cho note
    type VARCHAR(255),          -- thay cho project

    -- ✅ Thêm các cột mới cho Date (đặt trước priority)
    due_date TIMESTAMP,         -- ngày đến hạn (có thể gồm cả giờ)
    time TIMESTAMP,             -- giờ cụ thể (nếu tách riêng)
    duration VARCHAR(50),       -- thời lượng ("30m", "1h", "none")
    repeat VARCHAR(50),         -- lặp lại ("daily", "weekly", "monthly", "none")

    priority INTEGER,           -- mức ưu tiên (1=High, 2=Medium, 3=Low, 4=None)
    reminder INTEGER,           -- số phút trước sự kiện
    completed_at TIMESTAMP      -- thời điểm hoàn thành
);