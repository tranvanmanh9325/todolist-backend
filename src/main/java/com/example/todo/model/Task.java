package com.example.todo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "tasks", schema = "todo_schema") // Chỉ định schema
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Task title is required")
    private String title;

    private boolean completed;

    private String note;

    private String project;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // Thêm cột reminder
    // lưu số phút trước sự kiện (0, 30, 60, 120...)
    @Column(name = "reminder")
    private Integer reminder;

    // Constructor mặc định bắt buộc cho JPA
    public Task() {}

    // Constructor có tham số
    public Task(String title, boolean completed, String note, String project, Integer reminder) {
        this.title = title;
        this.completed = completed;
        this.note = note;
        this.project = project;
        this.reminder = reminder;
    }
}