package com.fund.trade.domain.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 金额值对象。
 * 精度固定 2 位小数（HALF_UP 四舍五入），不可变。
 */
public final class Money implements Comparable<Money> {

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** 工厂方法：基于 BigDecimal 创建金额 */
    public static Money of(BigDecimal amount) {
        return new Money(Objects.requireNonNull(amount, "金额不能为空"));
    }

    /** 工厂方法：基于字符串创建金额，如 Money.of("10000.00") */
    public static Money of(String amount) {
        return of(new BigDecimal(amount));
    }

    /** 零元 */
    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public BigDecimal value() {
        return amount;
    }

    public Money add(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    /** 乘以系数（如净值），结果保持 2 位小数 */
    public Money multiply(BigDecimal factor) {
        return new Money(amount.multiply(factor));
    }

    /** 除以除数，结果四舍五入保留 2 位小数 */
    public Money divide(BigDecimal divisor) {
        return new Money(amount.divide(divisor, 2, RoundingMode.HALF_UP));
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money money)) {
            return false;
        }
        return amount.compareTo(money.amount) == 0;
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
