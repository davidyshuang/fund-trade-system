package com.fund.trade.domain.repository;

import com.fund.trade.domain.model.order.RedemptionOrder;
import com.fund.trade.domain.model.order.RedemptionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 赎回单仓储接口（领域层定义，基础设施层实现）。
 */
public interface RedemptionOrderRepository {

    void save(RedemptionOrder order);

    Optional<RedemptionOrder> findById(String orderId);

    List<RedemptionOrder> findByCustomerId(String customerId);

    /** 按状态与 T 日查询（T+1 确认批处理用） */
    List<RedemptionOrder> findByStatusAndTDay(RedemptionStatus status, LocalDate tDay);

    /** 多条件分页查询（status/productId/dateFrom/dateTo 可为空） */
    List<RedemptionOrder> query(String customerId, RedemptionStatus status, String productId,
                                LocalDate dateFrom, LocalDate dateTo, int offset, int limit);

    long count(String customerId, RedemptionStatus status, String productId,
               LocalDate dateFrom, LocalDate dateTo);
}
