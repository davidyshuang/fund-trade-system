package com.fund.trade.domain.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 基金净值值对象（估值上下文）。
 * 精度固定 4 位小数。
 */
public record NetValue(String productId, LocalDate navDate, BigDecimal value) {

    public NetValue {
        Objects.requireNonNull(productId, "产品ID不能为空");
        Objects.requireNonNull(navDate, "净值日期不能为空");
        Objects.requireNonNull(value, "净值不能为空");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("净值必须大于 0");
        }
        value = value.setScale(4, RoundingMode.HALF_UP);
    }

    /** 工厂方法 */
    public static NetValue of(String productId, LocalDate navDate, String value) {
        return new NetValue(productId, navDate, new BigDecimal(value));
    }
}
