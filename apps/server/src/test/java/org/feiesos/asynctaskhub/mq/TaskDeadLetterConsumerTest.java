package org.feiesos.asynctaskhub.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.common.message.MessageExt;
import org.feiesos.asynctaskhub.entity.Task;
import org.feiesos.asynctaskhub.entity.TaskStatus;
import org.feiesos.asynctaskhub.mapper.TaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskDeadLetterConsumerTest {

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TaskDeadLetterConsumer deadLetterConsumer;

    @Test
    void marksTaskFailedWhenStatusIsNotAlreadyFailed() throws Exception {
        UUID taskId = UUID.randomUUID();
        Task task = new Task();
        task.setTaskId(taskId);
        task.setStatus(TaskStatus.PROCESSING);

        String payload = new ObjectMapper().writeValueAsString(
                new TaskConsumer.TaskMessage(taskId, "IMAGE_RESIZE", Map.of()));

        MessageExt message = new MessageExt();
        message.setBody(payload.getBytes(StandardCharsets.UTF_8));

        when(objectMapper.readValue(payload, TaskConsumer.TaskMessage.class))
                .thenReturn(new TaskConsumer.TaskMessage(taskId, "IMAGE_RESIZE", Map.of()));
        when(taskMapper.selectById(taskId)).thenReturn(task);

        deadLetterConsumer.onMessage(message);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getErrorMsg()).isEqualTo("重试耗尽，进入死信队列");
        verify(taskMapper).updateById(task);
    }

    @Test
    void appendsDlqMarkerWhenTaskAlreadyFailedWithBusinessError() throws Exception {
        UUID taskId = UUID.randomUUID();
        Task task = new Task();
        task.setTaskId(taskId);
        task.setStatus(TaskStatus.FAILED);
        task.setErrorMsg("Failed to compress image: /tmp/xxx.jpg");

        String payload = new ObjectMapper().writeValueAsString(
                new TaskConsumer.TaskMessage(taskId, "IMAGE_RESIZE", Map.of()));

        MessageExt message = new MessageExt();
        message.setBody(payload.getBytes(StandardCharsets.UTF_8));

        when(objectMapper.readValue(payload, TaskConsumer.TaskMessage.class))
                .thenReturn(new TaskConsumer.TaskMessage(taskId, "IMAGE_RESIZE", Map.of()));
        when(taskMapper.selectById(taskId)).thenReturn(task);

        deadLetterConsumer.onMessage(message);

        assertThat(task.getErrorMsg()).isEqualTo("Failed to compress image: /tmp/xxx.jpg | 重试耗尽，进入死信队列");
        verify(taskMapper).updateById(task);
    }

    @Test
    void skipsProcessingWhenAlreadyMarkedAsDlq() throws Exception {
        UUID taskId = UUID.randomUUID();
        Task task = new Task();
        task.setTaskId(taskId);
        task.setStatus(TaskStatus.FAILED);
        task.setErrorMsg("Failed to compress image: /tmp/xxx.jpg | 重试耗尽，进入死信队列");

        String payload = new ObjectMapper().writeValueAsString(
                new TaskConsumer.TaskMessage(taskId, "IMAGE_RESIZE", Map.of()));

        MessageExt message = new MessageExt();
        message.setBody(payload.getBytes(StandardCharsets.UTF_8));

        when(objectMapper.readValue(payload, TaskConsumer.TaskMessage.class))
                .thenReturn(new TaskConsumer.TaskMessage(taskId, "IMAGE_RESIZE", Map.of()));
        when(taskMapper.selectById(taskId)).thenReturn(task);

        deadLetterConsumer.onMessage(message);

        verify(taskMapper, never()).updateById(task);
    }

    @Test
    void skipsProcessingWhenTaskNotFound() throws Exception {
        UUID taskId = UUID.randomUUID();

        String payload = new ObjectMapper().writeValueAsString(
                new TaskConsumer.TaskMessage(taskId, "IMAGE_RESIZE", Map.of()));

        MessageExt message = new MessageExt();
        message.setBody(payload.getBytes(StandardCharsets.UTF_8));

        when(objectMapper.readValue(payload, TaskConsumer.TaskMessage.class))
                .thenReturn(new TaskConsumer.TaskMessage(taskId, "IMAGE_RESIZE", Map.of()));
        when(taskMapper.selectById(taskId)).thenReturn(null);

        deadLetterConsumer.onMessage(message);

        verify(taskMapper, never()).updateById(any(Task.class));
    }

    @Test
    void doesNotRethrowOnUnexpectedException() throws Exception {
        UUID taskId = UUID.randomUUID();

        String payload = new ObjectMapper().writeValueAsString(
                new TaskConsumer.TaskMessage(taskId, "IMAGE_RESIZE", Map.of()));

        MessageExt message = new MessageExt();
        message.setBody(payload.getBytes(StandardCharsets.UTF_8));

        when(objectMapper.readValue(payload, TaskConsumer.TaskMessage.class))
                .thenReturn(new TaskConsumer.TaskMessage(taskId, "IMAGE_RESIZE", Map.of()));
        when(taskMapper.selectById(taskId)).thenThrow(new RuntimeException("DB connection lost"));

        deadLetterConsumer.onMessage(message);

        verify(taskMapper, never()).updateById(any(Task.class));
    }

    @Test
    void doesNotRethrowOnParseFailure() {
        MessageExt message = new MessageExt();
        message.setBody("invalid json".getBytes(StandardCharsets.UTF_8));

        deadLetterConsumer.onMessage(message);

        verify(taskMapper, never()).updateById(any(Task.class));
    }
}
