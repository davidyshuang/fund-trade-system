package com.fund.trade.domain.model.product;

import com.fund.trade.domain.valueobject.Rate;

/**
 * 赎回费率规则（FundProduct 聚合内实体）。
 * maxHoldingDaysInclusive 为持有天数上限（含）；
 * 持有天数 ≤ 上限时命中该规则，适用对应费率。
 */
public record RedemptionFeeRule(int maxHoldingDaysInclusive, Rate feeRate) {

    /** 判断指定持有天数是否命中该规则 */
    public boolean matches(int holdingDays) {
        return holdingDays <= maxHoldingDaysInclusive;
    }
}
