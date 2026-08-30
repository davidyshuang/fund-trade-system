package com.fund.trade.domain.model.product;

import com.fund.trade.domain.valueobject.Money;
import com.fund.trade.domain.valueobject.Rate;
import com.fund.trade.domain.valueobject.Share;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 基金产品聚合根（产品上下文）。
 * <p>核心不变量：</p>
 * <ul>
 *   <li>只有在售状态才可申购 / 赎回；</li>
 *   <li>费率必须在 [0, 1) 区间；起购金额必须为正；</li>
 *   <li>赎回费率规则按持有天数上限升序排列。</li>
 * </ul>
 */
public class FundProduct {

    /** 默认赎回费率梯度：<7 天 1.5%；7~365 天 0.5%；366~730 天 0.25%；>730 天 0% */
    private static final List<RedemptionFeeRule> DEFAULT_REDEMPTION_FEE_RULES = List.of(
            new RedemptionFeeRule(6, Rate.of("0.015")),
            new RedemptionFeeRule(365, Rate.of("0.005")),
            new RedemptionFeeRule(730, Rate.of("0.0025")));

    private final String productId;
    private final String productCode;
    private final String productName;
    private ProductStatus status;
    private final Money minSubscriptionAmount;
    private final Rate subscriptionFeeRate;
    private final RiskLevel riskLevel;
    private final List<RedemptionFeeRule> redemptionFeeRules;

    public FundProduct(String productId, String productCode, String productName,
                       ProductStatus status, Money minSubscriptionAmount,
                       Rate subscriptionFeeRate, RiskLevel riskLevel,
                       List<RedemptionFeeRule> redemptionFeeRules) {
        this.productId = Objects.requireNonNull(productId, "产品ID不能为空");
        this.productCode = Objects.requireNonNull(productCode, "基金代码不能为空");
        this.productName = Objects.requireNonNull(productName, "基金名称不能为空");
        this.status = Objects.requireNonNull(status, "产品状态不能为空");
        this.minSubscriptionAmount = Objects.requireNonNull(minSubscriptionAmount, "起购金额不能为空");
        if (!minSubscriptionAmount.isPositive()) {
            throw new IllegalArgumentException("起购金额必须大于 0");
        }
        this.subscriptionFeeRate = Objects.requireNonNull(subscriptionFeeRate, "申购费率不能为空");
        this.riskLevel = Objects.requireNonNull(riskLevel, "风险等级不能为空");
        // 拷贝并按持有天数上限升序排列，保证梯度匹配从短持有期到长持有期
        this.redemptionFeeRules = Objects.requireNonNull(redemptionFeeRules, "赎回费率规则不能为空")
                .stream()
                .sorted(Comparator.comparingInt(RedemptionFeeRule::maxHoldingDaysInclusive))
                .toList();
    }

    /**
     * 工厂方法：创建在售产品（使用默认赎回费率梯度）。
     */
    public static FundProduct onSale(String productId, String productCode, String productName,
                                     Money minSubscriptionAmount, Rate subscriptionFeeRate,
                                     RiskLevel riskLevel) {
        return new FundProduct(productId, productCode, productName, ProductStatus.ON_SALE,
                minSubscriptionAmount, subscriptionFeeRate, riskLevel, DEFAULT_REDEMPTION_FEE_RULES);
    }

    /** 是否可申购 */
    public boolean canSubscribe() {
        return status == ProductStatus.ON_SALE;
    }

    /** 是否可赎回 */
    public boolean canRedeem() {
        return status == ProductStatus.ON_SALE;
    }

    /** 暂停申购 / 赎回 */
    public void suspend() {
        this.status = ProductStatus.SUSPENDED;
    }

    /** 恢复上架 */
    public void placeOnSale() {
        this.status = ProductStatus.ON_SALE;
    }

    /**
     * 申购下单前置校验：产品在售 + 金额 ≥ 起购金额。
     */
    public void validateSubscription(Money subscriptionAmount) {
        if (!canSubscribe()) {
            throw new IllegalStateException("产品已暂停申购");
        }
        if (!subscriptionAmount.isPositive()) {
            throw new IllegalArgumentException("申购金额必须大于 0");
        }
        if (subscriptionAmount.compareTo(minSubscriptionAmount) < 0) {
            throw new IllegalArgumentException("申购金额不能低于起购金额 " + minSubscriptionAmount);
        }
    }

    /**
     * 赎回下单前置校验：产品可赎回 + 份额为正。
     */
    public void validateRedemption(Share redemptionShares) {
        if (!canRedeem()) {
            throw new IllegalStateException("产品已暂停赎回");
        }
        if (!redemptionShares.isPositive()) {
            throw new IllegalArgumentException("赎回份额必须大于 0");
        }
    }

    /**
     * 计算申购费（外扣法）：
     * 净申购金额 = 申购金额 ÷ (1 + 申购费率)；申购费 = 申购金额 − 净申购金额。
     */
    public Money calcSubscriptionFee(Money subscriptionAmount) {
        Money netAmount = subscriptionAmount.divide(BigDecimal.ONE.add(subscriptionFeeRate.value()));
        return subscriptionAmount.subtract(netAmount);
    }

    /**
     * 计算赎回费：赎回总额 × 命中梯度费率；持有天数超过全部规则上限时费率为 0。
     */
    public Money calcRedemptionFee(Money grossAmount, int holdingDays) {
        if (holdingDays < 0) {
            throw new IllegalArgumentException("持有天数不能为负");
        }
        for (RedemptionFeeRule rule : redemptionFeeRules) {
            if (rule.matches(holdingDays)) {
                return grossAmount.multiply(rule.feeRate().value());
            }
        }
        return Money.zero();
    }

    public String getProductId() {
        return productId;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getProductName() {
        return productName;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public Money getMinSubscriptionAmount() {
        return minSubscriptionAmount;
    }

    public Rate getSubscriptionFeeRate() {
        return subscriptionFeeRate;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public List<RedemptionFeeRule> getRedemptionFeeRules() {
        return redemptionFeeRules;
    }
}
