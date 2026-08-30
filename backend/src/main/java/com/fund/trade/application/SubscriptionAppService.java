package com.fund.trade.application;

import com.fund.trade.domain.event.DomainEventPublisher;
import com.fund.trade.domain.event.DomainEvents;
import com.fund.trade.domain.model.order.SubscriptionOrder;
import com.fund.trade.domain.model.order.SubscriptionStatus;
import com.fund.trade.domain.model.product.FundProduct;
import com.fund.trade.domain.repository.FundProductRepository;
import com.fund.trade.domain.repository.HolidayRepository;
import com.fund.trade.domain.repository.OrderTraceRepository;
import com.fund.trade.domain.repository.SubscriptionOrderRepository;
import com.fund.trade.domain.service.TradeCalendar;
import com.fund.trade.domain.valueobject.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 申购应用服务：下单流程编排（不包含核心业务规则）。
 * 流程：产品校验（领域层）→ T 日解析（领域服务）→ 落库 → 发布「申购单已下单」事件
 * → 资金上下文冻结资金 → 交易上下文回写订单状态（全部通过领域事件协作）。
 */
@Service
public class SubscriptionAppService {

    private final FundProductRepository fundProductRepository;
    private final SubscriptionOrderRepository subscriptionOrderRepository;
    private final HolidayRepository holidayRepository;
    private final OrderTraceRepository orderTraceRepository;
    private final DomainEventPublisher publisher;
    private final TradeTimeProvider timeProvider;

    public SubscriptionAppService(FundProductRepository fundProductRepository,
                                  SubscriptionOrderRepository subscriptionOrderRepository,
                                  HolidayRepository holidayRepository,
                                  OrderTraceRepository orderTraceRepository,
                                  DomainEventPublisher publisher,
                                  TradeTimeProvider timeProvider) {
        this.fundProductRepository = fundProductRepository;
        this.subscriptionOrderRepository = subscriptionOrderRepository;
        this.holidayRepository = holidayRepository;
        this.orderTraceRepository = orderTraceRepository;
        this.publisher = publisher;
        this.timeProvider = timeProvider;
    }

    /**
     * 申购下单。
     * 资金冻结失败（资金不足等）时订单落库为 CLOSED 并抛出业务异常（不回滚，保留失败单据）。
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public SubscriptionOrder place(String customerId, String productId, String subscriptionAmount) {
        requireNonBlank(customerId, "客户ID不能为空");
        requireNonBlank(productId, "产品ID不能为空");
        requireNonBlank(subscriptionAmount, "申购金额不能为空");

        FundProduct product = fundProductRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        TradeCalendar calendar = TradeCalendar.of(holidayRepository.findAll());
        SubscriptionOrder order;
        try {
            order = SubscriptionOrder.place(customerId, product,
                    Money.of(subscriptionAmount), calendar.resolveTDay(timeProvider.now()));
        } catch (IllegalStateException e) {
            // 产品已暂停申购
            throw new BusinessException(ErrorCode.PRODUCT_SUSPENDED, e.getMessage());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "申购金额格式不合法");
        } catch (IllegalArgumentException e) {
            // 金额非法或低于起购金额
            throw new BusinessException(ErrorCode.BELOW_MIN_SUBSCRIPTION, e.getMessage());
        }

        // 订单落库（CREATED）并记录初始状态轨迹
        subscriptionOrderRepository.save(order);
        orderTraceRepository.record(order.getOrderId(), null, "CREATED", "OrderPlaced");

        // 发布领域事件：资金上下文冻结资金 → 交易上下文回写状态（同步分发）
        publisher.publish(new DomainEvents.SubscriptionOrderPlaced(
                order.getOrderId(), customerId, productId,
                order.getSubscriptionAmount(), order.getTDay()));

        // 重新加载事件处理后的订单终态
        SubscriptionOrder latest = subscriptionOrderRepository.findById(order.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (latest.getStatus() == SubscriptionStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS, latest.getFailReason());
        }
        return latest;
    }

    private void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
    }
}
