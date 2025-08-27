package com.example.todo.model;

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

    @Column(name = "priority")
    private Integer priority;     // mức ưu tiên (1=High, 2=Medium, 3=Low, 4=None)

    @Column(name = "reminder")
    private Integer reminder;     // số phút trước sự kiện (0, 30, 60, 120...)

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // Constructor mặc định bắt buộc cho JPA
    public Task() {}

    // Constructor có tham số
    public Task(String title, boolean completed, String description, String type,
                Integer priority, Integer reminder) {
        this.title = title;
        this.completed = completed;
        this.description = description;
        this.type = type;
        this.priority = priority;
        this.reminder = reminder;
    }
}