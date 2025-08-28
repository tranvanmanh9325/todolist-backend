package com.example.todo.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "task_details", schema = "todo_schema")
public class TaskDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Khóa ngoại trỏ về bảng tasks
    @OneToOne
    @JoinColumn(name = "task_id", nullable = false)
    @JsonBackReference   // ✅ tránh vòng lặp JSON khi serialize
    private Task task;

    @Column(name = "due_date")
    private LocalDateTime dueDate;   // ngày đến hạn

    @Column(name = "time")
    private LocalDateTime time;      // giờ cụ thể

    @Column(name = "duration")
    private String duration;         // thời lượng ("30m", "1h", "none")

    @Column(name = "repeat")
    private String repeat;           // lặp lại ("daily", "weekly", "monthly", "none")

    @Column(name = "priority")
    private Integer priority;        // mức ưu tiên (1=High, 2=Medium, 3=Low, 4=None)

    @Column(name = "reminder")
    private Integer reminder;        // số phút trước sự kiện

    // Constructor mặc định cho JPA
    public TaskDetail() {}

    // Constructor đầy đủ
    public TaskDetail(Task task, LocalDateTime dueDate, LocalDateTime time,
                      String duration, String repeat, Integer priority, Integer reminder) {
        this.task = task;
        this.dueDate = dueDate;
        this.time = time;
        this.duration = duration;
        this.repeat = repeat;
        this.priority = priority;
        this.reminder = reminder;
    }
}