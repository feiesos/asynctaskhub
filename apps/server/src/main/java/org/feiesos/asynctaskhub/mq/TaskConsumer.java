package org.feiesos.asynctaskhub.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.feiesos.asynctaskhub.entity.Task;
import org.feiesos.asynctaskhub.entity.TaskStatus;
import org.feiesos.asynctaskhub.mapper.TaskMapper;
import org.feiesos.asynctaskhub.service.ImageProcessService;
import org.feiesos.asynctaskhub.service.NonRetryableException;
import org.feiesos.asynctaskhub.service.RetryableException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
// maxReconsumeTimes=3 是演示用途的调低配置，生产环境建议 16
@RocketMQMessageListener(consumerGroup = "async-task-consumer", topic = "image-process-topic", maxReconsumeTimes = 3)
public class TaskConsumer implements RocketMQListener<MessageExt> {

    private static final String PROCESSING_LOCK_PREFIX = "task:processing:";
    private static final Duration PROCESSING_LOCK_TTL = Duration.ofMinutes(10);

    private final TaskMapper taskMapper;
    private final ObjectMapper objectMapper;
    private final ImageProcessService imageProcessService;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void onMessage(MessageExt message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        TaskMessage taskMessage;
        try {
            taskMessage = objectMapper.readValue(body, TaskMessage.class);
        } catch (Exception e) {
            log.error("Failed to parse task message body", e);
            return;
        }

        UUID taskId = taskMessage.taskId();
        String lockKey = PROCESSING_LOCK_PREFIX + taskId;
        boolean lockAcquired = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(lockKey, "processing", PROCESSING_LOCK_TTL)
        );

        if (!lockAcquired) {
            log.info("Task is already being processed, skip duplicate message. taskId={}", taskId);
            return;
        }

        try {
            Task task = taskMapper.selectById(taskId);
            if (task == null) {
                log.warn("Task not found for taskId={}", taskId);
                return;
            }

            if (task.getStatus() == TaskStatus.SUCCESS || task.getStatus() == TaskStatus.FAILED) {
                log.info("Task already in terminal state, skip processing. taskId={}, status={}", taskId, task.getStatus());
                return;
            }

            task.setRetryCount(message.getReconsumeTimes());
            task.setStatus(TaskStatus.PROCESSING);
            taskMapper.updateById(task);

            try {
                String resultPath = imageProcessService.compressImage(task.getFilePath(), task.getParams());
                task.setResultPath(resultPath);
                task.setStatus(TaskStatus.SUCCESS);
                taskMapper.updateById(task);
                log.info("Task processed successfully, taskId={}, resultPath={}", taskId, resultPath);
            } catch (NonRetryableException e) {
                log.warn("Non-retryable error for taskId={}: {}", taskId, e.getMessage());
                task.setStatus(TaskStatus.FAILED);
                task.setErrorMsg(e.getMessage());
                taskMapper.updateById(task);
            } catch (RetryableException e) {
                log.warn("Retryable error for taskId={}, reconsumeTimes={}: {}", taskId, message.getReconsumeTimes(), e.getMessage());
                task.setStatus(TaskStatus.FAILED);
                task.setErrorMsg(e.getMessage());
                taskMapper.updateById(task);
                throw e;
            }
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    public record TaskMessage(UUID taskId, String taskType, Map<String, Object> params) {
    }
}
