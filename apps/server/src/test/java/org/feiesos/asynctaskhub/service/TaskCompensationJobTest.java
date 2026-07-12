package org.feiesos.asynctaskhub.service;

import org.feiesos.asynctaskhub.entity.Task;
import org.feiesos.asynctaskhub.entity.TaskStatus;
import org.feiesos.asynctaskhub.mapper.TaskMapper;
import org.feiesos.asynctaskhub.mq.TaskProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskCompensationJobTest {

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private TaskProducer taskProducer;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    private Clock fixedClock;

    private TaskCompensationJob taskCompensationJob;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneId.of("UTC"));
        taskCompensationJob = new TaskCompensationJob(taskMapper, taskProducer, redisTemplate, fixedClock);
    }

    @Test
    void skipCompensationWhenRedisLockExists() {
        UUID taskId = UUID.randomUUID();
        Task pendingTask = new Task();
        pendingTask.setTaskId(taskId);
        pendingTask.setStatus(TaskStatus.PENDING);
        pendingTask.setCreateTime(LocalDateTime.of(2026, 7, 12, 11, 57));
        pendingTask.setCompensationCount(0);

        when(taskMapper.selectList(any())).thenReturn(List.of(pendingTask));
        when(redisTemplate.hasKey("task:processing:" + taskId)).thenReturn(true);

        taskCompensationJob.compensatePendingTasks();

        verify(taskProducer, never()).send(any(), anyString(), any());
        verify(taskMapper, never()).updateById(any(Task.class));
    }

    @Test
    void markAsFailedWhenCompensationCountExhausted() {
        UUID taskId = UUID.randomUUID();
        Task pendingTask = new Task();
        pendingTask.setTaskId(taskId);
        pendingTask.setStatus(TaskStatus.PENDING);
        pendingTask.setCreateTime(LocalDateTime.of(2026, 7, 12, 11, 57));
        pendingTask.setCompensationCount(3);

        when(taskMapper.selectList(any())).thenReturn(List.of(pendingTask));
        when(redisTemplate.hasKey("task:processing:" + taskId)).thenReturn(false);

        taskCompensationJob.compensatePendingTasks();

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateById(taskCaptor.capture());
        Task updatedTask = taskCaptor.getValue();

        assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(updatedTask.getErrorMsg()).isEqualTo("补偿次数耗尽，需人工介入");
        verify(taskProducer, never()).send(any(), anyString(), any());
    }

    @Test
    void normalCompensationIncrementsCountAndResends() {
        UUID taskId = UUID.randomUUID();
        Task pendingTask = new Task();
        pendingTask.setTaskId(taskId);
        pendingTask.setStatus(TaskStatus.PENDING);
        pendingTask.setCreateTime(LocalDateTime.of(2026, 7, 12, 11, 57));
        pendingTask.setCompensationCount(1);

        when(taskMapper.selectList(any())).thenReturn(List.of(pendingTask));
        when(redisTemplate.hasKey("task:processing:" + taskId)).thenReturn(false);

        taskCompensationJob.compensatePendingTasks();

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateById(taskCaptor.capture());
        Task updatedTask = taskCaptor.getValue();

        assertThat(updatedTask.getCompensationCount()).isEqualTo(2);
        verify(taskProducer).send(taskId, updatedTask.getTaskType(), updatedTask.getParams());
    }
}
