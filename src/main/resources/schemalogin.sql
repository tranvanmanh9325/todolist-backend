-- Xóa tránh dư thừa
DROP TABLE IF EXISTS public.users;

-- Tạo schema nếu chưa tồn tại
CREATE SCHEMA IF NOT EXISTS todo_schema;

-- Tạo bảng users trong todo_schema
CREATE TABLE todo_schema.users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);