package com.fund.trade.domain.repository;

import com.fund.trade.domain.model.order.SubscriptionOrder;
import com.fund.trade.domain.model.order.SubscriptionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 申购单仓储接口（领域层定义，基础设施层实现）。
 */
public interface SubscriptionOrderRepository {

    void save(SubscriptionOrder order);

    Optional<SubscriptionOrder> findById(String orderId);

    List<SubscriptionOrder> findByCustomerId(String customerId);

    /** 按状态与 T 日查询（T+1 确认批处理用） */
    List<SubscriptionOrder> findByStatusAndTDay(SubscriptionStatus status, LocalDate tDay);

    /** 多条件分页查询（status/productId/dateFrom/dateTo 可为空） */
    List<SubscriptionOrder> query(String customerId, SubscriptionStatus status, String productId,
                                  LocalDate dateFrom, LocalDate dateTo, int offset, int limit);

    long count(String customerId, SubscriptionStatus status, String productId,
               LocalDate dateFrom, LocalDate dateTo);
}
