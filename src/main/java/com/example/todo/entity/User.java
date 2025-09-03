package com.example.todo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Entity
@Data
@Table(name = "users", schema = "todo_schema")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name cannot be empty")
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Email must be a valid email address")
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @NotBlank(message = "Password cannot be empty")
    @Column(name = "password", nullable = false)
    private String password;

    // 👇 thêm cột avatar (có thể null)
    @Column(name = "avatar")
    private String avatar;
}