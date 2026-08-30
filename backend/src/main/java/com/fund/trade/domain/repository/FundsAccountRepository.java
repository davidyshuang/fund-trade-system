package com.fund.trade.domain.repository;

import com.fund.trade.domain.model.funds.FundsAccount;

import java.util.Optional;

/**
 * 资金账户仓储接口（领域层定义，基础设施层实现）。
 */
public interface FundsAccountRepository {

    void save(FundsAccount account);

    Optional<FundsAccount> findByCustomerId(String customerId);
}
