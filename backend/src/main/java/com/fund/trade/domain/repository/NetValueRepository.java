package com.fund.trade.domain.repository;

import com.fund.trade.domain.valueobject.NetValue;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 基金净值仓储接口（领域层定义，基础设施层实现）。
 */
public interface NetValueRepository {

    void save(NetValue netValue);

    Optional<NetValue> findByProductAndDate(String productId, LocalDate date);

    Optional<NetValue> findLatestByProduct(String productId);
}
