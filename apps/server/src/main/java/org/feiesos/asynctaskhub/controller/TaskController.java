package org.feiesos.asynctaskhub.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.feiesos.asynctaskhub.service.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Validated
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskCreateResponse createTask(@Valid @RequestBody TaskCreateRequest request) {
        UUID taskId = taskService.createTask(request.getTaskType(), request.getFilePath(), request.getParams());
        return new TaskCreateResponse(taskId);
    }

    public static class TaskCreateRequest {
        @NotBlank(message = "taskType is required")
        @Pattern(regexp = "IMAGE_RESIZE|IMAGE_FILTER|IMAGE_COMPRESS", message = "taskType must be one of IMAGE_RESIZE, IMAGE_FILTER, IMAGE_COMPRESS")
        private String taskType;

        @NotBlank(message = "filePath is required")
        private String filePath;

        @NotNull(message = "params is required")
        private Map<String, Object> params;

        public String getTaskType() {
            return taskType;
        }

        public void setTaskType(String taskType) {
            this.taskType = taskType;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public Map<String, Object> getParams() {
            return params;
        }

        public void setParams(Map<String, Object> params) {
            this.params = params;
        }
    }

    public record TaskCreateResponse(UUID taskId) {
    }
}
