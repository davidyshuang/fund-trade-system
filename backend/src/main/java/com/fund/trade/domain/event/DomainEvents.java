package com.fund.trade.domain.event;

import com.fund.trade.domain.valueobject.Money;
import com.fund.trade.domain.valueobject.NetValue;
import com.fund.trade.domain.valueobject.Share;
import com.fund.trade.domain.valueobject.TradeDate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 领域事件清单（共 12 个，对应设计文档第七章）。
 * 跨上下文协作一律通过领域事件异步解耦，禁止上下文之间直接调用对方领域模型。
 */
public final class DomainEvents {

    private DomainEvents() {
    }

    /** 1. 申购单已下单（发布方：交易 → 订阅方：资金） */
    public record SubscriptionOrderPlaced(String orderId, String customerId, String productId,
                                          Money subscriptionAmount, TradeDate tDay) implements DomainEvent {
    }

    /** 2. 资金已冻结（发布方：资金 → 订阅方：交易） */
    public record FundsFrozen(String orderId, String customerId, String freezeFlowNo,
                              Money frozenAmount) implements DomainEvent {
    }

    /** 3. 资金冻结失败（发布方：资金 → 订阅方：交易） */
    public record FundsFreezeFailed(String orderId, String customerId, String reason) implements DomainEvent {
    }

    /** 4. 赎回单已下单（发布方：交易 → 订阅方：TA 账户） */
    public record RedemptionOrderPlaced(String orderId, String customerId, String productId,
                                        Share redemptionShares, TradeDate tDay) implements DomainEvent {
    }

    /** 5. 份额已冻结（发布方：TA 账户 → 订阅方：交易） */
    public record SharesFrozen(String orderId, String customerId, Share frozenShares) implements DomainEvent {
    }

    /** 6. 份额冻结失败（发布方：TA 账户 → 订阅方：交易） */
    public record SharesFreezeFailed(String orderId, String customerId, String reason) implements DomainEvent {
    }

    /** 7. 净值已发布（发布方：估值 → 订阅方：交易批处理） */
    public record NetValuePublished(String productId, LocalDate navDate, BigDecimal nav) implements DomainEvent {
    }

    /** 8. 申购已确认（发布方：交易 → 订阅方：TA 账户、资金） */
    public record SubscriptionConfirmed(String orderId, String customerId, String productId,
                                        Money subscriptionAmount, Share confirmedShares,
                                        NetValue confirmedNetValue, Money confirmedFee,
                                        LocalDate tDay) implements DomainEvent {
    }

    /** 9. 申购确认失败（发布方：交易 → 订阅方：资金） */
    public record SubscriptionConfirmationFailed(String orderId, String customerId,
                                                 Money subscriptionAmount, String reason) implements DomainEvent {
    }

    /** 10. 赎回已确认（发布方：交易 → 订阅方：TA 账户、资金） */
    public record RedemptionConfirmed(String orderId, String customerId, String productId,
                                      Share redemptionShares, NetValue confirmedNetValue,
                                      Money redemptionAmount, Money redemptionFee) implements DomainEvent {
    }

    /** 11. 赎回确认失败（发布方：交易 → 订阅方：TA 账户） */
    public record RedemptionConfirmationFailed(String orderId, String customerId, String productId,
                                               Share redemptionShares, String reason) implements DomainEvent {
    }

    /** 12. 产品已暂停申购（发布方：产品 → 订阅方：交易前置校验） */
    public record ProductSuspended(String productId, LocalDateTime effectiveTime) implements DomainEvent {
    }
}
