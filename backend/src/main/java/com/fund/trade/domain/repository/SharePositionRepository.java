package com.fund.trade.domain.repository;

import com.fund.trade.domain.model.position.SharePosition;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 份额持仓仓储接口（领域层定义，基础设施层实现）。
 * lastCreditDate（最近入账日）为持久化支撑字段，用于计算赎回持有天数，不属于聚合模型。
 */
public interface SharePositionRepository {

    void save(SharePosition position);

    Optional<SharePosition> findByCustomerIdAndProductId(String customerId, String productId);

    List<SharePosition> findByCustomerId(String customerId);

    /** 更新最近入账日期（申购确认成功时由事件处理器调用） */
    void updateLastCreditDate(String customerId, String productId, LocalDate date);

    /** 查询最近入账日期（赎回确认时计算持有天数用） */
    Optional<LocalDate> findLastCreditDate(String customerId, String productId);
}
