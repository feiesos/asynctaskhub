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
}
