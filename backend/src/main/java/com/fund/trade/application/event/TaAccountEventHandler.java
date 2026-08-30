package com.fund.trade.application.event;

import com.fund.trade.domain.event.DomainEventPublisher;
import com.fund.trade.domain.event.DomainEvents;
import com.fund.trade.domain.model.position.SharePosition;
import com.fund.trade.domain.repository.SharePositionRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * TA 账户上下文事件处理器：订阅交易上下文事件，执行份额冻结/入账/扣减。
 */
@Component
public class TaAccountEventHandler {

    private final SharePositionRepository sharePositionRepository;
    private final DomainEventPublisher publisher;

    public TaAccountEventHandler(SharePositionRepository sharePositionRepository,
                                 DomainEventPublisher publisher) {
        this.sharePositionRepository = sharePositionRepository;
        this.publisher = publisher;
    }

    /** 订阅「赎回单已下单」→ 冻结赎回份额（成功→SharesFrozen；失败→SharesFreezeFailed） */
    @EventListener
    public void onRedemptionOrderPlaced(DomainEvents.RedemptionOrderPlaced event) {
        SharePosition position = sharePositionRepository
                .findByCustomerIdAndProductId(event.customerId(), event.productId()).orElse(null);
        if (position == null) {
            publisher.publish(new DomainEvents.SharesFreezeFailed(
                    event.orderId(), event.customerId(), "无持仓记录"));
            return;
        }
        try {
            position.freeze(event.redemptionShares());
            sharePositionRepository.save(position);
            publisher.publish(new DomainEvents.SharesFrozen(
                    event.orderId(), event.customerId(), event.redemptionShares()));
        } catch (Exception e) {
            publisher.publish(new DomainEvents.SharesFreezeFailed(
                    event.orderId(), event.customerId(), e.getMessage()));
        }
    }

    /** 订阅「申购已确认」→ 持仓份额入账 + 更新最近入账日（赎回持有天数计算基准） */
    @EventListener
    public void onSubscriptionConfirmed(DomainEvents.SubscriptionConfirmed event) {
        SharePosition position = sharePositionRepository
                .findByCustomerIdAndProductId(event.customerId(), event.productId())
                .orElseGet(() -> new SharePosition(
                        "TA-" + event.customerId() + "-" + event.productId(),
                        event.customerId(), event.productId()));
        position.increase(event.confirmedShares());
        sharePositionRepository.save(position);
        sharePositionRepository.updateLastCreditDate(
                event.customerId(), event.productId(), event.tDay());
    }

    /** 订阅「赎回已确认」→ 扣减总份额并释放冻结份额 */
    @EventListener
    public void onRedemptionConfirmed(DomainEvents.RedemptionConfirmed event) {
        SharePosition position = sharePositionRepository
                .findByCustomerIdAndProductId(event.customerId(), event.productId()).orElseThrow();
        position.decreaseAndUnfreeze(event.redemptionShares());
        sharePositionRepository.save(position);
    }

    /** 订阅「赎回确认失败」→ 解冻份额恢复可用 */
    @EventListener
    public void onRedemptionConfirmationFailed(DomainEvents.RedemptionConfirmationFailed event) {
        SharePosition position = sharePositionRepository
                .findByCustomerIdAndProductId(event.customerId(), event.productId()).orElseThrow();
        position.unfreeze(event.redemptionShares());
        sharePositionRepository.save(position);
    }
}
