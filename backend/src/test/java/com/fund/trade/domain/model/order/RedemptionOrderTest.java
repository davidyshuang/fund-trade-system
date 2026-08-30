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

/**
 * 赎回单聚合单元测试。
 * 关联场景：S-13、S-14、S-15、S-16（下单、份额冻结状态机、T+1 确认金额计算）
 */
class RedemptionOrderTest {

    /** 在售产品：申购费率 1.0%，默认赎回费率梯度 */
    private FundProduct product() {
        return FundProduct.onSale("P001", "F001", "示例成长混合基金",
                Money.of("100"), Rate.of("0.01"), RiskLevel.L3);
    }

    private TradeDate tDay() {
        return TradeDate.of(LocalDate.of(2026, 9, 1));
    }

    @Test
    @DisplayName("S-13：赎回下单成功，初始状态为已创建（待冻结份额）")
    void GIVEN_在售产品_WHEN_下单赎回8000份_THEN_订单创建成功() {
        RedemptionOrder order = RedemptionOrder.place("C001", product(),
                Share.of("8000"), tDay());
        assertNotNull(order.getOrderId());
        assertEquals(RedemptionStatus.CREATED, order.getStatus());
        assertEquals(Share.of("8000"), order.getRedemptionShares());
    }

    @Test
    @DisplayName("份额冻结成功后状态流转：已创建 → 份额已冻结（待确认）")
    void GIVEN_已创建订单_WHEN_份额冻结成功_THEN_状态为待确认() {
        RedemptionOrder order = RedemptionOrder.place("C001", product(),
                Share.of("8000"), tDay());
        order.markSharesFrozen();
        assertEquals(RedemptionStatus.SHARES_FROZEN, order.getStatus());
    }

    @Test
    @DisplayName("份额冻结失败后订单关闭并记录原因")
    void GIVEN_已创建订单_WHEN_份额冻结失败_THEN_状态为已关闭() {
        RedemptionOrder order = RedemptionOrder.place("C001", product(),
                Share.of("8000"), tDay());
        order.markSharesFreezeFailed("可用份额不足");
        assertEquals(RedemptionStatus.CLOSED, order.getStatus());
        assertEquals("可用份额不足", order.getFailReason());
    }

    @Test
    @DisplayName("S-15：T+1 确认成功：5000 份、净值 1.3000、持有 800 天（费率 0%）→ 金额 6500.00")
    void GIVEN_待确认赎回5000份_WHEN_确认净值1点3_THEN_金额6500() {
        RedemptionOrder order = RedemptionOrder.place("C001", product(),
                Share.of("5000"), tDay());
        order.markSharesFrozen();
        NetValue nav = NetValue.of("P001", tDay().date(), "1.3000");
        order.confirm(nav, product(), 800);
        assertEquals(RedemptionStatus.CONFIRMED, order.getStatus());
        assertEquals(Money.of("6500.00"), order.getRedemptionAmount());
        assertEquals(Money.zero(), order.getRedemptionFee());
        assertEquals(nav, order.getConfirmedNetValue());
    }

    @Test
    @DisplayName("赎回费梯度：4000 份、净值 1.3000、持有 30 天（费率 0.5%）→ 总额 5200、费 26.00、金额 5174.00")
    void GIVEN_待确认赎回4000份持有30天_WHEN_确认_THEN_金额5174() {
        RedemptionOrder order = RedemptionOrder.place("C001", product(),
                Share.of("4000"), tDay());
        order.markSharesFrozen();
        NetValue nav = NetValue.of("P001", tDay().date(), "1.3000");
        order.confirm(nav, product(), 30);
        assertEquals(Money.of("26.00"), order.getRedemptionFee());
        assertEquals(Money.of("5174.00"), order.getRedemptionAmount());
    }

    @Test
    @DisplayName("S-16：确认失败时状态转确认失败并记录原因")
    void GIVEN_待确认赎回单_WHEN_确认失败_THEN_状态为确认失败() {
        RedemptionOrder order = RedemptionOrder.place("C001", product(),
                Share.of("8000"), tDay());
        order.markSharesFrozen();
        order.fail("净值异常");
        assertEquals(RedemptionStatus.CONFIRM_FAILED, order.getStatus());
        assertEquals("净值异常", order.getFailReason());
    }

    @Test
    @DisplayName("状态机保护：未冻结份额的订单不能直接确认")
    void GIVEN_已创建订单_WHEN_直接确认_THEN_抛出非法状态异常() {
        RedemptionOrder order = RedemptionOrder.place("C001", product(),
                Share.of("8000"), tDay());
        NetValue nav = NetValue.of("P001", tDay().date(), "1.3000");
        assertThrows(IllegalStateException.class, () -> order.confirm(nav, product(), 30));
    }

    @Test
    @DisplayName("状态机保护：已确认订单不能重复确认")
    void GIVEN_已确认订单_WHEN_重复确认_THEN_抛出非法状态异常() {
        RedemptionOrder order = RedemptionOrder.place("C001", product(),
                Share.of("5000"), tDay());
        order.markSharesFrozen();
        order.confirm(NetValue.of("P001", tDay().date(), "1.3000"), product(), 800);
        assertThrows(IllegalStateException.class,
                () -> order.confirm(NetValue.of("P001", tDay().date(), "1.3000"), product(), 800));
    }

    @Test
    @DisplayName("确认校验：净值所属产品与订单产品不一致时抛出异常")
    void GIVEN_订单为P001_WHEN_用P002净值确认_THEN_抛出参数异常() {
        RedemptionOrder order = RedemptionOrder.place("C001", product(),
                Share.of("5000"), tDay());
        order.markSharesFrozen();
        NetValue wrongNav = NetValue.of("P002", tDay().date(), "1.0000");
        assertThrows(IllegalArgumentException.class, () -> order.confirm(wrongNav, product(), 30));
    }

    @Test
    @DisplayName("暂停赎回的产品下单失败")
    void GIVEN_暂停产品_WHEN_下单赎回_THEN_抛出产品暂停异常() {
        FundProduct suspended = product();
        suspended.suspend();
        assertThrows(IllegalStateException.class,
                () -> RedemptionOrder.place("C001", suspended, Share.of("5000"), tDay()));
    }

    @Test
    @DisplayName("赎回份额必须为正数")
    void GIVEN_赎回零份_WHEN_下单_THEN_抛出参数异常() {
        assertThrows(IllegalArgumentException.class,
                () -> RedemptionOrder.place("C001", product(), Share.zero(), tDay()));
    }
}
