package com.fund.trade.domain.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 金额值对象单元测试。
 * 关联场景：S-09（申购确认金额计算）、S-15（赎回金额计算）
 */
class MoneyTest {

    @Test
    @DisplayName("金额固定保留 2 位小数并四舍五入")
    void 金额精度为两位小数() {
        assertEquals("10.01", Money.of("10.005").value().toPlainString());
        assertEquals("100.00", Money.of("100").value().toPlainString());
        assertEquals("0.00", Money.of(BigDecimal.ZERO).value().toPlainString());
    }

    @Test
    @DisplayName("金额加减运算保持两位小数")
    void 加减运算() {
        Money a = Money.of("100.00");
        assertEquals("150.50", a.add(Money.of("50.50")).value().toPlainString());
        assertEquals("69.75", a.subtract(Money.of("30.25")).value().toPlainString());
    }

    @Test
    @DisplayName("金额除法四舍五入到分（外扣法净申购金额计算基础）")
    void 除法四舍五入() {
        // S-09 前置计算：10000 ÷ 1.01 = 9900.99
        assertEquals("9900.99",
                Money.of("10000").divide(new BigDecimal("1.01")).value().toPlainString());
        // 100 ÷ 3 = 33.33
        assertEquals("33.33",
                Money.of("100").divide(new BigDecimal("3")).value().toPlainString());
    }

    @Test
    @DisplayName("金额乘法用于份额×净值（S-15：5000×1.3000=6500.00）")
    void 乘法运算() {
        assertEquals("6500.00",
                Money.of("5000").multiply(new BigDecimal("1.3000")).value().toPlainString());
    }

    @Test
    @DisplayName("正负判断与比较")
    void 正负与比较() {
        assertTrue(Money.of("0.01").isPositive());
        assertFalse(Money.of("0.00").isPositive());
        assertTrue(Money.of("-1.00").isNegative());
        assertTrue(Money.of("100").compareTo(Money.of("99.99")) > 0);
        assertEquals(Money.of("100"), Money.of("100.00"));
    }
}
