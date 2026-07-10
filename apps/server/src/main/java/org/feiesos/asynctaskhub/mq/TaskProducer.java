package org.feiesos.asynctaskhub.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskProducer {

    private static final String TOPIC = "image-process-topic";

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    public void send(UUID taskId, String taskType, Map<String, Object> params) {
        try {
            TaskMessage message = new TaskMessage(taskId, taskType, params);
            String payload = objectMapper.writeValueAsString(message);
            rocketMQTemplate.asyncSend(TOPIC, payload, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("Sent task message successfully, taskId={}, topic={}", taskId, TOPIC);
                }

                @Override
                public void onException(Throwable throwable) {
                    log.warn("Failed to send task message, taskId={}, topic={}", taskId, TOPIC, throwable);
                }
            });
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize task message, taskId={}", taskId, ex);
        }
    }

    public record TaskMessage(UUID taskId, String taskType, Map<String, Object> params) {
    }
}
