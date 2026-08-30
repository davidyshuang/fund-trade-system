package com.fund.trade.integration;

import com.fund.trade.application.BusinessException;
import com.fund.trade.application.ConfirmationAppService;
import com.fund.trade.application.RedemptionAppService;
import com.fund.trade.domain.model.order.RedemptionOrder;
import com.fund.trade.domain.model.order.RedemptionStatus;
import com.fund.trade.domain.model.position.SharePosition;
import com.fund.trade.domain.repository.FundProductRepository;
import com.fund.trade.domain.repository.RedemptionOrderRepository;
import com.fund.trade.domain.repository.SharePositionRepository;
import com.fund.trade.domain.valueobject.Share;
import com.fund.trade.testsupport.FundsAccountRepositoryAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 赎回全流程集成测试（PostgreSQL 测试库 + 事件驱动编排）。
 * 对应设计文档场景：S-12 ~ S-16。
 */
@DisplayName("赎回流程集成测试")
class RedemptionFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private RedemptionAppService redemptionAppService;
    @Autowired
    private ConfirmationAppService confirmationAppService;
    @Autowired
    private RedemptionOrderRepository redemptionOrderRepository;
    @Autowired
    private SharePositionRepository sharePositionRepository;
    @Autowired
    private FundProductRepository fundProductRepository;
    @Autowired
    private FundsAccountRepositoryAccessor fundsAccessor;

    @BeforeEach
    void seedPosition() {
        // 持仓 7920.79 份，最近入账日设为 400 天前（命中 0.25% 赎回费率档）
        SharePosition position = new SharePosition("TA-C001-P001", "C001", "P001",
                Share.of(new BigDecimal("7920.79")), Share.zero());
        sharePositionRepository.save(position);
        sharePositionRepository.updateLastCreditDate("C001", "P001",
                LocalDate.of(2026, 9, 24).minusDays(400));
    }

    @Test
    @DisplayName("GIVEN 持仓充足 WHEN 赎回 5000 份 THEN 份额冻结订单进入待确认")
    void test_赎回下单_份额冻结成功() {
        RedemptionOrder order = redemptionAppService.place("C001", "P001", "5000.00");

        assertEquals(RedemptionStatus.SHARES_FROZEN, order.getStatus());
        SharePosition position = sharePositionRepository
                .findByCustomerIdAndProductId("C001", "P001").orElseThrow();
        assertEquals(new BigDecimal("7920.79"), position.getTotalShares().value());
        assertEquals(new BigDecimal("2920.79"), position.availableShares().value());
        assertEquals(new BigDecimal("5000.00"), position.getFrozenShares().value());
    }

    @Test
    @DisplayName("GIVEN 可用份额不足 WHEN 赎回 THEN 抛出 40004 异常订单关闭持仓不变")
    void test_赎回下单_可用份额不足() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> redemptionAppService.place("C001", "P001", "9000.00"));

        assertEquals("40004", ex.getCode());
        List<RedemptionOrder> orders = redemptionOrderRepository.findByCustomerId("C001");
        assertEquals(1, orders.size());
        assertEquals(RedemptionStatus.CLOSED, orders.get(0).getStatus());

        SharePosition position = sharePositionRepository
                .findByCustomerIdAndProductId("C001", "P001").orElseThrow();
        assertEquals(new BigDecimal("7920.79"), position.getTotalShares().value());
        assertEquals(new BigDecimal("0.00"), position.getFrozenShares().value());
    }

    @Test
    @DisplayName("GIVEN 待确认赎回单 WHEN 发布净值 1.2500 并确认（持有 400 天）THEN 到账 6234.37 持仓扣减")
    void test_赎回T加1确认_费率梯度() {
        RedemptionOrder order = redemptionAppService.place("C001", "P001", "5000.00");
        LocalDate tDay = order.getTDay().date();

        confirmationAppService.publishNetValue("P001", tDay, "1.2500");
        ConfirmationAppService.ConfirmationSummary summary =
                confirmationAppService.runConfirmations(tDay);

        assertEquals(1, summary.redemptionConfirmed());

        RedemptionOrder confirmed = redemptionOrderRepository.findById(order.getOrderId()).orElseThrow();
        assertEquals(RedemptionStatus.CONFIRMED, confirmed.getStatus());
        // 赎回总额 6250.00，费率 0.25% → 费 15.63，到账 6234.37
        assertEquals(new BigDecimal("15.63"), confirmed.getRedemptionFee().value());
        assertEquals(new BigDecimal("6234.37"), confirmed.getRedemptionAmount().value());

        // 持仓：总份额 2920.79，冻结释放
        SharePosition position = sharePositionRepository
                .findByCustomerIdAndProductId("C001", "P001").orElseThrow();
        assertEquals(new BigDecimal("2920.79"), position.getTotalShares().value());
        assertEquals(new BigDecimal("0.00"), position.getFrozenShares().value());

        // 资金：赎回款入账 20000 + 6234.37
        assertEquals(new BigDecimal("26234.37"), fundsAccessor.balance("C001"));
    }

    @Test
    @DisplayName("GIVEN 产品暂停赎回 WHEN 下单 THEN 抛出 40005 异常")
    void test_暂停赎回产品_下单被拒() {
        var product = fundProductRepository.findById("P001").orElseThrow();
        product.suspend();
        fundProductRepository.save(product);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> redemptionAppService.place("C001", "P001", "1000.00"));
        assertEquals("40005", ex.getCode());
        assertTrue(redemptionOrderRepository.findByCustomerId("C001").isEmpty());
    }
}
