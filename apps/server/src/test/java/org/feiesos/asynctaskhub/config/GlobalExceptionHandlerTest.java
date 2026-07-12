package org.feiesos.asynctaskhub.config;

import org.feiesos.asynctaskhub.common.ApiResponse;
import org.feiesos.asynctaskhub.common.BusinessException;
import org.feiesos.asynctaskhub.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionReturns400WithCodeAndMessage() {
        BusinessException ex = new BusinessException(400, "业务异常");
        ApiResponse<Void> response = handler.handleBusiness(ex);
        assertThat(response.code()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("业务异常");
        assertThat(response.data()).isNull();
    }

    @Test
    void resourceNotFoundExceptionReturns404WithMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("任务不存在");
        ApiResponse<Void> response = handler.handleNotFound(ex);
        assertThat(response.code()).isEqualTo(404);
        assertThat(response.message()).isEqualTo("任务不存在");
        assertThat(response.data()).isNull();
    }

    @Test
    void validationExceptionReturns400WithFieldErrors() {
        Object target = new Object();
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(target, "target");
        binding.addError(new FieldError("target", "name", "must not be blank"));
        binding.addError(new FieldError("target", "age", "must be positive"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, binding);

        ApiResponse<Void> response = handler.handleValidation(ex);

        assertThat(response.code()).isEqualTo(400);
        assertThat(response.message()).contains("name: must not be blank");
        assertThat(response.message()).contains("age: must be positive");
        assertThat(response.data()).isNull();
    }

    @Test
    void unexpectedExceptionReturns500WithGenericMessage() {
        Exception ex = new RuntimeException("数据库连接失败");
        ApiResponse<Void> response = handler.handleUnexpected(ex);
        assertThat(response.code()).isEqualTo(500);
        assertThat(response.message()).isEqualTo("服务器内部错误");
        assertThat(response.data()).isNull();
    }
}
