package com.fund.trade.domain.model.order;

/**
 * 申购单状态机：
 * CREATED（已创建，待冻结资金）→ FUNDS_FROZEN（资金已冻结，待确认）
 * → CONFIRMED（已确认）/ CONFIRM_FAILED（确认失败，资金解冻退回）；
 * CREATED → CLOSED（资金冻结失败关闭）。
 * 状态只能沿上述路径单向流转。
 */
public enum SubscriptionStatus {
    /** 已创建（待冻结资金） */
    CREATED,
    /** 资金已冻结（待 T+1 确认） */
    FUNDS_FROZEN,
    /** 已确认（份额入账） */
    CONFIRMED,
    /** 确认失败（资金已解冻退回） */
    CONFIRM_FAILED,
    /** 已关闭（资金冻结失败） */
    CLOSED
}
