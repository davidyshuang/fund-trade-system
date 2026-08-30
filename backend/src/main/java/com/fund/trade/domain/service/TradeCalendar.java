package com.fund.trade.domain.service;

import com.fund.trade.domain.valueobject.TradeDate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

/**
 * 交易日历领域服务。
 * 业务规则：
 * 1. 周六、周日为非交易日；
 * 2. 日历中登记的节假日为非交易日；
 * 3. 交易日 15:00（含）前提交 → 当日为 T 日；15:00 后提交 → 下一交易日为 T 日；
 * 4. 非交易日提交 → 下一交易日为 T 日（按日历顺延）。
 */
public final class TradeCalendar {

    /** 交易截止时间（截单）：15:00 */
    public static final LocalTime CUTOFF_TIME = LocalTime.of(15, 0);

    private final Set<LocalDate> holidays;

    private TradeCalendar(Set<LocalDate> holidays) {
        this.holidays = Set.copyOf(holidays);
    }

    /** 工厂方法：基于节假日集合创建交易日历 */
    public static TradeCalendar of(Set<LocalDate> holidays) {
        return new TradeCalendar(holidays);
    }

    /** 判断指定日期是否为交易日（周一至周五且非节假日） */
    public boolean isTradeDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY
                && dayOfWeek != DayOfWeek.SUNDAY
                && !holidays.contains(date);
    }

    /** 获取指定日期之后的下一个交易日（不含当日） */
    public LocalDate nextTradeDay(LocalDate date) {
        LocalDate candidate = date.plusDays(1);
        while (!isTradeDay(candidate)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    /**
     * 按提交时刻解析 T 日。
     * 交易日 15:00（含）前提交 → 当日；否则顺延至下一交易日。
     */
    public TradeDate resolveTDay(LocalDateTime submitTime) {
        LocalDate today = submitTime.toLocalDate();
        boolean todayIsTDay = isTradeDay(today)
                && !submitTime.toLocalTime().isAfter(CUTOFF_TIME);
        if (todayIsTDay) {
            return TradeDate.of(today);
        }
        return TradeDate.of(nextTradeDay(today));
    }
}
