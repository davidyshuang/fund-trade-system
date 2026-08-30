package com.fund.trade.infrastructure.persistence;

import com.fund.trade.domain.repository.NetValueRepository;
import com.fund.trade.domain.valueobject.NetValue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 基金净值仓储 PostgreSQL 实现（净值 4 位小数，以 TEXT 存储）。
 */
@Repository
public class NetValueRepositoryImpl implements NetValueRepository {

    private final JdbcTemplate jdbc;

    public NetValueRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(NetValue netValue) {
        jdbc.update("""
                        INSERT INTO net_value (product_id, nav_date, nav)
                        VALUES (?, ?, ?)
                        ON CONFLICT (product_id, nav_date) DO UPDATE SET
                          nav = EXCLUDED.nav
                        """,
                netValue.productId(), netValue.navDate().toString(),
                netValue.value().toPlainString());
    }

    @Override
    public Optional<NetValue> findByProductAndDate(String productId, LocalDate date) {
        List<NetValue> list = jdbc.query(
                "SELECT * FROM net_value WHERE product_id = ? AND nav_date = ?",
                (rs, i) -> mapRow(rs, productId), productId, date.toString());
        return list.stream().findFirst();
    }

    @Override
    public Optional<NetValue> findLatestByProduct(String productId) {
        List<NetValue> list = jdbc.query(
                "SELECT * FROM net_value WHERE product_id = ? ORDER BY nav_date DESC LIMIT 1",
                (rs, i) -> mapRow(rs, productId), productId);
        return list.stream().findFirst();
    }

    private NetValue mapRow(java.sql.ResultSet rs, String productId) throws java.sql.SQLException {
        return NetValue.of(productId,
                LocalDate.parse(rs.getString("nav_date")),
                rs.getString("nav"));
    }
}
