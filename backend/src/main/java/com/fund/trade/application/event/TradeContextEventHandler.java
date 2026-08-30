package com.fund.trade.application.event;

import com.fund.trade.domain.event.DomainEvents;
import com.fund.trade.domain.model.order.RedemptionOrder;
import com.fund.trade.domain.model.order.SubscriptionOrder;
import com.fund.trade.domain.repository.OrderTraceRepository;
import com.fund.trade.domain.repository.RedemptionOrderRepository;
import com.fund.trade.domain.repository.SubscriptionOrderRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 交易上下文事件处理器：订阅资金/TA 账户上下文事件，回写订单状态（含状态轨迹记录）。
 */
@Component
public class TradeContextEventHandler {

    private final SubscriptionOrderRepository subscriptionOrderRepository;
    private final RedemptionOrderRepository redemptionOrderRepository;
    private final OrderTraceRepository orderTraceRepository;

    public TradeContextEventHandler(SubscriptionOrderRepository subscriptionOrderRepository,
                                    RedemptionOrderRepository redemptionOrderRepository,
                                    OrderTraceRepository orderTraceRepository) {
        this.subscriptionOrderRepository = subscriptionOrderRepository;
        this.redemptionOrderRepository = redemptionOrderRepository;
        this.orderTraceRepository = orderTraceRepository;
    }

    /** 订阅「资金已冻结」→ 申购单 CREATED → FUNDS_FROZEN */
    @EventListener
    public void onFundsFrozen(DomainEvents.FundsFrozen event) {
        SubscriptionOrder order = subscriptionOrderRepository.findById(event.orderId()).orElseThrow();
        order.markFundsFrozen();
        subscriptionOrderRepository.save(order);
        orderTraceRepository.record(order.getOrderId(), "CREATED", "FUNDS_FROZEN", "FundsFrozen");
    }

    /** 订阅「资金冻结失败」→ 申购单 CREATED → CLOSED */
    @EventListener
    public void onFundsFreezeFailed(DomainEvents.FundsFreezeFailed event) {
        SubscriptionOrder order = subscriptionOrderRepository.findById(event.orderId()).orElseThrow();
        order.markFundsFreezeFailed(event.reason());
        subscriptionOrderRepository.save(order);
        orderTraceRepository.record(order.getOrderId(), "CREATED", "CLOSED", "FundsFreezeFailed");
    }

    /** 订阅「份额已冻结」→ 赎回单 CREATED → SHARES_FROZEN */
    @EventListener
    public void onSharesFrozen(DomainEvents.SharesFrozen event) {
        RedemptionOrder order = redemptionOrderRepository.findById(event.orderId()).orElseThrow();
        order.markSharesFrozen();
        redemptionOrderRepository.save(order);
        orderTraceRepository.record(order.getOrderId(), "CREATED", "SHARES_FROZEN", "SharesFrozen");
    }

    /** 订阅「份额冻结失败」→ 赎回单 CREATED → CLOSED */
    @EventListener
    public void onSharesFreezeFailed(DomainEvents.SharesFreezeFailed event) {
        RedemptionOrder order = redemptionOrderRepository.findById(event.orderId()).orElseThrow();
        order.markSharesFreezeFailed(event.reason());
        redemptionOrderRepository.save(order);
        orderTraceRepository.record(order.getOrderId(), "CREATED", "CLOSED", "SharesFreezeFailed");
    }
}
