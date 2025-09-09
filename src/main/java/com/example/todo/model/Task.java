package com.example.todo.model;

import com.example.todo.entity.User;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "tasks", schema = "todo_schema") // chỉ định schema
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Task title is required")
    private String title;

    private boolean completed;

    @Column(name = "description")
    private String description;   // thay cho note

    @Column(name = "type")
    private String type;          // thay cho project

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // 🔹 Mỗi Task thuộc về một User
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) // cột user_id trong bảng tasks
    @JsonBackReference // tránh vòng lặp JSON khi serialize User -> Task -> User
    private User user;

    // 🔹 Liên kết 1-1 với bảng task_details
    @OneToOne(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference   // ✅ tránh vòng lặp JSON
    private TaskDetail taskDetail;

    // Constructor mặc định bắt buộc cho JPA
    public Task() {}

    // Constructor có tham số (cơ bản, không bao gồm taskDetail)
    public Task(String title, boolean completed, String description, String type, LocalDateTime completedAt, User user) {
        this.title = title;
        this.completed = completed;
        this.description = description;
        this.type = type;
        this.completedAt = completedAt;
        this.user = user;
    }
}