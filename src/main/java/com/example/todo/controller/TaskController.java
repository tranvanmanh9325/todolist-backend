package com.example.todo.controller;

import com.example.todo.model.Task;
import com.example.todo.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class TaskController {

    private final TaskRepository taskRepository;

    public TaskController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    // GET: /api/tasks
    @GetMapping("/tasks")
    public List<Task> getTasks() {
        return taskRepository.findAll();
    }

    // POST: /api/tasks
    @PostMapping("/tasks")
    public ResponseEntity<?> addTask(@RequestBody Task task) {
        if (task.getTitle() == null || task.getTitle().trim().isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Task title is required.");
        }

        task.setCompleted(false); // Luôn tạo mới với trạng thái chưa hoàn thành
        task.setCompletedAt(null);
        Task savedTask = taskRepository.save(task);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedTask);
    }

    // PUT: /api/tasks/{id}
    @PutMapping("/tasks/{id}")
    public Task updateTask(@PathVariable Long id, @RequestBody Task updated) {
        return taskRepository.findById(id).map(task -> {
            if (updated.getTitle() != null) {
                task.setTitle(updated.getTitle());
            }
            if (updated.getNote() != null) {
                task.setNote(updated.getNote());
            }
            if (updated.getProject() != null) {
                task.setProject(updated.getProject());
            }

            task.setCompleted(updated.isCompleted());
            task.setCompletedAt(updated.isCompleted() ? LocalDateTime.now() : null);

            return taskRepository.save(task);
        }).orElseThrow(() -> new RuntimeException("Task not found with id: " + id));
    }

    // DELETE: /api/tasks/{id}
    @DeleteMapping("/tasks/{id}")
    public void deleteTask(@PathVariable Long id) {
        taskRepository.deleteById(id);
    }
}