package org.feiesos.asynctaskhub.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.common.message.MessageExt;
import org.feiesos.asynctaskhub.entity.Task;
import org.feiesos.asynctaskhub.entity.TaskStatus;
import org.feiesos.asynctaskhub.mapper.TaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskConsumerTest {

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private ObjectMapper objectMapper;

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

        when(taskMapper.selectById(taskId)).thenReturn(task);
        when(objectMapper.readValue(payload, TaskConsumer.TaskMessage.class)).thenReturn(taskMessage);

        taskConsumer.onMessage(message);

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper, org.mockito.Mockito.atLeast(2)).updateById(taskCaptor.capture());

        assertThat(taskCaptor.getAllValues()).hasSize(2);
        assertThat(taskCaptor.getAllValues()).extracting(Task::getStatus)
                .contains(TaskStatus.SUCCESS);
    }
}
