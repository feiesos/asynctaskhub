package org.feiesos.asynctaskhub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.feiesos.asynctaskhub.common.BusinessException;
import org.feiesos.asynctaskhub.common.ResourceNotFoundException;
import org.feiesos.asynctaskhub.config.GlobalExceptionHandler;
import org.feiesos.asynctaskhub.entity.Task;
import org.feiesos.asynctaskhub.entity.TaskStatus;
import org.feiesos.asynctaskhub.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    @Mock
    private TaskService taskService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TaskController controller = new TaskController(taskService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void retryTaskReturns200WhenTaskIsFailed() throws Exception {
        UUID taskId = UUID.randomUUID();
        TaskService.TaskRetryResponse response = new TaskService.TaskRetryResponse(
                taskId, "IMAGE_RESIZE", TaskStatus.PENDING);

        when(taskService.retryTask(taskId)).thenReturn(response);

        mockMvc.perform(post("/api/tasks/{taskId}/retry", taskId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(taskId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void getTaskReturns200WhenTaskExists() throws Exception {
        UUID taskId = UUID.randomUUID();
        Task task = new Task();
        task.setTaskId(taskId);
        task.setTaskType("WATERMARK");
        task.setStatus(TaskStatus.PENDING);

        when(taskService.getTask(taskId)).thenReturn(task);

        mockMvc.perform(get("/api/tasks/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value(taskId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void getTaskReturns404WhenTaskNotFound() throws Exception {
        UUID taskId = UUID.randomUUID();

        when(taskService.getTask(taskId)).thenThrow(new ResourceNotFoundException("Task not found: " + taskId));

        mockMvc.perform(get("/api/tasks/{taskId}", taskId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Task not found: " + taskId));
    }

    @Test
    void listTasksReturnsPagedResponse() throws Exception {
        Task task = new Task();
        task.setTaskId(UUID.randomUUID());
        task.setTaskType("COMPRESS");
        task.setStatus(TaskStatus.PENDING);
        List<Task> records = List.of(task);

        when(taskService.listTasks(anyInt(), anyInt()))
                .thenReturn(new TaskController.TaskPageResponse(records, 1, 20, 1, 1));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].taskId").value(task.getTaskId().toString()))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void createTaskReturns201WithTaskId() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(taskService.createTask("COMPRESS", "/input.jpg", Map.of("quality", 80)))
                .thenReturn(taskId);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(Map.of(
                                "taskType", "COMPRESS",
                                "filePath", "/input.jpg",
                                "params", Map.of("quality", 80)
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value(taskId.toString()));
    }

    @Test
    void retryTaskReturns409WhenTaskIsNotFailed() throws Exception {
        UUID taskId = UUID.randomUUID();

        when(taskService.retryTask(taskId))
                .thenThrow(new BusinessException(400, "Task " + taskId + " is not in FAILED status, cannot retry"));

        mockMvc.perform(post("/api/tasks/{taskId}/retry", taskId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Task " + taskId + " is not in FAILED status, cannot retry"));
    }
}
