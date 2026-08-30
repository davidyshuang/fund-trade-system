package com.fund.trade.domain.model.product;

import com.fund.trade.domain.valueobject.Money;
import com.fund.trade.domain.valueobject.Rate;
import com.fund.trade.domain.valueobject.Share;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基金产品聚合单元测试。
 * 关联场景：S-05（暂停申购）、S-08（起购金额）、S-09（申购费外扣法）、S-15（赎回费率梯度）
 */
class FundProductTest {

    /** 在售产品：起购 100 元，申购费率 1.0%，默认赎回费率梯度 */
    private FundProduct product() {
        return FundProduct.onSale("P001", "F001", "示例成长混合基金",
                Money.of("100"), Rate.of("0.01"), RiskLevel.L3);
    }

    @Test
    @DisplayName("S-05：暂停申购的产品 canSubscribe 为 false，恢复上架后为 true")
    void GIVEN_暂停产品_WHEN_判断可申购_THEN_返回false() {
        FundProduct product = product();
        product.suspend();
        assertFalse(product.canSubscribe());
        assertFalse(product.canRedeem());
        product.placeOnSale();
        assertTrue(product.canSubscribe());
    }

    @Test
    @DisplayName("S-09：申购费外扣法 = 申购金额 − 申购金额÷(1+费率)（10000 元 1% 费率 → 99.01）")
    void GIVEN_申购10000_费率1个点_WHEN_计算申购费_THEN_费用99点01() {
        assertEquals(Money.of("99.01"), product().calcSubscriptionFee(Money.of("10000")));
    }

    @Test
    @DisplayName("S-08：低于起购金额校验失败")
    void GIVEN_起购1000_WHEN_申购500_THEN_校验抛出异常() {
        FundProduct product = FundProduct.onSale("P002", "F002", "稳健债券基金",
                Money.of("1000"), Rate.of("0.005"), RiskLevel.L2);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> product.validateSubscription(Money.of("500")));
        assertTrue(ex.getMessage().contains("起购金额"));
    }

    @Test
    @DisplayName("S-05：暂停申购产品下单校验抛出异常")
    void GIVEN_暂停产品_WHEN_下单校验_THEN_抛出产品暂停异常() {
        FundProduct product = product();
        product.suspend();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> product.validateSubscription(Money.of("1000")));
        assertEquals("产品已暂停申购", ex.getMessage());
    }

    @Test
    @DisplayName("赎回费率梯度：<7天1.5%；7~365天0.5%；366~730天0.25%；>730天0%")
    void 赎回费率梯度() {
        FundProduct product = product();
        Money gross = Money.of("10000");
        // 持有 6 天 → 1.5% = 150.00
        assertEquals(Money.of("150.00"), product.calcRedemptionFee(gross, 6));
        // 持有 7 天 → 0.5% = 50.00
        assertEquals(Money.of("50.00"), product.calcRedemptionFee(gross, 7));
        // 持有 365 天 → 0.5%
        assertEquals(Money.of("50.00"), product.calcRedemptionFee(gross, 365));
        // 持有 366 天 → 0.25% = 25.00
        assertEquals(Money.of("25.00"), product.calcRedemptionFee(gross, 366));
        // 持有 730 天 → 0.25%
        assertEquals(Money.of("25.00"), product.calcRedemptionFee(gross, 730));
        // 持有 731 天 → 0%
        assertEquals(Money.zero(), product.calcRedemptionFee(gross, 731));
    }

    @Test
    @DisplayName("赎回份额必须为正数")
    void GIVEN_赎回零份_WHEN_赎回校验_THEN_抛出参数异常() {
        assertThrows(IllegalArgumentException.class,
                () -> product().validateRedemption(Share.zero()));
    }

    @Test
    @DisplayName("产品创建即校验：费率必须在 [0,1) 区间")
    void GIVEN_非法费率_WHEN_创建产品_THEN_抛出参数异常() {
        assertThrows(IllegalArgumentException.class,
                () -> FundProduct.onSale("P003", "F003", "坏费率基金",
                        Money.of("100"), Rate.of("1.5"), RiskLevel.L3));
    }
}
