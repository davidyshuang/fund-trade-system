package com.fund.trade.domain.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 份额值对象单元测试。
 * 关联场景：S-09（确认份额 7920.79）、S-13（份额冻结）
 */
class ShareTest {

    @Test
    @DisplayName("份额固定保留 2 位小数并四舍五入")
    void 份额精度为两位小数() {
        assertEquals("7920.79", Share.of("7920.792").value().toPlainString());
        assertEquals("10000.00", Share.of("10000").value().toPlainString());
    }

    @Test
    @DisplayName("份额加减运算")
    void 加减运算() {
        Share total = Share.of("10000");
        assertEquals("2000.00", total.subtract(Share.of("8000")).value().toPlainString());
        assertEquals("10200.00", total.add(Share.of("200")).value().toPlainString());
    }

    @Test
    @DisplayName("份额比较")
    void 比较() {
        assertTrue(Share.of("8000").compareTo(Share.of("7999.99")) > 0);
        assertEquals(Share.of("100"), Share.of("100.00"));
    }
}
