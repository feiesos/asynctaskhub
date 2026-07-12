package org.feiesos.asynctaskhub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.feiesos.asynctaskhub.common.BusinessException;
import org.feiesos.asynctaskhub.common.ResourceNotFoundException;
import org.feiesos.asynctaskhub.controller.TaskController;
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

    public TaskController.TaskPageResponse listTasks(int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);

        Page<Task> taskPage = new Page<>(safePage, safePageSize);
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<Task>()
                .orderByDesc(Task::getCreateTime);
        taskMapper.selectPage(taskPage, queryWrapper);

        return new TaskController.TaskPageResponse(
                taskPage.getRecords(),
                taskPage.getTotal(),
                taskPage.getSize(),
                taskPage.getCurrent(),
                taskPage.getPages()
        );
    }

    public Task getTask(UUID taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }
        return task;
    }

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
            throw new BusinessException(400, "Task " + taskId + " is not in FAILED status, cannot retry");
        }

        Task task = taskMapper.selectById(taskId);

        taskProducer.send(task.getTaskId(), task.getTaskType(), task.getParams());

        log.info("Task retry triggered, taskId={}, taskType={}", taskId, task.getTaskType());
        return new TaskRetryResponse(task.getTaskId(), task.getTaskType(), task.getStatus());
    }

    public record TaskRetryResponse(UUID taskId, String taskType, TaskStatus status) {
    }
}
