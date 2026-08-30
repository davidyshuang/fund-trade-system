package com.fund.trade.domain.model.order;

import com.fund.trade.domain.model.product.FundProduct;
import com.fund.trade.domain.valueobject.Money;
import com.fund.trade.domain.valueobject.NetValue;
import com.fund.trade.domain.valueobject.Share;
import com.fund.trade.domain.valueobject.TradeDate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * 申购单聚合根（交易上下文）。
 * <p>核心不变量：</p>
 * <ul>
 *   <li>状态只能沿状态机单向流转（CREATED → FUNDS_FROZEN → CONFIRMED / CONFIRM_FAILED）；</li>
 *   <li>T+1 确认必须在资金冻结成功（FUNDS_FROZEN）后执行；</li>
 *   <li>确认份额 / 确认费用仅在确认成功时一次性写入。</li>
 * </ul>
 */
public class SubscriptionOrder {

    private final String orderId;
    private final String customerId;
    private final String productId;
    private final Money subscriptionAmount;
    private final TradeDate tDay;
    private SubscriptionStatus status;
    private NetValue confirmedNetValue;
    private Share confirmedShares;
    private Money confirmedFee;
    private String failReason;

    private SubscriptionOrder(String orderId, String customerId, String productId,
                              Money subscriptionAmount, TradeDate tDay) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.productId = productId;
        this.subscriptionAmount = subscriptionAmount;
        this.tDay = tDay;
        this.status = SubscriptionStatus.CREATED;
    }

    /**
     * 下单工厂方法：执行产品在售与起购金额校验后创建申购单（初始状态 CREATED）。
     */
    public static SubscriptionOrder place(String customerId, FundProduct product,
                                          Money subscriptionAmount, TradeDate tDay) {
        Objects.requireNonNull(customerId, "客户ID不能为空");
        Objects.requireNonNull(product, "基金产品不能为空");
        Objects.requireNonNull(tDay, "T日不能为空");
        // S-05 / S-08：产品在售、金额 ≥ 起购金额校验收敛在产品聚合内
        product.validateSubscription(subscriptionAmount);
        return new SubscriptionOrder("SUB-" + UUID.randomUUID(), customerId,
                product.getProductId(), subscriptionAmount, tDay);
    }

    /**
     * 仓储重建工厂：按持久化数据恢复申购单聚合（含历史状态与确认结果）。
     */
    public static SubscriptionOrder restore(String orderId, String customerId, String productId,
                                             Money subscriptionAmount, TradeDate tDay,
                                             SubscriptionStatus status, NetValue confirmedNetValue,
                                             Share confirmedShares, Money confirmedFee, String failReason) {
        SubscriptionOrder order = new SubscriptionOrder(orderId, customerId, productId,
                subscriptionAmount, tDay);
        order.status = status;
        order.confirmedNetValue = confirmedNetValue;
        order.confirmedShares = confirmedShares;
        order.confirmedFee = confirmedFee;
        order.failReason = failReason;
        return order;
    }

    /** 资金冻结成功：CREATED → FUNDS_FROZEN */
    public void markFundsFrozen() {
        requireStatus(SubscriptionStatus.CREATED, "资金冻结成功流转仅允许在已创建状态下执行");
        this.status = SubscriptionStatus.FUNDS_FROZEN;
    }

    /** 资金冻结失败：CREATED → CLOSED（订单关闭） */
    public void markFundsFreezeFailed(String reason) {
        requireStatus(SubscriptionStatus.CREATED, "资金冻结失败流转仅允许在已创建状态下执行");
        this.status = SubscriptionStatus.CLOSED;
        this.failReason = reason;
    }

    /**
     * T+1 确认（外扣法）：
     * 净申购金额 = 申购金额 ÷ (1 + 申购费率)；
     * 确认份额 = 净申购金额 ÷ T 日净值。
     */
    public void confirm(NetValue netValue, FundProduct product) {
        requireStatus(SubscriptionStatus.FUNDS_FROZEN, "确认仅允许在资金已冻结（待确认）状态下执行");
        Objects.requireNonNull(netValue, "净值不能为空");
        Objects.requireNonNull(product, "基金产品不能为空");
        if (!netValue.productId().equals(this.productId)) {
            throw new IllegalArgumentException("净值所属产品与订单产品不一致");
        }
        // 净申购金额（保留 2 位小数）
        Money netAmount = subscriptionAmount.divide(
                BigDecimal.ONE.add(product.getSubscriptionFeeRate().value()));
        this.confirmedFee = subscriptionAmount.subtract(netAmount);
        this.confirmedShares = Share.of(
                netAmount.value().divide(netValue.value(), 2, RoundingMode.HALF_UP));
        this.confirmedNetValue = netValue;
        this.status = SubscriptionStatus.CONFIRMED;
    }

    /** 确认失败：FUNDS_FROZEN → CONFIRM_FAILED（触发资金解冻退回） */
    public void fail(String reason) {
        requireStatus(SubscriptionStatus.FUNDS_FROZEN, "确认失败流转仅允许在待确认状态下执行");
        this.status = SubscriptionStatus.CONFIRM_FAILED;
        this.failReason = reason;
    }

    private void requireStatus(SubscriptionStatus expected, String message) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    message + "，当前状态：" + this.status + "，期望状态：" + expected);
        }
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductId() {
        return productId;
    }

    public Money getSubscriptionAmount() {
        return subscriptionAmount;
    }

    public TradeDate getTDay() {
        return tDay;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public NetValue getConfirmedNetValue() {
        return confirmedNetValue;
    }

    public Share getConfirmedShares() {
        return confirmedShares;
    }

    public Money getConfirmedFee() {
        return confirmedFee;
    }

    public String getFailReason() {
        return failReason;
    }
}
