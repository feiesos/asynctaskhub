package org.feiesos.asynctaskhub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.feiesos.asynctaskhub.config.JsonbTypeHandler;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@TableName(value = "task", autoResultMap = true)
public class Task {

    @TableId(type = IdType.NONE)
    private UUID taskId;

    private String taskType;

    private String filePath;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> params;

    private TaskStatus status;

    private String resultPath;

    private Integer retryCount;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
