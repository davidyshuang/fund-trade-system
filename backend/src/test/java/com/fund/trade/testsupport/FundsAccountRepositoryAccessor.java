package com.fund.trade.testsupport;

import com.fund.trade.domain.model.funds.FundsAccount;
import com.fund.trade.domain.repository.FundsAccountRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 测试辅助：便捷读取资金账户金额字段。
 */
@Component
public class FundsAccountRepositoryAccessor {

    private final FundsAccountRepository fundsAccountRepository;

    public FundsAccountRepositoryAccessor(FundsAccountRepository fundsAccountRepository) {
        this.fundsAccountRepository = fundsAccountRepository;
    }

    public BigDecimal balance(String customerId) {
        return find(customerId).getBalance().value();
    }

    public BigDecimal frozen(String customerId) {
        return find(customerId).getFrozenAmount().value();
    }

    public BigDecimal available(String customerId) {
        return find(customerId).availableAmount().value();
    }

    private FundsAccount find(String customerId) {
        return fundsAccountRepository.findByCustomerId(customerId).orElseThrow();
    }
}
