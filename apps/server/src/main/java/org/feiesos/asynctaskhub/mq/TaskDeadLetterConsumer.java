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
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@RocketMQMessageListener(consumerGroup = "async-task-dlq-consumer", topic = "%DLQ%async-task-consumer")
public class TaskDeadLetterConsumer implements RocketMQListener<MessageExt> {

    private static final String DLQ_MARKER = "重试耗尽，进入死信队列";

    private final TaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageExt message) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            TaskConsumer.TaskMessage taskMessage;
            try {
                taskMessage = objectMapper.readValue(body, TaskConsumer.TaskMessage.class);
            } catch (Exception e) {
                log.error("Failed to parse DLQ message body", e);
                return;
            }

            UUID taskId = taskMessage.taskId();
            Task task = taskMapper.selectById(taskId);
            if (task == null) {
                log.warn("DLQ: Task not found for taskId={}", taskId);
                return;
            }

            if (task.getStatus() == TaskStatus.FAILED
                    && task.getErrorMsg() != null
                    && task.getErrorMsg().contains(DLQ_MARKER)) {
                log.info("DLQ: Task already marked as DLQ, skip. taskId={}", taskId);
                return;
            }

            task.setStatus(TaskStatus.FAILED);
            if (task.getErrorMsg() == null) {
                task.setErrorMsg(DLQ_MARKER);
            } else {
                task.setErrorMsg(task.getErrorMsg() + " | " + DLQ_MARKER);
            }
            taskMapper.updateById(task);
            log.warn("DLQ: Task marked as failed after retry exhaustion. taskId={}", taskId);
        } catch (Exception e) {
            log.error("DLQ: Unexpected error processing dead letter message", e);
        }
    }
}
