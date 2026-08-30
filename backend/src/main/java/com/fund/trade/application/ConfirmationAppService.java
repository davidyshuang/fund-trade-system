package com.fund.trade.application;

import com.fund.trade.domain.event.DomainEventPublisher;
import com.fund.trade.domain.event.DomainEvents;
import com.fund.trade.domain.model.order.RedemptionOrder;
import com.fund.trade.domain.model.order.RedemptionStatus;
import com.fund.trade.domain.model.order.SubscriptionOrder;
import com.fund.trade.domain.model.order.SubscriptionStatus;
import com.fund.trade.domain.model.product.FundProduct;
import com.fund.trade.domain.repository.FundProductRepository;
import com.fund.trade.domain.repository.NetValueRepository;
import com.fund.trade.domain.repository.OrderTraceRepository;
import com.fund.trade.domain.repository.RedemptionOrderRepository;
import com.fund.trade.domain.repository.SharePositionRepository;
import com.fund.trade.domain.repository.SubscriptionOrderRepository;
import com.fund.trade.domain.valueobject.NetValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * T+1 确认应用服务：净值发布与确认批处理编排。
 * 批处理规则：
 * 1. 仅处理 T 日匹配且处于待确认状态（FUNDS_FROZEN / SHARES_FROZEN）的订单；
 * 2. T 日净值未发布的订单跳过，保持待确认；
 * 3. 确认成功 → 发布确认事件（资金扣款/份额入账/持仓扣减/赎回款入账由各上下文事件处理器完成）；
 * 4. 确认失败 → 订单标记 CONFIRM_FAILED 并发布确认失败事件（资金/份额解冻退回）。
 */
@Service
public class ConfirmationAppService {

    private final FundProductRepository fundProductRepository;
    private final SubscriptionOrderRepository subscriptionOrderRepository;
    private final RedemptionOrderRepository redemptionOrderRepository;
    private final NetValueRepository netValueRepository;
    private final SharePositionRepository sharePositionRepository;
    private final OrderTraceRepository orderTraceRepository;
    private final DomainEventPublisher publisher;

    public ConfirmationAppService(FundProductRepository fundProductRepository,
                                  SubscriptionOrderRepository subscriptionOrderRepository,
                                  RedemptionOrderRepository redemptionOrderRepository,
                                  NetValueRepository netValueRepository,
                                  SharePositionRepository sharePositionRepository,
                                  OrderTraceRepository orderTraceRepository,
                                  DomainEventPublisher publisher) {
        this.fundProductRepository = fundProductRepository;
        this.subscriptionOrderRepository = subscriptionOrderRepository;
        this.redemptionOrderRepository = redemptionOrderRepository;
        this.netValueRepository = netValueRepository;
        this.sharePositionRepository = sharePositionRepository;
        this.orderTraceRepository = orderTraceRepository;
        this.publisher = publisher;
    }

    /** 管理端发布某产品某日净值（估值上下文） */
    @Transactional
    public void publishNetValue(String productId, LocalDate navDate, String nav) {
        if (productId == null || productId.isBlank() || navDate == null || nav == null || nav.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "净值发布参数不完整");
        }
        NetValue netValue;
        try {
            netValue = NetValue.of(productId, navDate, nav);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "净值参数不合法：" + e.getMessage());
        }
        netValueRepository.save(netValue);
        publisher.publish(new DomainEvents.NetValuePublished(productId, navDate, netValue.value()));
    }

    /**
     * 运行 T+1 确认批处理：处理指定 T 日的全部待确认申购单与赎回单。
     */
    @Transactional
    public ConfirmationSummary runConfirmations(LocalDate tDay) {
        if (tDay == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "确认日期不能为空");
        }
        int subConfirmed = confirmSubscriptions(tDay);
        int[] redemptionResult = confirmRedemptions(tDay);
        return new ConfirmationSummary(subConfirmed,
                countFailedSubscriptions(tDay),
                redemptionResult[0], redemptionResult[1]);
    }

    /** 申购单确认：成功返回确认数量（失败即时处理，不走二次统计） */
    private int confirmSubscriptions(LocalDate tDay) {
        int confirmed = 0;
        for (SubscriptionOrder order : subscriptionOrderRepository
                .findByStatusAndTDay(SubscriptionStatus.FUNDS_FROZEN, tDay)) {
            Optional<NetValue> navOpt = netValueRepository
                    .findByProductAndDate(order.getProductId(), tDay);
            if (navOpt.isEmpty()) {
                // 净值未发布：跳过，保持待确认
                continue;
            }
            FundProduct product = fundProductRepository.findById(order.getProductId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
            try {
                order.confirm(navOpt.get(), product);
            } catch (RuntimeException e) {
                order.fail(e.getMessage());
                subscriptionOrderRepository.save(order);
                orderTraceRepository.record(order.getOrderId(),
                        "FUNDS_FROZEN", "CONFIRM_FAILED", "SubscriptionConfirmationFailed");
                publisher.publish(new DomainEvents.SubscriptionConfirmationFailed(
                        order.getOrderId(), order.getCustomerId(),
                        order.getSubscriptionAmount(), e.getMessage()));
                continue;
            }
            subscriptionOrderRepository.save(order);
            orderTraceRepository.record(order.getOrderId(),
                    "FUNDS_FROZEN", "CONFIRMED", "SubscriptionConfirmed");
            publisher.publish(new DomainEvents.SubscriptionConfirmed(
                    order.getOrderId(), order.getCustomerId(), order.getProductId(),
                    order.getSubscriptionAmount(), order.getConfirmedShares(),
                    order.getConfirmedNetValue(), order.getConfirmedFee(), tDay));
            confirmed++;
        }
        return confirmed;
    }

    /** 统计指定 T 日确认失败的申购单数量（供批次汇总） */
    private int countFailedSubscriptions(LocalDate tDay) {
        return subscriptionOrderRepository
                .findByStatusAndTDay(SubscriptionStatus.CONFIRM_FAILED, tDay).size();
    }

    /** 赎回单确认：返回 [确认数, 失败数] */
    private int[] confirmRedemptions(LocalDate tDay) {
        int confirmed = 0;
        int failed = 0;
        for (RedemptionOrder order : redemptionOrderRepository
                .findByStatusAndTDay(RedemptionStatus.SHARES_FROZEN, tDay)) {
            Optional<NetValue> navOpt = netValueRepository
                    .findByProductAndDate(order.getProductId(), tDay);
            if (navOpt.isEmpty()) {
                // 净值未发布：跳过，保持待确认
                continue;
            }
            FundProduct product = fundProductRepository.findById(order.getProductId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
            // 持有天数 = 最近入账日 → T 日（赎回费率梯度计算基准）
            int holdingDays = sharePositionRepository
                    .findLastCreditDate(order.getCustomerId(), order.getProductId())
                    .map(d -> (int) ChronoUnit.DAYS.between(d, tDay))
                    .orElse(0);
            try {
                order.confirm(navOpt.get(), product, holdingDays);
            } catch (RuntimeException e) {
                order.fail(e.getMessage());
                redemptionOrderRepository.save(order);
                orderTraceRepository.record(order.getOrderId(),
                        "SHARES_FROZEN", "CONFIRM_FAILED", "RedemptionConfirmationFailed");
                publisher.publish(new DomainEvents.RedemptionConfirmationFailed(
                        order.getOrderId(), order.getCustomerId(), order.getProductId(),
                        order.getRedemptionShares(), e.getMessage()));
                failed++;
                continue;
            }
            redemptionOrderRepository.save(order);
            orderTraceRepository.record(order.getOrderId(),
                    "SHARES_FROZEN", "CONFIRMED", "RedemptionConfirmed");
            publisher.publish(new DomainEvents.RedemptionConfirmed(
                    order.getOrderId(), order.getCustomerId(), order.getProductId(),
                    order.getRedemptionShares(), order.getConfirmedNetValue(),
                    order.getRedemptionAmount(), order.getRedemptionFee()));
            confirmed++;
        }
        return new int[]{confirmed, failed};
    }

    /** 批处理汇总结果 */
    public record ConfirmationSummary(int subscriptionConfirmed, int subscriptionFailed,
                                       int redemptionConfirmed, int redemptionFailed) {
    }
}
