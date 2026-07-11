package org.feiesos.asynctaskhub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.feiesos.asynctaskhub.entity.Task;
import org.feiesos.asynctaskhub.entity.TaskStatus;
import org.feiesos.asynctaskhub.mapper.TaskMapper;
import org.feiesos.asynctaskhub.mq.TaskProducer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskMapper taskMapper;
    private final TaskProducer taskProducer;

    @Transactional
    public UUID createTask(String taskType, String filePath, Map<String, Object> params) {
        Task task = new Task();
        task.setTaskId(UUID.randomUUID());
        task.setTaskType(taskType);
        task.setFilePath(filePath);
        task.setParams(params == null ? Map.of() : params);
        task.setStatus(TaskStatus.PENDING);

        taskMapper.insert(task);

        UUID taskId = task.getTaskId();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        return;
                    }
                    try {
                        taskProducer.send(taskId, taskType, params);
                    } catch (Exception ex) {
                        log.warn("Failed to send task message for taskId={}, taskType={}, filePath={}", taskId, taskType, filePath, ex);
                    }
                }
            });
        }

        return taskId;
    }

    public TaskRetryResponse retryTask(UUID taskId) {
        int rows = taskMapper.update(
                null,
                new UpdateWrapper<Task>()
                        .set("status", TaskStatus.PENDING)
                        .set("retry_count", 0)
                        .set("error_msg", null)
                        .eq("task_id", taskId)
                        .eq("status", TaskStatus.FAILED)
        );

        if (rows == 0) {
            throw new IllegalStateException("Task " + taskId + " is not in FAILED status, cannot retry");
        }

        Task task = taskMapper.selectById(taskId);

        taskProducer.send(task.getTaskId(), task.getTaskType(), task.getParams());

        log.info("Task retry triggered, taskId={}, taskType={}", taskId, task.getTaskType());
        return new TaskRetryResponse(task.getTaskId(), task.getTaskType(), task.getStatus());
    }

    public record TaskRetryResponse(UUID taskId, String taskType, TaskStatus status) {
    }
}
