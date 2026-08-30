package com.fund.trade.domain.model.order;

/**
 * 赎回单状态机：
 * CREATED（已创建，待冻结份额）→ SHARES_FROZEN（份额已冻结，待确认）
 * → CONFIRMED（已确认）/ CONFIRM_FAILED（确认失败，份额解冻恢复）；
 * CREATED → CLOSED（份额冻结失败关闭）。
 * 状态只能沿上述路径单向流转。
 */
public enum RedemptionStatus {
    /** 已创建（待冻结份额） */
    CREATED,
    /** 份额已冻结（待 T+1 确认） */
    SHARES_FROZEN,
    /** 已确认（份额扣减、赎回款入账） */
    CONFIRMED,
    /** 确认失败（份额已解冻恢复可用） */
    CONFIRM_FAILED,
    /** 已关闭（份额冻结失败） */
    CLOSED
}
