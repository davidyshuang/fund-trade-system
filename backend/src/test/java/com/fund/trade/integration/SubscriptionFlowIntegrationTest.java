package com.fund.trade.integration;

import com.fund.trade.application.BusinessException;
import com.fund.trade.application.ConfirmationAppService;
import com.fund.trade.application.SubscriptionAppService;
import com.fund.trade.domain.model.order.SubscriptionOrder;
import com.fund.trade.domain.model.order.SubscriptionStatus;
import com.fund.trade.domain.repository.SharePositionRepository;
import com.fund.trade.domain.repository.SubscriptionOrderRepository;
import com.fund.trade.testsupport.FundsAccountRepositoryAccessor;
import com.fund.trade.testsupport.TestTimeProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 申购全流程集成测试（PostgreSQL 测试库 + 事件驱动编排）。
 * 对应设计文档场景：S-05 ~ S-11、S-02。
 */
@DisplayName("申购流程集成测试")
class SubscriptionFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private SubscriptionAppService subscriptionAppService;
    @Autowired
    private ConfirmationAppService confirmationAppService;
    @Autowired
    private SubscriptionOrderRepository subscriptionOrderRepository;
    @Autowired
    private SharePositionRepository sharePositionRepository;
    @Autowired
    private FundsAccountRepositoryAccessor fundsAccessor;

    @Test
    @DisplayName("GIVEN 余额充足且产品在售 WHEN 申购 10000 元 THEN 冻结成功订单进入待确认")
    void test_申购下单_资金冻结成功() {
        SubscriptionOrder order = subscriptionAppService.place("C001", "P001", "10000.00");

        assertNotNull(order.getOrderId());
        assertEquals(SubscriptionStatus.FUNDS_FROZEN, order.getStatus());
        assertEquals(LocalDate.of(2026, 9, 24), order.getTDay().date());
        // 资金账户：总余额 20000，冻结 10000，可用 10000
        assertEquals(new BigDecimal("20000.00"), fundsAccessor.balance("C001"));
        assertEquals(new BigDecimal("10000.00"), fundsAccessor.frozen("C001"));
    }

    @Test
    @DisplayName("GIVEN 可用余额不足 WHEN 申购 THEN 抛出资金不足异常且订单关闭资金不变")
    void test_申购下单_资金不足() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> subscriptionAppService.place("C001", "P001", "50000.00"));

        assertEquals("40003", ex.getCode());
        // 订单落库为 CLOSED，失败原因：资金不足
        List<SubscriptionOrder> orders = subscriptionOrderRepository.findByCustomerId("C001");
        assertEquals(1, orders.size());
        assertEquals(SubscriptionStatus.CLOSED, orders.get(0).getStatus());
        // 资金账户不变
        assertEquals(new BigDecimal("20000.00"), fundsAccessor.balance("C001"));
        assertEquals(new BigDecimal("0.00"), fundsAccessor.frozen("C001"));
    }

    @Test
    @DisplayName("GIVEN 申购金额 500 低于起购 1000 WHEN 下单 THEN 抛出 40002 异常")
    void test_申购下单_低于起购金额() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> subscriptionAppService.place("C001", "P001", "500.00"));
        assertEquals("40002", ex.getCode());
    }

    @Test
    @DisplayName("GIVEN 待确认申购单 WHEN 发布 T 日净值 1.2500 并确认 THEN 份额 7920.79 入账资金扣款")
    void test_申购T加1确认_外扣法计算() {
        SubscriptionOrder order = subscriptionAppService.place("C001", "P001", "10000.00");
        LocalDate tDay = order.getTDay().date();

        // 管理端发布 T 日净值
        confirmationAppService.publishNetValue("P001", tDay, "1.2500");

        ConfirmationAppService.ConfirmationSummary summary =
                confirmationAppService.runConfirmations(tDay);

        assertEquals(1, summary.subscriptionConfirmed());
        assertEquals(0, summary.subscriptionFailed());

        SubscriptionOrder confirmed = subscriptionOrderRepository.findById(order.getOrderId()).orElseThrow();
        assertEquals(SubscriptionStatus.CONFIRMED, confirmed.getStatus());
        // 外扣法：净申购 9900.99，申购费 99.01，份额 = 9900.99 / 1.25 = 7920.79
        assertEquals(new BigDecimal("99.01"), confirmed.getConfirmedFee().value());
        assertEquals(new BigDecimal("7920.79"), confirmed.getConfirmedShares().value());

        // 持仓入账
        var position = sharePositionRepository.findByCustomerIdAndProductId("C001", "P001").orElseThrow();
        assertEquals(new BigDecimal("7920.79"), position.getTotalShares().value());
        assertEquals(new BigDecimal("7920.79"), position.availableShares().value());

        // 资金：扣冻结款，余额 20000 - 10000 = 10000，冻结归零
        assertEquals(new BigDecimal("10000.00"), fundsAccessor.balance("C001"));
        assertEquals(new BigDecimal("0.00"), fundsAccessor.frozen("C001"));
    }

    @Test
    @DisplayName("GIVEN 待确认申购单但 T 日净值未发布 WHEN 运行确认 THEN 订单跳过保持待确认")
    void test_净值未发布_确认跳过() {
        SubscriptionOrder order = subscriptionAppService.place("C001", "P001", "10000.00");

        ConfirmationAppService.ConfirmationSummary summary =
                confirmationAppService.runConfirmations(order.getTDay().date());

        assertEquals(0, summary.subscriptionConfirmed());
        assertEquals(SubscriptionStatus.FUNDS_FROZEN,
                subscriptionOrderRepository.findById(order.getOrderId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("GIVEN 周五 15:30 已截单 WHEN 下单 THEN T 日顺延至下周一（S-02）")
    void test_截单顺延() {
        timeProvider.setFixed(LocalDateTime.of(2026, 9, 25, 15, 30));

        SubscriptionOrder order = subscriptionAppService.place("C001", "P001", "10000.00");

        assertEquals(LocalDate.of(2026, 9, 28), order.getTDay().date());
        assertTrue(order.getTDay().date().getDayOfWeek().getValue() == 1, "T 日应为周一");
    }
}
