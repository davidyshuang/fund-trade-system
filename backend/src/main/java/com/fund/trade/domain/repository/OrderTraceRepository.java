package com.fund.trade.domain.repository;

import java.util.List;

/**
 * 订单状态流转轨迹仓储接口（查询支撑，领域层定义，基础设施层实现）。
 */
public interface OrderTraceRepository {

    /** 记录一次状态流转（fromStatus 为空表示初始创建） */
    void record(String orderId, String fromStatus, String toStatus, String triggerEvent);

    /** 查询订单全部状态流转轨迹（按时间正序） */
    List<OrderTraceEntry> findByOrderId(String orderId);

    /** 状态流转轨迹条目（查询视图） */
    record OrderTraceEntry(String occurredAt, String fromStatus, String toStatus, String triggerEvent) {
    }
}
