package com.fund.trade.application;

/**
 * 业务错误码（对应设计文档第八章 API 错误码约定）。
 */
public enum ErrorCode {

    PARAM_INVALID("40000", "请求参数不合法"),
    PRODUCT_SUSPENDED("40001", "产品暂停申购"),
    BELOW_MIN_SUBSCRIPTION("40002", "申购金额低于起购金额"),
    INSUFFICIENT_FUNDS("40003", "资金不足"),
    INSUFFICIENT_SHARES("40004", "可用份额不足"),
    PRODUCT_SUSPENDED_REDEMPTION("40005", "产品暂停赎回"),

    PRODUCT_NOT_FOUND("40401", "产品不存在"),
    ORDER_NOT_FOUND("40402", "订单不存在"),
    POSITION_NOT_FOUND("40403", "无持仓记录"),
    FUNDS_ACCOUNT_NOT_FOUND("40404", "资金账户不存在");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
