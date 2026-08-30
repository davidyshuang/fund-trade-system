package com.fund.trade.infrastructure.persistence;

import com.fund.trade.domain.model.order.SubscriptionOrder;
import com.fund.trade.domain.model.order.SubscriptionStatus;
import com.fund.trade.domain.repository.SubscriptionOrderRepository;
import com.fund.trade.domain.valueobject.Money;
import com.fund.trade.domain.valueobject.NetValue;
import com.fund.trade.domain.valueobject.Share;
import com.fund.trade.domain.valueobject.TradeDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 申购单仓储 PostgreSQL 实现。
 * 确认净值序列化为 TEXT（"navDate|nav" 格式，如 "2026-09-24|1.2500"），未确认为 NULL。
 */
@Repository
public class SubscriptionOrderRepositoryImpl implements SubscriptionOrderRepository {

    private final JdbcTemplate jdbc;

    public SubscriptionOrderRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(SubscriptionOrder order) {
        jdbc.update("""
                        INSERT INTO subscription_order
                          (order_id, customer_id, product_id, subscription_amount, t_day, status,
                           confirmed_net_value, confirmed_shares, confirmed_fee, fail_reason)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (order_id) DO UPDATE SET
                          customer_id = EXCLUDED.customer_id,
                          product_id = EXCLUDED.product_id,
                          subscription_amount = EXCLUDED.subscription_amount,
                          t_day = EXCLUDED.t_day,
                          status = EXCLUDED.status,
                          confirmed_net_value = EXCLUDED.confirmed_net_value,
                          confirmed_shares = EXCLUDED.confirmed_shares,
                          confirmed_fee = EXCLUDED.confirmed_fee,
                          fail_reason = EXCLUDED.fail_reason
                        """,
                order.getOrderId(), order.getCustomerId(), order.getProductId(),
                order.getSubscriptionAmount().value().toPlainString(),
                order.getTDay().date().toString(),
                order.getStatus().name(),
                order.getConfirmedNetValue() == null ? null
                        : order.getConfirmedNetValue().navDate() + "|" + order.getConfirmedNetValue().value().toPlainString(),
                order.getConfirmedShares() == null ? null : order.getConfirmedShares().value().toPlainString(),
                order.getConfirmedFee() == null ? null : order.getConfirmedFee().value().toPlainString(),
                order.getFailReason());
    }

    @Override
    public Optional<SubscriptionOrder> findById(String orderId) {
        List<SubscriptionOrder> list = jdbc.query(
                "SELECT * FROM subscription_order WHERE order_id = ?",
                (rs, i) -> mapRow(rs), orderId);
        return list.stream().findFirst();
    }

    @Override
    public List<SubscriptionOrder> findByCustomerId(String customerId) {
        return jdbc.query(
                "SELECT * FROM subscription_order WHERE customer_id = ? ORDER BY t_day, order_id",
                (rs, i) -> mapRow(rs), customerId);
    }

    @Override
    public List<SubscriptionOrder> findByStatusAndTDay(SubscriptionStatus status, LocalDate tDay) {
        return jdbc.query(
                "SELECT * FROM subscription_order WHERE status = ? AND t_day = ?",
                (rs, i) -> mapRow(rs), status.name(), tDay.toString());
    }

    @Override
    public List<SubscriptionOrder> query(String customerId, SubscriptionStatus status, String productId,
                                         LocalDate dateFrom, LocalDate dateTo, int offset, int limit) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT * FROM subscription_order" + buildWhere(args, customerId, status, productId, dateFrom, dateTo)
                + " ORDER BY t_day DESC, order_id LIMIT ? OFFSET ?";
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql, (rs, i) -> mapRow(rs), args.toArray());
    }

    @Override
    public long count(String customerId, SubscriptionStatus status, String productId,
                      LocalDate dateFrom, LocalDate dateTo) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM subscription_order"
                + buildWhere(args, customerId, status, productId, dateFrom, dateTo);
        Long count = jdbc.queryForObject(sql, Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private String buildWhere(List<Object> args, String customerId, SubscriptionStatus status,
                              String productId, LocalDate dateFrom, LocalDate dateTo) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (customerId != null && !customerId.isBlank()) {
            where.append(" AND customer_id = ?");
            args.add(customerId);
        }
        if (status != null) {
            where.append(" AND status = ?");
            args.add(status.name());
        }
        if (productId != null && !productId.isBlank()) {
            where.append(" AND product_id = ?");
            args.add(productId);
        }
        if (dateFrom != null) {
            where.append(" AND t_day >= ?");
            args.add(dateFrom.toString());
        }
        if (dateTo != null) {
            where.append(" AND t_day <= ?");
            args.add(dateTo.toString());
        }
        return where.toString();
    }

    private SubscriptionOrder mapRow(ResultSet rs) throws SQLException {
        String navText = rs.getString("confirmed_net_value");
        String confirmedShares = rs.getString("confirmed_shares");
        String confirmedFee = rs.getString("confirmed_fee");
        return SubscriptionOrder.restore(
                rs.getString("order_id"),
                rs.getString("customer_id"),
                rs.getString("product_id"),
                Money.of(rs.getString("subscription_amount")),
                TradeDate.of(LocalDate.parse(rs.getString("t_day"))),
                SubscriptionStatus.valueOf(rs.getString("status")),
                parseNetValue(rs.getString("product_id"), navText),
                confirmedShares == null ? null : Share.of(confirmedShares),
                confirmedFee == null ? null : Money.of(confirmedFee),
                rs.getString("fail_reason"));
    }

    private NetValue parseNetValue(String productId, String navText) {
        if (navText == null || navText.isBlank()) {
            return null;
        }
        String[] parts = navText.split("\\|");
        return NetValue.of(productId, LocalDate.parse(parts[0]), parts[1]);
    }
}
