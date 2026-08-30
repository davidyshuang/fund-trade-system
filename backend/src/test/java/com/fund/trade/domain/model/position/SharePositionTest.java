package com.fund.trade.domain.model.position;

import com.fund.trade.domain.valueobject.Share;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 持仓（TA 账户份额）聚合单元测试。
 * 关联场景：S-12（申购确认入账）、S-13（份额冻结）、S-14（可用份额不足）、S-15（赎回扣减）、S-16（份额解冻）
 */
class SharePositionTest {

    @Test
    @DisplayName("S-13：赎回下单冻结可用份额，总份额不变")
    void GIVEN_总份额10000_WHEN_冻结8000_THEN_可用2000_冻结8000() {
        SharePosition position = new SharePosition("TA001", "C001", "P001",
                Share.of("10000"), Share.zero());
        position.freeze(Share.of("8000"));
        assertEquals(Share.of("10000"), position.getTotalShares());
        assertEquals(Share.of("8000"), position.getFrozenShares());
        assertEquals(Share.of("2000"), position.availableShares());
    }

    @Test
    @DisplayName("S-14：可用份额不足时冻结失败，持仓无变动")
    void GIVEN_可用份额2000_WHEN_冻结5000_THEN_抛出可用份额不足异常() {
        SharePosition position = new SharePosition("TA001", "C001", "P001",
                Share.of("10000"), Share.of("8000")); // 可用仅 2000
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> position.freeze(Share.of("5000")));
        assertEquals("可用份额不足", ex.getMessage());
        assertEquals(Share.of("10000"), position.getTotalShares());
        assertEquals(Share.of("8000"), position.getFrozenShares());
    }

    @Test
    @DisplayName("S-12：申购确认后份额入账，总份额与可用份额同步增加")
    void GIVEN_无持仓_WHEN_入账7920点79份_THEN_总份额与可用份额一致() {
        SharePosition position = new SharePosition("TA001", "C001", "P001");
        position.increase(Share.of("7920.79"));
        assertEquals(Share.of("7920.79"), position.getTotalShares());
        assertEquals(Share.of("7920.79"), position.availableShares());
    }

    @Test
    @DisplayName("S-15：赎回确认后扣减并解冻份额（总份额与冻结份额同时减少）")
    void GIVEN_总10000冻结8000_WHEN_赎回扣减8000_THEN_总2000_冻结0() {
        SharePosition position = new SharePosition("TA001", "C001", "P001",
                Share.of("10000"), Share.of("8000"));
        position.decreaseAndUnfreeze(Share.of("8000"));
        assertEquals(Share.of("2000"), position.getTotalShares());
        assertEquals(Share.zero(), position.getFrozenShares());
        assertEquals(Share.of("2000"), position.availableShares());
    }

    @Test
    @DisplayName("S-16：赎回确认失败时解冻份额，恢复可用")
    void GIVEN_总10000冻结8000_WHEN_解冻8000_THEN_可用恢复10000() {
        SharePosition position = new SharePosition("TA001", "C001", "P001",
                Share.of("10000"), Share.of("8000"));
        position.unfreeze(Share.of("8000"));
        assertEquals(Share.of("10000"), position.getTotalShares());
        assertEquals(Share.zero(), position.getFrozenShares());
        assertEquals(Share.of("10000"), position.availableShares());
    }

    @Test
    @DisplayName("解冻份额不能超过已冻结份额")
    void GIVEN_冻结3000_WHEN_解冻5000_THEN_抛出异常() {
        SharePosition position = new SharePosition("TA001", "C001", "P001",
                Share.of("10000"), Share.of("3000"));
        assertThrows(IllegalStateException.class, () -> position.unfreeze(Share.of("5000")));
    }

    @Test
    @DisplayName("不变量：总份额 = 可用份额 + 冻结份额 恒成立（部分冻结后部分赎回）")
    void 不变量_总份额等于可用加冻结() {
        SharePosition position = new SharePosition("TA001", "C001", "P001",
                Share.of("10000"), Share.zero());
        position.freeze(Share.of("3000"));
        position.decreaseAndUnfreeze(Share.of("1000"));
        assertEquals(position.getTotalShares(),
                position.availableShares().add(position.getFrozenShares()));
    }

    @Test
    @DisplayName("冻结份额必须为正数")
    void GIVEN_冻结零份_WHEN_冻结_THEN_抛出参数异常() {
        SharePosition position = new SharePosition("TA001", "C001", "P001",
                Share.of("10000"), Share.zero());
        assertThrows(IllegalArgumentException.class, () -> position.freeze(Share.zero()));
    }
}
