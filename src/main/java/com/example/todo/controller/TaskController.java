package com.example.todo.controller;

import com.example.todo.entity.User;
import com.example.todo.model.Task;
import com.example.todo.model.TaskDetail;
import com.example.todo.repository.TaskRepository;
import com.example.todo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class TaskController {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public TaskController(TaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    // GET: /api/tasks
    @GetMapping("/tasks")
    public ResponseEntity<List<Task>> getTasks(Principal principal) {
        String email = principal.getName();
        User user = userRepository.findByEmail(email).orElseThrow();
        List<Task> tasks = taskRepository.findByUser(user);
        return ResponseEntity.ok(tasks);
    }

    // POST: /api/tasks
    @PostMapping("/tasks")
    public ResponseEntity<?> addTask(@RequestBody Task task, Principal principal) {
        try {
            if (task.getTitle() == null || task.getTitle().trim().isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body("Task title is required.");
            }

            String email = principal.getName();
            User user = userRepository.findByEmail(email).orElseThrow();

            task.setUser(user); // gắn user hiện tại
            task.setCompleted(false); // mặc định là chưa hoàn thành
            task.setCompletedAt(null);

            if (task.getTaskDetail() != null) {
                task.getTaskDetail().setTask(task); // set quan hệ ngược
            }

            Task savedTask = taskRepository.save(task);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedTask);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating task: " + e.getMessage());
        }
    }

    // PUT: /api/tasks/{id}
    @PutMapping("/tasks/{id}")
    public ResponseEntity<?> updateTask(@PathVariable Long id,
                                        @RequestBody Task updated,
                                        Principal principal) {
        String email = principal.getName();
        User user = userRepository.findByEmail(email).orElseThrow();

        Optional<Task> existingOpt = taskRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Task not found with id: " + id);
        }

        Task task = existingOpt.get();
        if (!task.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have permission to update this task");
        }

        // Cập nhật field cơ bản
        task.setTitle(updated.getTitle() != null ? updated.getTitle() : task.getTitle());
        task.setDescription(updated.getDescription()); // cho phép null để clear
        task.setType(updated.getType());               // cho phép null để clear

        task.setCompleted(updated.isCompleted());
        task.setCompletedAt(updated.isCompleted() ? LocalDateTime.now() : null);

        // Cập nhật TaskDetail
        if (updated.getTaskDetail() != null) {
            TaskDetail detail = task.getTaskDetail();
            if (detail == null) {
                detail = new TaskDetail();
                detail.setTask(task);
                task.setTaskDetail(detail);
            }

            // merge dữ liệu, cho phép clear nếu null
            detail.setDueDate(updated.getTaskDetail().getDueDate());
            detail.setTime(updated.getTaskDetail().getTime());
            detail.setDuration(updated.getTaskDetail().getDuration());
            detail.setRepeat(updated.getTaskDetail().getRepeat());
            detail.setPriority(updated.getTaskDetail().getPriority());
            detail.setReminder(updated.getTaskDetail().getReminder());
        }

        Task saved = taskRepository.save(task);
        return ResponseEntity.ok(saved);
    }

    // DELETE: /api/tasks/{id}
    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<?> deleteTask(@PathVariable Long id, Principal principal) {
        String email = principal.getName();
        User user = userRepository.findByEmail(email).orElseThrow();

        Optional<Task> existingOpt = taskRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Task not found with id: " + id);
        }

        Task task = existingOpt.get();
        if (!task.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have permission to delete this task");
        }

        taskRepository.delete(task);
        return ResponseEntity.noContent().build();
    }
}