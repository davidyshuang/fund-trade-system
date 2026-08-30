package com.fund.trade.infrastructure.persistence;

import com.fund.trade.domain.model.position.SharePosition;
import com.fund.trade.domain.repository.SharePositionRepository;
import com.fund.trade.domain.valueobject.Share;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 份额持仓仓储 PostgreSQL 实现。
 * last_credit_date 为持久化支撑字段（计算赎回持有天数基准），不属于聚合模型，
 * 因此 save() 时不覆盖该字段，由 updateLastCreditDate 单独维护。
 */
@Repository
public class SharePositionRepositoryImpl implements SharePositionRepository {

    private final JdbcTemplate jdbc;

    public SharePositionRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(SharePosition position) {
        jdbc.update("""
                        INSERT INTO share_position
                          (ta_account_id, customer_id, product_id, total_shares, frozen_shares, last_credit_date)
                        VALUES (?, ?, ?, ?, ?, NULL)
                        ON CONFLICT(customer_id, product_id) DO UPDATE SET
                          total_shares = excluded.total_shares,
                          frozen_shares = excluded.frozen_shares
                        """,
                position.getTaAccountId(), position.getCustomerId(), position.getProductId(),
                position.getTotalShares().value().toPlainString(),
                position.getFrozenShares().value().toPlainString());
    }

    @Override
    public Optional<SharePosition> findByCustomerIdAndProductId(String customerId, String productId) {
        List<SharePosition> list = jdbc.query(
                "SELECT * FROM share_position WHERE customer_id = ? AND product_id = ?",
                (rs, i) -> mapRow(rs), customerId, productId);
        return list.stream().findFirst();
    }

    @Override
    public List<SharePosition> findByCustomerId(String customerId) {
        return jdbc.query(
                "SELECT * FROM share_position WHERE customer_id = ? ORDER BY product_id",
                (rs, i) -> mapRow(rs), customerId);
    }

    @Override
    public void updateLastCreditDate(String customerId, String productId, LocalDate date) {
        jdbc.update("UPDATE share_position SET last_credit_date = ? WHERE customer_id = ? AND product_id = ?",
                date.toString(), customerId, productId);
    }

    @Override
    public Optional<LocalDate> findLastCreditDate(String customerId, String productId) {
        List<String> dates = jdbc.query(
                "SELECT last_credit_date FROM share_position WHERE customer_id = ? AND product_id = ?",
                (rs, i) -> rs.getString(1), customerId, productId);
        return dates.stream()
                .filter(d -> d != null && !d.isBlank())
                .findFirst()
                .map(LocalDate::parse);
    }

    private SharePosition mapRow(ResultSet rs) throws SQLException {
        return new SharePosition(
                rs.getString("ta_account_id"),
                rs.getString("customer_id"),
                rs.getString("product_id"),
                Share.of(rs.getString("total_shares")),
                Share.of(rs.getString("frozen_shares")));
    }
}
