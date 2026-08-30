package com.fund.trade.infrastructure.persistence;

import com.fund.trade.domain.repository.OrderTraceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 订单状态流转轨迹仓储 PostgreSQL 实现。
 */
@Repository
public class OrderTraceRepositoryImpl implements OrderTraceRepository {

    private final JdbcTemplate jdbc;

    public OrderTraceRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(String orderId, String fromStatus, String toStatus, String triggerEvent) {
        jdbc.update("""
                        INSERT INTO order_status_trace
                          (order_id, occurred_at, from_status, to_status, trigger_event)
                        VALUES (?, LOCALTIMESTAMP, ?, ?, ?)
                        """,
                orderId, fromStatus, toStatus, triggerEvent);
    }

    @Override
    public List<OrderTraceEntry> findByOrderId(String orderId) {
        return jdbc.query("""
                        SELECT occurred_at, from_status, to_status, trigger_event
                        FROM order_status_trace
                        WHERE order_id = ?
                        ORDER BY id
                        """,
                (rs, i) -> new OrderTraceEntry(
                        rs.getString("occurred_at"),
                        rs.getString("from_status"),
                        rs.getString("to_status"),
                        rs.getString("trigger_event")),
                orderId);
    }
}
