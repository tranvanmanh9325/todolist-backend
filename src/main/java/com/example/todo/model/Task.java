package com.example.todo.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
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

    // Liên kết 1-1 với bảng task_details
    @OneToOne(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference   // ✅ tránh vòng lặp JSON
    private TaskDetail taskDetail;

    // Constructor mặc định bắt buộc cho JPA
    public Task() {}

    // Constructor có tham số (cơ bản, không bao gồm taskDetail)
    public Task(String title, boolean completed, String description, String type, LocalDateTime completedAt) {
        this.title = title;
        this.completed = completed;
        this.description = description;
        this.type = type;
        this.completedAt = completedAt;
    }
}