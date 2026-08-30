package com.fund.trade.api;

import com.fund.trade.application.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：将应用层业务异常与领域层非法状态统一转换为标准响应体。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常 → HTTP 400 + 业务错误码 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(Integer.parseInt(e.getCode()), e.getMessage(), null));
    }

    /** 领域层参数/状态非法 → HTTP 400 + 40000 */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(RuntimeException e) {
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(40000, e.getMessage(), null));
    }

    /** 兜底异常 → HTTP 500 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("系统内部错误", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(50000, "系统内部错误", null));
    }
}
