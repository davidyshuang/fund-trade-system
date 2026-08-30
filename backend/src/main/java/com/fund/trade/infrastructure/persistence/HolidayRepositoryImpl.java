package com.fund.trade.infrastructure.persistence;

import com.fund.trade.domain.repository.HolidayRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * 交易日历（节假日）仓储 PostgreSQL 实现。
 */
@Repository
public class HolidayRepositoryImpl implements HolidayRepository {

    private final JdbcTemplate jdbc;

    public HolidayRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(LocalDate holiday) {
        jdbc.update("INSERT INTO trade_calendar_holiday (holiday) VALUES (?) ON CONFLICT (holiday) DO NOTHING",
                holiday.toString());
    }

    @Override
    public Set<LocalDate> findAll() {
        return new HashSet<>(jdbc.query(
                "SELECT holiday FROM trade_calendar_holiday",
                (rs, i) -> LocalDate.parse(rs.getString(1))));
    }
}
