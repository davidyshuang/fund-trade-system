package com.fund.trade.infrastructure.persistence;

import com.fund.trade.domain.model.order.RedemptionOrder;
import com.fund.trade.domain.model.order.RedemptionStatus;
import com.fund.trade.domain.repository.RedemptionOrderRepository;
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
 * 赎回单仓储 PostgreSQL 实现（确认净值序列化规则与申购单一致）。
 */
@Repository
public class RedemptionOrderRepositoryImpl implements RedemptionOrderRepository {

    private final JdbcTemplate jdbc;

    public RedemptionOrderRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(RedemptionOrder order) {
        jdbc.update("""
                        INSERT INTO redemption_order
                          (order_id, customer_id, product_id, redemption_shares, t_day, status,
                           confirmed_net_value, redemption_amount, redemption_fee, fail_reason)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (order_id) DO UPDATE SET
                          customer_id = EXCLUDED.customer_id,
                          product_id = EXCLUDED.product_id,
                          redemption_shares = EXCLUDED.redemption_shares,
                          t_day = EXCLUDED.t_day,
                          status = EXCLUDED.status,
                          confirmed_net_value = EXCLUDED.confirmed_net_value,
                          redemption_amount = EXCLUDED.redemption_amount,
                          redemption_fee = EXCLUDED.redemption_fee,
                          fail_reason = EXCLUDED.fail_reason
                        """,
                order.getOrderId(), order.getCustomerId(), order.getProductId(),
                order.getRedemptionShares().value().toPlainString(),
                order.getTDay().date().toString(),
                order.getStatus().name(),
                order.getConfirmedNetValue() == null ? null
                        : order.getConfirmedNetValue().navDate() + "|" + order.getConfirmedNetValue().value().toPlainString(),
                order.getRedemptionAmount() == null ? null : order.getRedemptionAmount().value().toPlainString(),
                order.getRedemptionFee() == null ? null : order.getRedemptionFee().value().toPlainString(),
                order.getFailReason());
    }

    @Override
    public Optional<RedemptionOrder> findById(String orderId) {
        List<RedemptionOrder> list = jdbc.query(
                "SELECT * FROM redemption_order WHERE order_id = ?",
                (rs, i) -> mapRow(rs), orderId);
        return list.stream().findFirst();
    }

    @Override
    public List<RedemptionOrder> findByCustomerId(String customerId) {
        return jdbc.query(
                "SELECT * FROM redemption_order WHERE customer_id = ? ORDER BY t_day, order_id",
                (rs, i) -> mapRow(rs), customerId);
    }

    @Override
    public List<RedemptionOrder> findByStatusAndTDay(RedemptionStatus status, LocalDate tDay) {
        return jdbc.query(
                "SELECT * FROM redemption_order WHERE status = ? AND t_day = ?",
                (rs, i) -> mapRow(rs), status.name(), tDay.toString());
    }

    @Override
    public List<RedemptionOrder> query(String customerId, RedemptionStatus status, String productId,
                                       LocalDate dateFrom, LocalDate dateTo, int offset, int limit) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT * FROM redemption_order" + buildWhere(args, customerId, status, productId, dateFrom, dateTo)
                + " ORDER BY t_day DESC, order_id LIMIT ? OFFSET ?";
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql, (rs, i) -> mapRow(rs), args.toArray());
    }

    @Override
    public long count(String customerId, RedemptionStatus status, String productId,
                      LocalDate dateFrom, LocalDate dateTo) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM redemption_order"
                + buildWhere(args, customerId, status, productId, dateFrom, dateTo);
        Long count = jdbc.queryForObject(sql, Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private String buildWhere(List<Object> args, String customerId, RedemptionStatus status,
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

    private RedemptionOrder mapRow(ResultSet rs) throws SQLException {
        String navText = rs.getString("confirmed_net_value");
        String redemptionAmount = rs.getString("redemption_amount");
        String redemptionFee = rs.getString("redemption_fee");
        return RedemptionOrder.restore(
                rs.getString("order_id"),
                rs.getString("customer_id"),
                rs.getString("product_id"),
                Share.of(rs.getString("redemption_shares")),
                TradeDate.of(LocalDate.parse(rs.getString("t_day"))),
                RedemptionStatus.valueOf(rs.getString("status")),
                parseNetValue(rs.getString("product_id"), navText),
                redemptionAmount == null ? null : Money.of(redemptionAmount),
                redemptionFee == null ? null : Money.of(redemptionFee),
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
