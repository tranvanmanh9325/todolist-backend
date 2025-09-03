package com.example.todo.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private Long id;
    private String name;
    private String email;
    private String token;  // JWT token
    private String avatar; // 👈 thêm avatar (URL hoặc null)

    // Constructor đầy đủ
    public LoginResponse(Long id, String name, String email, String token, String avatar) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.token = token;
        this.avatar = avatar;
    }

    // Constructor không có avatar (mặc định null)
    public LoginResponse(Long id, String name, String email, String token) {
        this(id, name, email, token, null);
    }

    // Constructor không có token (ví dụ: nếu dùng cho non-JWT usage)
    public LoginResponse(Long id, String name, String email) {
        this(id, name, email, null, null);
    }
}