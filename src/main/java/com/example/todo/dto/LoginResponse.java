package com.example.todo.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private Long id;
    private String name;
    private String email;
    private String token; // Optional: for JWT

    public LoginResponse(Long id, String name, String email, String token) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.token = token;
    }

    // Constructor without token for non-JWT usage
    public LoginResponse(Long id, String name, String email) {
        this(id, name, email, null);
    }
}