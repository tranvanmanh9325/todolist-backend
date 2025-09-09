package com.example.todo.repository;

import com.example.todo.model.Task;
import com.example.todo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // Lấy tất cả task của một user
    List<Task> findByUser(User user);
}