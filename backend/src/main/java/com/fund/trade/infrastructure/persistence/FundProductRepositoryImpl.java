package com.fund.trade.infrastructure.persistence;

import com.fund.trade.domain.model.product.FundProduct;
import com.fund.trade.domain.model.product.ProductStatus;
import com.fund.trade.domain.model.product.RedemptionFeeRule;
import com.fund.trade.domain.model.product.RiskLevel;
import com.fund.trade.domain.repository.FundProductRepository;
import com.fund.trade.domain.valueobject.Money;
import com.fund.trade.domain.valueobject.Rate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 基金产品仓储 PostgreSQL 实现。
 * 赎回费率规则序列化为 TEXT（"6:0.0150;365:0.0050" 格式）。
 */
@Repository
public class FundProductRepositoryImpl implements FundProductRepository {

    private final JdbcTemplate jdbc;

    public FundProductRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(FundProduct product) {
        jdbc.update("""
                        INSERT INTO fund_product
                          (product_id, product_code, product_name, status, min_subscription_amount,
                           subscription_fee_rate, risk_level, redemption_fee_rules)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (product_id) DO UPDATE SET
                          product_code = EXCLUDED.product_code,
                          product_name = EXCLUDED.product_name,
                          status = EXCLUDED.status,
                          min_subscription_amount = EXCLUDED.min_subscription_amount,
                          subscription_fee_rate = EXCLUDED.subscription_fee_rate,
                          risk_level = EXCLUDED.risk_level,
                          redemption_fee_rules = EXCLUDED.redemption_fee_rules
                        """,
                product.getProductId(), product.getProductCode(), product.getProductName(),
                product.getStatus().name(),
                product.getMinSubscriptionAmount().value().toPlainString(),
                product.getSubscriptionFeeRate().value().toPlainString(),
                product.getRiskLevel().name(),
                serializeRules(product.getRedemptionFeeRules()));
    }

    @Override
    public Optional<FundProduct> findById(String productId) {
        List<FundProduct> list = jdbc.query(
                "SELECT * FROM fund_product WHERE product_id = ?",
                (rs, i) -> mapRow(rs), productId);
        return list.stream().findFirst();
    }

    @Override
    public List<FundProduct> findOnSale(String productNameLike, int offset, int limit) {
        if (productNameLike != null && !productNameLike.isBlank()) {
            return jdbc.query("""
                            SELECT * FROM fund_product
                            WHERE status = 'ON_SALE' AND product_name LIKE ?
                            ORDER BY product_id LIMIT ? OFFSET ?
                            """,
                    (rs, i) -> mapRow(rs), "%" + productNameLike + "%", limit, offset);
        }
        return jdbc.query("""
                        SELECT * FROM fund_product
                        WHERE status = 'ON_SALE'
                        ORDER BY product_id LIMIT ? OFFSET ?
                        """,
                (rs, i) -> mapRow(rs), limit, offset);
    }

    @Override
    public long countOnSale(String productNameLike) {
        Long count;
        if (productNameLike != null && !productNameLike.isBlank()) {
            count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM fund_product WHERE status = 'ON_SALE' AND product_name LIKE ?",
                    Long.class, "%" + productNameLike + "%");
        } else {
            count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM fund_product WHERE status = 'ON_SALE'", Long.class);
        }
        return count == null ? 0 : count;
    }

    private FundProduct mapRow(ResultSet rs) throws SQLException {
        return new FundProduct(
                rs.getString("product_id"),
                rs.getString("product_code"),
                rs.getString("product_name"),
                ProductStatus.valueOf(rs.getString("status")),
                Money.of(rs.getString("min_subscription_amount")),
                Rate.of(rs.getString("subscription_fee_rate")),
                RiskLevel.valueOf(rs.getString("risk_level")),
                deserializeRules(rs.getString("redemption_fee_rules")));
    }

    private String serializeRules(List<RedemptionFeeRule> rules) {
        return rules.stream()
                .map(r -> r.maxHoldingDaysInclusive() + ":" + r.feeRate().value().toPlainString())
                .collect(Collectors.joining(";"));
    }

    private List<RedemptionFeeRule> deserializeRules(String text) {
        return Arrays.stream(text.split(";"))
                .map(s -> s.split(":"))
                .map(p -> new RedemptionFeeRule(Integer.parseInt(p[0]), Rate.of(p[1])))
                .toList();
    }
}
