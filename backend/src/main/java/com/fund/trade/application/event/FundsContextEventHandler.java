package com.fund.trade.application.event;

import com.fund.trade.domain.event.DomainEventPublisher;
import com.fund.trade.domain.event.DomainEvents;
import com.fund.trade.domain.model.funds.FundsAccount;
import com.fund.trade.domain.repository.FundsAccountRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 资金上下文事件处理器：订阅交易上下文事件，执行资金冻结/扣款/入账，并发布资金侧领域事件。
 */
@Component
public class FundsContextEventHandler {

    private final FundsAccountRepository fundsAccountRepository;
    private final DomainEventPublisher publisher;

    public FundsContextEventHandler(FundsAccountRepository fundsAccountRepository,
                                    DomainEventPublisher publisher) {
        this.fundsAccountRepository = fundsAccountRepository;
        this.publisher = publisher;
    }

    /** 订阅「申购单已下单」→ 冻结申购资金（成功→FundsFrozen；失败→FundsFreezeFailed） */
    @EventListener
    public void onSubscriptionOrderPlaced(DomainEvents.SubscriptionOrderPlaced event) {
        FundsAccount account = fundsAccountRepository.findByCustomerId(event.customerId()).orElse(null);
        if (account == null) {
            publisher.publish(new DomainEvents.FundsFreezeFailed(
                    event.orderId(), event.customerId(), "资金账户不存在"));
            return;
        }
        try {
            account.freeze(event.subscriptionAmount());
            fundsAccountRepository.save(account);
            publisher.publish(new DomainEvents.FundsFrozen(
                    event.orderId(), event.customerId(),
                    "FRZ-" + UUID.randomUUID(), event.subscriptionAmount()));
        } catch (Exception e) {
            publisher.publish(new DomainEvents.FundsFreezeFailed(
                    event.orderId(), event.customerId(), e.getMessage()));
        }
    }

    /** 订阅「申购已确认」→ 扣除冻结申购款（实际扣款） */
    @EventListener
    public void onSubscriptionConfirmed(DomainEvents.SubscriptionConfirmed event) {
        FundsAccount account = fundsAccountRepository.findByCustomerId(event.customerId()).orElseThrow();
        account.deductFrozen(event.subscriptionAmount());
        fundsAccountRepository.save(account);
    }

    /** 订阅「申购确认失败」→ 解冻资金退回可用 */
    @EventListener
    public void onSubscriptionConfirmationFailed(DomainEvents.SubscriptionConfirmationFailed event) {
        FundsAccount account = fundsAccountRepository.findByCustomerId(event.customerId()).orElseThrow();
        account.unfreeze(event.subscriptionAmount());
        fundsAccountRepository.save(account);
    }

    /** 订阅「赎回已确认」→ 赎回款入账 */
    @EventListener
    public void onRedemptionConfirmed(DomainEvents.RedemptionConfirmed event) {
        FundsAccount account = fundsAccountRepository.findByCustomerId(event.customerId()).orElseThrow();
        account.credit(event.redemptionAmount());
        fundsAccountRepository.save(account);
    }
}
