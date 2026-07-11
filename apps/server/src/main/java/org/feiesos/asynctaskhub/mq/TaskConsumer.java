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
    private final ImageProcessService imageProcessService;

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
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("Task not found for taskId={}", taskId);
            return;
        }

        task.setStatus(TaskStatus.PROCESSING);
        taskMapper.updateById(task);

        try {
            String resultPath = imageProcessService.compressImage(task.getFilePath(), task.getParams());
            task.setResultPath(resultPath);
            task.setStatus(TaskStatus.SUCCESS);
            taskMapper.updateById(task);
            log.info("Task processed successfully, taskId={}, resultPath={}", taskId, resultPath);
        } catch (Exception e) {
            log.error("Failed to process task, taskId={}", taskId, e);
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMsg(e.getMessage());
            taskMapper.updateById(task);
        }
    }

    public record TaskMessage(UUID taskId, String taskType, Map<String, Object> params) {
    }
}
