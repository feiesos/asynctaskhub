package org.feiesos.asynctaskhub.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.common.message.MessageExt;
import org.feiesos.asynctaskhub.entity.Task;
import org.feiesos.asynctaskhub.entity.TaskStatus;
import org.feiesos.asynctaskhub.mapper.TaskMapper;
import org.feiesos.asynctaskhub.service.ImageProcessService;
import org.feiesos.asynctaskhub.service.NonRetryableException;
import org.feiesos.asynctaskhub.service.RetryableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskConsumerTest {

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ImageProcessService imageProcessService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TaskConsumer taskConsumer;

    @Test
    void onMessageUpdatesStatusFromProcessingToSuccess() throws Exception {
        UUID taskId = UUID.randomUUID();
        Task task = new Task();
        task.setTaskId(taskId);
        task.setStatus(TaskStatus.PENDING);

        TaskConsumer.TaskMessage taskMessage = new TaskConsumer.TaskMessage(taskId, "IMAGE_RESIZE", Map.of("width", 100));
        String payload = new ObjectMapper().writeValueAsString(taskMessage);

        MessageExt message = new MessageExt();
        message.setBody(payload.getBytes(StandardCharsets.UTF_8));
        message.setReconsumeTimes(0);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("processing"), any(Duration.class))).thenReturn(true);
        when(taskMapper.selectById(taskId)).thenReturn(task);
        when(objectMapper.readValue(payload, TaskConsumer.TaskMessage.class)).thenReturn(taskMessage);
        when(imageProcessService.compressImage(task.getFilePath(), task.getParams())).thenReturn("/output/result.jpg");

        taskConsumer.onMessage(message);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(task.getResultPath()).isEqualTo("/output/result.jpg");
        assertThat(task.getRetryCount()).isZero();
        verify(taskMapper, times(2)).updateById(task);
    }

    @Test
    void onMessageMarksFailedOnNonRetryableException() throws Exception {
        UUID taskId = UUID.randomUUID();
        Task task = new Task();
        task.setTaskId(taskId);
        task.setStatus(TaskStatus.PENDING);

        TaskConsumer.TaskMessage taskMessage = new TaskConsumer.TaskMessage(taskId, "IMAGE_RESIZE", Map.of());
        String payload = new ObjectMapper().writeValueAsString(taskMessage);

        MessageExt message = new MessageExt();
        message.setBody(payload.getBytes(StandardCharsets.UTF_8));
        message.setReconsumeTimes(2);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("processing"), any(Duration.class))).thenReturn(true);
        when(taskMapper.selectById(taskId)).thenReturn(task);
        when(objectMapper.readValue(payload, TaskConsumer.TaskMessage.class)).thenReturn(taskMessage);
        when(imageProcessService.compressImage(task.getFilePath(), task.getParams()))
                .thenThrow(new NonRetryableException("File not found"));

        taskConsumer.onMessage(message);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getErrorMsg()).isEqualTo("File not found");
        assertThat(task.getRetryCount()).isEqualTo(2);
        verify(taskMapper, times(2)).updateById(task);
    }

    @Test
    void onMessageRethrowsRetryableException() throws Exception {
        UUID taskId = UUID.randomUUID();
        Task task = new Task();
        task.setTaskId(taskId);
        task.setStatus(TaskStatus.PENDING);

        TaskConsumer.TaskMessage taskMessage = new TaskConsumer.TaskMessage(taskId, "IMAGE_RESIZE", Map.of());
        String payload = new ObjectMapper().writeValueAsString(taskMessage);

        MessageExt message = new MessageExt();
        message.setBody(payload.getBytes(StandardCharsets.UTF_8));
        message.setReconsumeTimes(1);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("processing"), any(Duration.class))).thenReturn(true);
        when(taskMapper.selectById(taskId)).thenReturn(task);
        when(objectMapper.readValue(payload, TaskConsumer.TaskMessage.class)).thenReturn(taskMessage);
        when(imageProcessService.compressImage(task.getFilePath(), task.getParams()))
                .thenThrow(new RetryableException("IO error", new java.io.IOException("timeout")));

        assertThatThrownBy(() -> taskConsumer.onMessage(message))
                .isInstanceOf(RetryableException.class)
                .hasMessage("IO error");

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getErrorMsg()).isEqualTo("IO error");
        assertThat(task.getRetryCount()).isEqualTo(1);
        verify(taskMapper, times(2)).updateById(task);
    }

    @Test
    void onMessageSkipsDuplicateProcessingForSameTaskId() throws Exception {
        UUID taskId = UUID.randomUUID();
        Task task = new Task();
        task.setTaskId(taskId);
        task.setStatus(TaskStatus.PENDING);

        TaskConsumer.TaskMessage taskMessage = new TaskConsumer.TaskMessage(taskId, "IMAGE_RESIZE", Map.of("width", 100));
        String payload = new ObjectMapper().writeValueAsString(taskMessage);

        MessageExt message = new MessageExt();
        message.setBody(payload.getBytes(StandardCharsets.UTF_8));
        message.setReconsumeTimes(0);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("processing"), any(Duration.class))).thenReturn(true, false);
        when(taskMapper.selectById(taskId)).thenReturn(task);
        when(objectMapper.readValue(payload, TaskConsumer.TaskMessage.class)).thenReturn(taskMessage);
        when(imageProcessService.compressImage(task.getFilePath(), task.getParams())).thenReturn("/output/result.jpg");

        taskConsumer.onMessage(message);
        taskConsumer.onMessage(message);

        verify(imageProcessService, times(1)).compressImage(task.getFilePath(), task.getParams());
        verify(taskMapper, times(2)).updateById(task);
    }
}
