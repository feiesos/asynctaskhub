package org.feiesos.asynctaskhub.mapper;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.feiesos.asynctaskhub.entity.Task;
import org.feiesos.asynctaskhub.entity.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration"
})
@Testcontainers
class TaskMapperTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("asynctaskhub")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private TaskMapper taskMapper;

    @Test
    void insertAndSelectById() {
        Task task = new Task();
        task.setTaskId(UUID.randomUUID());
        task.setTaskType("IMAGE_RESIZE");
        task.setFilePath("/data/input/test.jpg");
        task.setStatus(TaskStatus.PENDING);
        task.setParams(Map.of("width", 800, "height", 600));

        int rows = taskMapper.insert(task);
        assertThat(rows).isEqualTo(1);
        assertThat(task.getTaskId()).isNotNull();

        Task found = taskMapper.selectById(task.getTaskId());
        assertThat(found).isNotNull();
        assertThat(found.getTaskType()).isEqualTo("IMAGE_RESIZE");
        assertThat(found.getFilePath()).isEqualTo("/data/input/test.jpg");
        assertThat(found.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(found.getParams()).containsEntry("width", 800);
    }

    @Test
    void updateStatus() {
        Task task = new Task();
        task.setTaskId(UUID.randomUUID());
        task.setTaskType("IMAGE_FILTER");
        task.setFilePath("/data/input/photo.png");
        task.setStatus(TaskStatus.PENDING);
        taskMapper.insert(task);

        UUID taskId = task.getTaskId();

        task.setStatus(TaskStatus.PROCESSING);
        int rows = taskMapper.updateById(task);
        assertThat(rows).isEqualTo(1);

        Task updated = taskMapper.selectById(taskId);
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.PROCESSING);
    }

    @Test
    void insertWithAllFields() {
        Task task = new Task();
        task.setTaskId(UUID.randomUUID());
        task.setTaskType("IMAGE_COMPRESS");
        task.setFilePath("/data/video/clip.mp4");
        task.setParams(Map.of("quality", 85, "format", "webp"));
        task.setStatus(TaskStatus.SUCCESS);
        task.setResultPath("/data/output/clip_result.webp");
        task.setRetryCount(2);
        task.setErrorMsg("Initial attempt failed, retried");

        int rows = taskMapper.insert(task);
        assertThat(rows).isEqualTo(1);
        assertThat(task.getTaskId()).isNotNull();

        Task found = taskMapper.selectById(task.getTaskId());
        assertThat(found.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(found.getResultPath()).isEqualTo("/data/output/clip_result.webp");
        assertThat(found.getRetryCount()).isEqualTo(2);
        assertThat(found.getErrorMsg()).isEqualTo("Initial attempt failed, retried");
        assertThat(found.getParams()).containsEntry("quality", 85);
        assertThat(found.getCreateTime()).isNotNull();
        assertThat(found.getUpdateTime()).isNotNull();
    }
}
