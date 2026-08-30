package com.fund.trade.domain.model.funds;

import com.fund.trade.domain.valueobject.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 资金账户聚合单元测试。
 * 关联场景：S-06（冻结资金）、S-07（资金不足）、S-09（确认扣款）、S-11（解冻退款）、S-15（赎回款入账）
 */
class FundsAccountTest {

    @Test
    @DisplayName("S-06：余额充足时冻结成功，可用余额同步扣减")
    void GIVEN_余额10000_WHEN_冻结5000_THEN_可用余额5000() {
        FundsAccount account = new FundsAccount("FA001", "C001", Money.of("10000"));
        account.freeze(Money.of("5000"));
        assertEquals(Money.of("10000"), account.getBalance());
        assertEquals(Money.of("5000"), account.getFrozenAmount());
        assertEquals(Money.of("5000"), account.availableAmount());
    }

    @Test
    @DisplayName("S-07：余额不足冻结失败，账户无任何变动")
    void GIVEN_可用余额3000_WHEN_冻结5000_THEN_抛出资金不足异常() {
        FundsAccount account = new FundsAccount("FA001", "C001", Money.of("3000"));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> account.freeze(Money.of("5000")));
        assertEquals("资金不足", ex.getMessage());
        assertEquals(Money.of("3000"), account.getBalance());
        assertEquals(Money.zero(), account.getFrozenAmount());
    }

    @Test
    @DisplayName("冻结金额超过可用余额（部分已被冻结）时失败")
    void GIVEN_总余额10000冻结6000_WHEN_再冻结5000_THEN_抛出资金不足异常() {
        FundsAccount account = new FundsAccount("FA001", "C001", Money.of("10000"));
        account.freeze(Money.of("6000"));
        // 可用余额仅 4000
        assertThrows(IllegalStateException.class, () -> account.freeze(Money.of("5000")));
    }

    @Test
    @DisplayName("S-09：申购确认后扣除冻结资金（余额与冻结同时减少）")
    void GIVEN_已冻结5000_WHEN_扣除冻结5000_THEN_余额与冻结均减少5000() {
        FundsAccount account = new FundsAccount("FA001", "C001", Money.of("10000"));
        account.freeze(Money.of("5000"));
        account.deductFrozen(Money.of("5000"));
        assertEquals(Money.of("5000"), account.getBalance());
        assertEquals(Money.zero(), account.getFrozenAmount());
        assertEquals(Money.of("5000"), account.availableAmount());
    }

    @Test
    @DisplayName("S-11：确认失败时解冻退回，可用余额恢复")
    void GIVEN_已冻结5000_WHEN_解冻5000_THEN_可用余额恢复10000() {
        FundsAccount account = new FundsAccount("FA001", "C001", Money.of("10000"));
        account.freeze(Money.of("5000"));
        account.unfreeze(Money.of("5000"));
        assertEquals(Money.of("10000"), account.getBalance());
        assertEquals(Money.zero(), account.getFrozenAmount());
        assertEquals(Money.of("10000"), account.availableAmount());
    }

    @Test
    @DisplayName("解冻金额不能超过已冻结金额")
    void GIVEN_已冻结3000_WHEN_解冻5000_THEN_抛出异常() {
        FundsAccount account = new FundsAccount("FA001", "C001", Money.of("10000"));
        account.freeze(Money.of("3000"));
        assertThrows(IllegalStateException.class, () -> account.unfreeze(Money.of("5000")));
    }

    @Test
    @DisplayName("S-15：赎回款入账增加总余额与可用余额")
    void GIVEN_余额10000_WHEN_入账6500_THEN_余额16500() {
        FundsAccount account = new FundsAccount("FA001", "C001", Money.of("10000"));
        account.credit(Money.of("6500"));
        assertEquals(Money.of("16500"), account.getBalance());
        assertEquals(Money.of("16500"), account.availableAmount());
    }

    @Test
    @DisplayName("冻结金额必须为正数")
    void GIVEN_冻结金额为零_WHEN_冻结_THEN_抛出参数异常() {
        FundsAccount account = new FundsAccount("FA001", "C001", Money.of("10000"));
        assertThrows(IllegalArgumentException.class, () -> account.freeze(Money.zero()));
        assertThrows(IllegalArgumentException.class, () -> account.freeze(Money.of("-100")));
    }
}
