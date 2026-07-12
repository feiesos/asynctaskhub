package org.feiesos.asynctaskhub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.feiesos.asynctaskhub.entity.Task;
import org.feiesos.asynctaskhub.entity.TaskStatus;
import org.feiesos.asynctaskhub.mapper.TaskMapper;
import org.feiesos.asynctaskhub.mq.TaskProducer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskCompensationJob {

    private static final String PROCESSING_LOCK_PREFIX = "task:processing:";
    private static final int MAX_COMPENSATION_COUNT = 3;
    private static final int COMPENSATION_THRESHOLD_MINUTES = 2;

    private final TaskMapper taskMapper;
    private final TaskProducer taskProducer;
    private final RedisTemplate<String, String> redisTemplate;
    private final Clock clock;

    @Scheduled(fixedRateString = "60000")
    public void compensatePendingTasks() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Task> candidates = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, TaskStatus.PENDING)
                .lt(Task::getCreateTime, now.minusMinutes(COMPENSATION_THRESHOLD_MINUTES))
        );

        if (candidates.isEmpty()) {
            return;
        }

        for (Task task : candidates) {
            String lockKey = PROCESSING_LOCK_PREFIX + task.getTaskId();
            Boolean processing = redisTemplate.hasKey(lockKey);
            if (Boolean.TRUE.equals(processing)) {
                log.debug("Skip compensation because task is being processed by consumer: {}", task.getTaskId());
                continue;
            }

            Integer compensationCount = task.getCompensationCount();
            if (compensationCount == null) {
                compensationCount = 0;
            }

            if (compensationCount >= MAX_COMPENSATION_COUNT) {
                task.setStatus(TaskStatus.FAILED);
                task.setErrorMsg("补偿次数耗尽，需人工介入");
                taskMapper.updateById(task);
                log.warn("Task compensation exhausted and marked failed: taskId={}, compensationCount={}", task.getTaskId(), compensationCount);
                continue;
            }

            task.setCompensationCount(compensationCount + 1);
            taskMapper.updateById(task);
            taskProducer.send(task.getTaskId(), task.getTaskType(), task.getParams());
            log.info("Task compensated and resent to MQ: taskId={}, compensationCount={}", task.getTaskId(), task.getCompensationCount());
        }
    }
}
