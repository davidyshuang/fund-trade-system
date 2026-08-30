package com.fund.trade.domain.valueobject;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 交易日期（T 日）值对象。
 */
public record TradeDate(LocalDate date) {

    public TradeDate {
        Objects.requireNonNull(date, "交易日期不能为空");
    }

    public static TradeDate of(LocalDate date) {
        return new TradeDate(date);
    }
}
