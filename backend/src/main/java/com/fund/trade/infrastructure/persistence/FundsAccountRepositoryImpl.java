package com.fund.trade.infrastructure.persistence;

import com.fund.trade.domain.model.funds.FundsAccount;
import com.fund.trade.domain.repository.FundsAccountRepository;
import com.fund.trade.domain.valueobject.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 资金账户仓储 PostgreSQL 实现。
 * 金额以 TEXT 存储（BigDecimal 字符串），避免浮点精度丢失。
 */
@Repository
public class FundsAccountRepositoryImpl implements FundsAccountRepository {

    private final JdbcTemplate jdbc;

    public FundsAccountRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(FundsAccount account) {
        jdbc.update("""
                        INSERT INTO funds_account
                          (account_id, customer_id, balance, frozen_amount)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (account_id) DO UPDATE SET
                          customer_id = EXCLUDED.customer_id,
                          balance = EXCLUDED.balance,
                          frozen_amount = EXCLUDED.frozen_amount
                        """,
                account.getAccountId(), account.getCustomerId(),
                account.getBalance().value().toPlainString(),
                account.getFrozenAmount().value().toPlainString());
    }

    @Override
    public Optional<FundsAccount> findByCustomerId(String customerId) {
        List<FundsAccount> list = jdbc.query(
                "SELECT * FROM funds_account WHERE customer_id = ?",
                (rs, i) -> new FundsAccount(
                        rs.getString("account_id"),
                        rs.getString("customer_id"),
                        Money.of(rs.getString("balance")),
                        Money.of(rs.getString("frozen_amount"))),
                customerId);
        return list.stream().findFirst();
    }
}
