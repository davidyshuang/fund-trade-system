package com.fund.trade.domain.model.order;

import com.fund.trade.domain.model.product.FundProduct;
import com.fund.trade.domain.valueobject.Money;
import com.fund.trade.domain.valueobject.NetValue;
import com.fund.trade.domain.valueobject.Share;
import com.fund.trade.domain.valueobject.TradeDate;

import java.util.Objects;
import java.util.UUID;

/**
 * 赎回单聚合根（交易上下文）。
 * <p>核心不变量：</p>
 * <ul>
 *   <li>状态只能沿状态机单向流转（CREATED → SHARES_FROZEN → CONFIRMED / CONFIRM_FAILED）；</li>
 *   <li>T+1 确认必须在份额冻结成功（SHARES_FROZEN）后执行；</li>
 *   <li>赎回金额 / 赎回费用仅在确认成功时一次性写入。</li>
 * </ul>
 */
public class RedemptionOrder {

    private final String orderId;
    private final String customerId;
    private final String productId;
    private final Share redemptionShares;
    private final TradeDate tDay;
    private RedemptionStatus status;
    private NetValue confirmedNetValue;
    private Money redemptionAmount;
    private Money redemptionFee;
    private String failReason;

    private RedemptionOrder(String orderId, String customerId, String productId,
                            Share redemptionShares, TradeDate tDay) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.productId = productId;
        this.redemptionShares = redemptionShares;
        this.tDay = tDay;
        this.status = RedemptionStatus.CREATED;
    }

    /**
     * 下单工厂方法：执行产品可赎回与份额校验后创建赎回单（初始状态 CREATED）。
     */
    public static RedemptionOrder place(String customerId, FundProduct product,
                                        Share redemptionShares, TradeDate tDay) {
        Objects.requireNonNull(customerId, "客户ID不能为空");
        Objects.requireNonNull(product, "基金产品不能为空");
        Objects.requireNonNull(tDay, "T日不能为空");
        product.validateRedemption(redemptionShares);
        return new RedemptionOrder("RED-" + UUID.randomUUID(), customerId,
                product.getProductId(), redemptionShares, tDay);
    }

    /**
     * 仓储重建工厂：按持久化数据恢复赎回单聚合（含历史状态与确认结果）。
     */
    public static RedemptionOrder restore(String orderId, String customerId, String productId,
                                           Share redemptionShares, TradeDate tDay,
                                           RedemptionStatus status, NetValue confirmedNetValue,
                                           Money redemptionAmount, Money redemptionFee, String failReason) {
        RedemptionOrder order = new RedemptionOrder(orderId, customerId, productId,
                redemptionShares, tDay);
        order.status = status;
        order.confirmedNetValue = confirmedNetValue;
        order.redemptionAmount = redemptionAmount;
        order.redemptionFee = redemptionFee;
        order.failReason = failReason;
        return order;
    }

    /** 份额冻结成功：CREATED → SHARES_FROZEN */
    public void markSharesFrozen() {
        requireStatus(RedemptionStatus.CREATED, "份额冻结成功流转仅允许在已创建状态下执行");
        this.status = RedemptionStatus.SHARES_FROZEN;
    }

    /** 份额冻结失败：CREATED → CLOSED（订单关闭） */
    public void markSharesFreezeFailed(String reason) {
        requireStatus(RedemptionStatus.CREATED, "份额冻结失败流转仅允许在已创建状态下执行");
        this.status = RedemptionStatus.CLOSED;
        this.failReason = reason;
    }

    /**
     * T+1 确认：
     * 赎回总额 = 赎回份额 × T 日净值；
     * 赎回费 = 赎回总额 × 命中梯度费率（按持有天数）；
     * 赎回金额 = 赎回总额 − 赎回费。
     */
    public void confirm(NetValue netValue, FundProduct product, int holdingDays) {
        requireStatus(RedemptionStatus.SHARES_FROZEN, "确认仅允许在份额已冻结（待确认）状态下执行");
        Objects.requireNonNull(netValue, "净值不能为空");
        Objects.requireNonNull(product, "基金产品不能为空");
        if (!netValue.productId().equals(this.productId)) {
            throw new IllegalArgumentException("净值所属产品与订单产品不一致");
        }
        Money grossAmount = Money.of(redemptionShares.value().multiply(netValue.value()));
        this.redemptionFee = product.calcRedemptionFee(grossAmount, holdingDays);
        this.redemptionAmount = grossAmount.subtract(redemptionFee);
        this.confirmedNetValue = netValue;
        this.status = RedemptionStatus.CONFIRMED;
    }

    /** 确认失败：SHARES_FROZEN → CONFIRM_FAILED（触发份额解冻恢复可用） */
    public void fail(String reason) {
        requireStatus(RedemptionStatus.SHARES_FROZEN, "确认失败流转仅允许在待确认状态下执行");
        this.status = RedemptionStatus.CONFIRM_FAILED;
        this.failReason = reason;
    }

    private void requireStatus(RedemptionStatus expected, String message) {
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

    public Share getRedemptionShares() {
        return redemptionShares;
    }

    public TradeDate getTDay() {
        return tDay;
    }

    public RedemptionStatus getStatus() {
        return status;
    }

    public NetValue getConfirmedNetValue() {
        return confirmedNetValue;
    }

    public Money getRedemptionAmount() {
        return redemptionAmount;
    }

    public Money getRedemptionFee() {
        return redemptionFee;
    }

    public String getFailReason() {
        return failReason;
    }
}
