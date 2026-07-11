package org.feiesos.asynctaskhub.service;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

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
