package org.feiesos.asynctaskhub.mq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.feiesos.asynctaskhub.entity.Task;
import org.feiesos.asynctaskhub.entity.TaskStatus;
import org.feiesos.asynctaskhub.mapper.TaskMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@RocketMQMessageListener(consumerGroup = "async-task-consumer", topic = "image-process-topic")
public class TaskConsumer implements RocketMQListener<MessageExt> {

    private final TaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageExt message) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            TaskMessage taskMessage = objectMapper.readValue(body, TaskMessage.class);

            UUID taskId = taskMessage.taskId();
            log.info("Received task message, taskId={}, taskType={}", taskId, taskMessage.taskType());

            Task task = taskMapper.selectById(taskId);
            if (task == null) {
                log.warn("Task not found for taskId={}", taskId);
                return;
            }

            task.setStatus(TaskStatus.PROCESSING);
            taskMapper.updateById(task);

            log.info("Simulating image processing for taskId={}", taskId);
            Thread.sleep(200);

            task.setStatus(TaskStatus.SUCCESS);
            taskMapper.updateById(task);
        } catch (Exception ex) {
            log.error("Failed to process task message", ex);
            throw new RuntimeException("task processing failed", ex);
        }
    }

    public record TaskMessage(UUID taskId, String taskType, Map<String, Object> params) {
    }
}
