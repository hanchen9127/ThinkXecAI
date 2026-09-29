package com.thinkxecai.exception;

import com.thinkxecai.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException ex) {
        log.warn("Business exception: {}", ex.getMessage());
        return ex.getCode() != null
                ? ApiResponse.error(ex.getCode(), ex.getMessage())
                : ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ApiResponse.error(ex.getMessage());
    }

}
