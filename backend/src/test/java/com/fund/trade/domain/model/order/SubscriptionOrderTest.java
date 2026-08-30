package com.fund.trade.domain.model.order;

import com.fund.trade.domain.model.product.FundProduct;
import com.fund.trade.domain.model.product.RiskLevel;
import com.fund.trade.domain.valueobject.Money;
import com.fund.trade.domain.valueobject.NetValue;
import com.fund.trade.domain.valueobject.Rate;
import com.fund.trade.domain.valueobject.Share;
import com.fund.trade.domain.valueobject.TradeDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 申购单聚合单元测试。
 * 关联场景：S-05、S-06、S-08、S-09、S-10、S-11（下单校验、状态机、T+1 确认计算）
 */
class SubscriptionOrderTest {

    /** 在售产品：起购 100 元，申购费率 1.0% */
    private FundProduct product() {
        return FundProduct.onSale("P001", "F001", "示例成长混合基金",
                Money.of("100"), Rate.of("0.01"), RiskLevel.L3);
    }

    private TradeDate tDay() {
        return TradeDate.of(LocalDate.of(2026, 9, 1));
    }

    @Test
    @DisplayName("S-06：下单成功，初始状态为已创建（待冻结资金）")
    void GIVEN_在售产品_WHEN_下单申购10000_THEN_订单创建成功() {
        SubscriptionOrder order = SubscriptionOrder.place("C001", product(),
                Money.of("10000"), tDay());
        assertNotNull(order.getOrderId());
        assertEquals(SubscriptionStatus.CREATED, order.getStatus());
        assertEquals(Money.of("10000"), order.getSubscriptionAmount());
        assertEquals(tDay(), order.getTDay());
    }

    @Test
    @DisplayName("S-05：暂停申购的产品下单失败")
    void GIVEN_暂停产品_WHEN_下单_THEN_抛出产品暂停异常() {
        FundProduct suspended = product();
        suspended.suspend();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SubscriptionOrder.place("C001", suspended, Money.of("10000"), tDay()));
        assertEquals("产品已暂停申购", ex.getMessage());
    }

    @Test
    @DisplayName("S-08：低于起购金额下单失败")
    void GIVEN_起购100_WHEN_申购50_THEN_抛出起购金额异常() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SubscriptionOrder.place("C001", product(), Money.of("50"), tDay()));
        assertTrue(ex.getMessage().contains("起购金额"));
    }

    @Test
    @DisplayName("资金冻结成功后状态流转：已创建 → 资金已冻结（待确认）")
    void GIVEN_已创建订单_WHEN_资金冻结成功_THEN_状态为待确认() {
        SubscriptionOrder order = SubscriptionOrder.place("C001", product(),
                Money.of("10000"), tDay());
        order.markFundsFrozen();
        assertEquals(SubscriptionStatus.FUNDS_FROZEN, order.getStatus());
    }

    @Test
    @DisplayName("资金冻结失败后订单关闭（状态：已创建 → 已关闭）")
    void GIVEN_已创建订单_WHEN_资金冻结失败_THEN_状态为已关闭并记录原因() {
        SubscriptionOrder order = SubscriptionOrder.place("C001", product(),
                Money.of("10000"), tDay());
        order.markFundsFreezeFailed("资金不足");
        assertEquals(SubscriptionStatus.CLOSED, order.getStatus());
        assertEquals("资金不足", order.getFailReason());
    }

    @Test
    @DisplayName("S-09：T+1 确认成功（外扣法）：10000 元、费率 1%、净值 1.2500 → 份额 7920.79、费用 99.01")
    void GIVEN_待确认申购单10000_WHEN_确认净值1点25_THEN_份额7920点79() {
        SubscriptionOrder order = SubscriptionOrder.place("C001", product(),
                Money.of("10000"), tDay());
        order.markFundsFrozen();
        NetValue nav = NetValue.of("P001", tDay().date(), "1.2500");
        order.confirm(nav, product());
        assertEquals(SubscriptionStatus.CONFIRMED, order.getStatus());
        assertEquals(Share.of("7920.79"), order.getConfirmedShares());
        assertEquals(Money.of("99.01"), order.getConfirmedFee());
        assertEquals(nav, order.getConfirmedNetValue());
    }

    @Test
    @DisplayName("S-11：确认失败时状态转确认失败并记录原因")
    void GIVEN_待确认申购单_WHEN_确认失败_THEN_状态为确认失败() {
        SubscriptionOrder order = SubscriptionOrder.place("C001", product(),
                Money.of("10000"), tDay());
        order.markFundsFrozen();
        order.fail("T日净值未发布，确认超时");
        assertEquals(SubscriptionStatus.CONFIRM_FAILED, order.getStatus());
        assertEquals("T日净值未发布，确认超时", order.getFailReason());
    }

    @Test
    @DisplayName("状态机保护：未冻结资金的订单不能直接确认")
    void GIVEN_已创建订单_WHEN_直接确认_THEN_抛出非法状态异常() {
        SubscriptionOrder order = SubscriptionOrder.place("C001", product(),
                Money.of("10000"), tDay());
        NetValue nav = NetValue.of("P001", tDay().date(), "1.2500");
        assertThrows(IllegalStateException.class, () -> order.confirm(nav, product()));
    }

    @Test
    @DisplayName("状态机保护：已确认订单不能重复确认")
    void GIVEN_已确认订单_WHEN_重复确认_THEN_抛出非法状态异常() {
        SubscriptionOrder order = SubscriptionOrder.place("C001", product(),
                Money.of("10000"), tDay());
        order.markFundsFrozen();
        order.confirm(NetValue.of("P001", tDay().date(), "1.2500"), product());
        assertThrows(IllegalStateException.class,
                () -> order.confirm(NetValue.of("P001", tDay().date(), "1.2500"), product()));
    }

    @Test
    @DisplayName("状态机保护：资金冻结成功后不能再执行冻结失败流转")
    void GIVEN_资金已冻结订单_WHEN_执行冻结失败流转_THEN_抛出非法状态异常() {
        SubscriptionOrder order = SubscriptionOrder.place("C001", product(),
                Money.of("10000"), tDay());
        order.markFundsFrozen();
        assertThrows(IllegalStateException.class, () -> order.markFundsFreezeFailed("资金不足"));
    }

    @Test
    @DisplayName("确认校验：净值所属产品与订单产品不一致时抛出异常")
    void GIVEN_订单为P001_WHEN_用P002净值确认_THEN_抛出参数异常() {
        SubscriptionOrder order = SubscriptionOrder.place("C001", product(),
                Money.of("10000"), tDay());
        order.markFundsFrozen();
        NetValue wrongNav = NetValue.of("P002", tDay().date(), "1.0000");
        assertThrows(IllegalArgumentException.class, () -> order.confirm(wrongNav, product()));
    }

    @Test
    @DisplayName("申购金额必须为正数")
    void GIVEN_申购金额为零_WHEN_下单_THEN_抛出参数异常() {
        assertThrows(IllegalArgumentException.class,
                () -> SubscriptionOrder.place("C001", product(), Money.zero(), tDay()));
    }
}
