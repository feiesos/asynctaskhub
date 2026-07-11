package org.feiesos.asynctaskhub.controller;

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

import java.util.UUID;

import static org.mockito.Mockito.when;
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
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
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
                .andExpect(jsonPath("$.taskId").value(taskId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void retryTaskReturns409WhenTaskIsNotFailed() throws Exception {
        UUID taskId = UUID.randomUUID();

        when(taskService.retryTask(taskId))
                .thenThrow(new IllegalStateException("Task " + taskId + " is not in FAILED status, cannot retry"));

        mockMvc.perform(post("/api/tasks/{taskId}/retry", taskId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Task " + taskId + " is not in FAILED status, cannot retry"));
    }
}
