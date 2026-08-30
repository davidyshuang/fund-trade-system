package com.fund.trade.application;

/**
 * 业务异常（application 层抛出，api 层统一转换为响应体）。
 */
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.code = errorCode.code();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.code();
    }

    public String getCode() {
        return code;
    }
}
