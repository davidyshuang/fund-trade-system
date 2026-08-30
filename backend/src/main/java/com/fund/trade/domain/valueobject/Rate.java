package com.fund.trade.domain.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 费率值对象。
 * 精度固定 4 位小数，合法区间 [0, 1)，如 Rate.of("0.01") 表示 1%。
 */
public final class Rate {

    private final BigDecimal value;

    private Rate(BigDecimal value) {
        this.value = value.setScale(4, RoundingMode.HALF_UP);
    }

    /** 工厂方法：基于字符串创建费率，如 Rate.of("0.01") */
    public static Rate of(String value) {
        return of(new BigDecimal(value));
    }

    /** 工厂方法：基于 BigDecimal 创建费率，并校验区间 [0, 1) */
    public static Rate of(BigDecimal value) {
        Objects.requireNonNull(value, "费率不能为空");
        Rate rate = new Rate(value);
        if (rate.value.signum() < 0 || rate.value.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("费率必须在 [0, 1) 区间内");
        }
        return rate;
    }

    public BigDecimal value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Rate rate)) {
            return false;
        }
        return value.compareTo(rate.value) == 0;
    }

    @Override
    public int hashCode() {
        return value.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
