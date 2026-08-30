package com.fund.trade.application;

import com.fund.trade.domain.event.DomainEventPublisher;
import com.fund.trade.domain.event.DomainEvents;
import com.fund.trade.domain.model.order.RedemptionOrder;
import com.fund.trade.domain.model.order.RedemptionStatus;
import com.fund.trade.domain.model.product.FundProduct;
import com.fund.trade.domain.repository.FundProductRepository;
import com.fund.trade.domain.repository.HolidayRepository;
import com.fund.trade.domain.repository.OrderTraceRepository;
import com.fund.trade.domain.repository.RedemptionOrderRepository;
import com.fund.trade.domain.service.TradeCalendar;
import com.fund.trade.domain.valueobject.Share;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 赎回应用服务：下单流程编排（不包含核心业务规则）。
 * 流程：产品校验（领域层）→ T 日解析（领域服务）→ 落库 → 发布「赎回单已下单」事件
 * → TA 账户上下文冻结份额 → 交易上下文回写订单状态（全部通过领域事件协作）。
 */
@Service
public class RedemptionAppService {

    private final FundProductRepository fundProductRepository;
    private final RedemptionOrderRepository redemptionOrderRepository;
    private final HolidayRepository holidayRepository;
    private final OrderTraceRepository orderTraceRepository;
    private final DomainEventPublisher publisher;
    private final TradeTimeProvider timeProvider;

    public RedemptionAppService(FundProductRepository fundProductRepository,
                                RedemptionOrderRepository redemptionOrderRepository,
                                HolidayRepository holidayRepository,
                                OrderTraceRepository orderTraceRepository,
                                DomainEventPublisher publisher,
                                TradeTimeProvider timeProvider) {
        this.fundProductRepository = fundProductRepository;
        this.redemptionOrderRepository = redemptionOrderRepository;
        this.holidayRepository = holidayRepository;
        this.orderTraceRepository = orderTraceRepository;
        this.publisher = publisher;
        this.timeProvider = timeProvider;
    }

    /**
     * 赎回下单。
     * 份额冻结失败（可用份额不足等）时订单落库为 CLOSED 并抛出业务异常（不回滚，保留失败单据）。
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public RedemptionOrder place(String customerId, String productId, String redemptionShares) {
        requireNonBlank(customerId, "客户ID不能为空");
        requireNonBlank(productId, "产品ID不能为空");
        requireNonBlank(redemptionShares, "赎回份额不能为空");

        FundProduct product = fundProductRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        TradeCalendar calendar = TradeCalendar.of(holidayRepository.findAll());
        RedemptionOrder order;
        try {
            order = RedemptionOrder.place(customerId, product,
                    Share.of(redemptionShares), calendar.resolveTDay(timeProvider.now()));
        } catch (IllegalStateException e) {
            // 产品已暂停赎回
            throw new BusinessException(ErrorCode.PRODUCT_SUSPENDED_REDEMPTION, e.getMessage());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "赎回份额格式不合法");
        } catch (IllegalArgumentException e) {
            // 份额非法（必须大于 0）
            throw new BusinessException(ErrorCode.PARAM_INVALID, e.getMessage());
        }

        // 订单落库（CREATED）并记录初始状态轨迹
        redemptionOrderRepository.save(order);
        orderTraceRepository.record(order.getOrderId(), null, "CREATED", "OrderPlaced");

        // 发布领域事件：TA 账户上下文冻结份额 → 交易上下文回写状态（同步分发）
        publisher.publish(new DomainEvents.RedemptionOrderPlaced(
                order.getOrderId(), customerId, productId,
                order.getRedemptionShares(), order.getTDay()));

        // 重新加载事件处理后的订单终态
        RedemptionOrder latest = redemptionOrderRepository.findById(order.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (latest.getStatus() == RedemptionStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_SHARES, latest.getFailReason());
        }
        return latest;
    }

    private void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
    }
}
