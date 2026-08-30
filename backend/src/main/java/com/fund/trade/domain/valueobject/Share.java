package com.fund.trade.domain.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 份额值对象。
 * 精度固定 2 位小数（HALF_UP 四舍五入），不可变。
 */
public final class Share implements Comparable<Share> {

    private final BigDecimal amount;

    private Share(BigDecimal amount) {
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** 工厂方法：基于 BigDecimal 创建份额 */
    public static Share of(BigDecimal amount) {
        return new Share(Objects.requireNonNull(amount, "份额不能为空"));
    }

    /** 工厂方法：基于字符串创建份额，如 Share.of("7920.79") */
    public static Share of(String amount) {
        return of(new BigDecimal(amount));
    }

    /** 零份额 */
    public static Share zero() {
        return new Share(BigDecimal.ZERO);
    }

    public BigDecimal value() {
        return amount;
    }

    public Share add(Share other) {
        return new Share(amount.add(other.amount));
    }

    public Share subtract(Share other) {
        return new Share(amount.subtract(other.amount));
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    @Override
    public int compareTo(Share other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Share share)) {
            return false;
        }
        return amount.compareTo(share.amount) == 0;
    }

    @Override
    public int hashCode() {
        return amount.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
