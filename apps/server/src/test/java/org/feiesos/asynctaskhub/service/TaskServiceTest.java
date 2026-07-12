package org.feiesos.asynctaskhub.service;

import org.feiesos.asynctaskhub.common.BusinessException;
import org.feiesos.asynctaskhub.common.ResourceNotFoundException;
import org.feiesos.asynctaskhub.entity.Task;
import org.feiesos.asynctaskhub.entity.TaskStatus;
import org.feiesos.asynctaskhub.mapper.TaskMapper;
import org.feiesos.asynctaskhub.mq.TaskProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private TaskProducer taskProducer;

    @InjectMocks
    private TaskService taskService;

    @Test
    void createTaskPersistsPendingTaskAndReturnsId() {
        UUID expectedTaskId = UUID.randomUUID();
        doAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setTaskId(expectedTaskId);
            return 1;
        }).when(taskMapper).insert(any(Task.class));

        UUID actualTaskId = taskService.createTask("IMAGE_RESIZE", "/tmp/input.jpg", Map.of("width", 800));

        assertThat(actualTaskId).isEqualTo(expectedTaskId);

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).insert(taskCaptor.capture());

        Task savedTask = taskCaptor.getValue();
        assertThat(savedTask.getTaskId()).isEqualTo(expectedTaskId);
        assertThat(savedTask.getTaskType()).isEqualTo("IMAGE_RESIZE");
        assertThat(savedTask.getFilePath()).isEqualTo("/tmp/input.jpg");
        assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(savedTask.getParams()).containsEntry("width", 800);
    }

    @Test
    void retryTaskResetsStatusAndSendsMessage() {
        UUID taskId = UUID.randomUUID();
        Task task = new Task();
        task.setTaskId(taskId);
        task.setTaskType("IMAGE_RESIZE");
        task.setStatus(TaskStatus.PENDING);
        task.setRetryCount(0);

        when(taskMapper.update(any(), any())).thenReturn(1);
        when(taskMapper.selectById(taskId)).thenReturn(task);

        TaskService.TaskRetryResponse response = taskService.retryTask(taskId);

        assertThat(response.taskId()).isEqualTo(taskId);
        assertThat(response.status()).isEqualTo(TaskStatus.PENDING);
        verify(taskProducer).send(taskId, "IMAGE_RESIZE", task.getParams());
    }

    @Test
    void retryTaskRejectsWhenStatusIsNotFailed() {
        UUID taskId = UUID.randomUUID();

        when(taskMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> taskService.retryTask(taskId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not in FAILED status");

        verify(taskProducer, never()).send(any(), any(), any());
    }

    @Test
    void getTaskReturnsTaskWhenExists() {
        UUID taskId = UUID.randomUUID();
        Task task = new Task();
        task.setTaskId(taskId);
        task.setTaskType("WATERMARK");
        task.setStatus(TaskStatus.PENDING);

        when(taskMapper.selectById(taskId)).thenReturn(task);

        Task result = taskService.getTask(taskId);

        assertThat(result.getTaskId()).isEqualTo(taskId);
        assertThat(result.getTaskType()).isEqualTo("WATERMARK");
    }

    @Test
    void getTaskThrowsResourceNotFoundExceptionWhenNotExists() {
        UUID taskId = UUID.randomUUID();

        when(taskMapper.selectById(taskId)).thenReturn(null);

        assertThatThrownBy(() -> taskService.getTask(taskId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Task not found");
    }

    @Test
    void createTaskUsesEmptyMapWhenParamsIsNull() {
        doAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setTaskId(UUID.randomUUID());
            return 1;
        }).when(taskMapper).insert(any(Task.class));

        taskService.createTask("THUMBNAIL", "/path/to/file.jpg", null);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).insert(captor.capture());
        assertThat(captor.getValue().getParams()).isEmpty();
    }

    @Test
    void createTaskDoesNotThrowWhenProducerFails() {
        doAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setTaskId(UUID.randomUUID());
            return 1;
        }).when(taskMapper).insert(any(Task.class));
        lenient().doThrow(new RuntimeException("mq fail")).when(taskProducer).send(any(UUID.class), any(String.class), any(Map.class));

        assertThatCode(() -> taskService.createTask("IMAGE_FILTER", "/tmp/photo.png", Map.of("mode", "gray")))
                .doesNotThrowAnyException();
    }
}
