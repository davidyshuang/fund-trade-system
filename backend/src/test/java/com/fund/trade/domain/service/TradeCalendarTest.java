package com.fund.trade.domain.service;

import com.fund.trade.domain.valueobject.TradeDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 交易日历领域服务单元测试。
 * 关联场景：S-01 ~ S-04、S-20（交易日历与 15:00 截单规则）
 */
class TradeCalendarTest {

    /** 无额外节假日的日历（周末天然为非交易日） */
    private final TradeCalendar calendar = TradeCalendar.of(Set.of());

    /** 含 2026 国庆节假期的日历（10-01 ~ 10-07） */
    private final TradeCalendar holidayCalendar = TradeCalendar.of(Set.of(
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3),
            LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6),
            LocalDate.of(2026, 10, 7)));

    @Test
    @DisplayName("S-01：交易日 15:00 前提交，T 日为当日（2026-09-01 为周二）")
    void GIVEN_交易日15点前_WHEN_解析T日_THEN_T日为当日() {
        LocalDateTime tuesdayMorning = LocalDateTime.of(2026, 9, 1, 14, 30);
        assertEquals(LocalDate.of(2026, 9, 1), calendar.resolveTDay(tuesdayMorning).date());
    }

    @Test
    @DisplayName("S-01 边界：恰好在 15:00:00 提交仍算当日（含 15:00）")
    void GIVEN_交易日恰好15点整_WHEN_解析T日_THEN_T日为当日() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 1, 15, 0, 0);
        assertEquals(LocalDate.of(2026, 9, 1), calendar.resolveTDay(cutoff).date());
    }

    @Test
    @DisplayName("S-02：交易日 15:00 后提交，T 日为下一交易日")
    void GIVEN_交易日15点后_WHEN_解析T日_THEN_T日为下一交易日() {
        LocalDateTime tuesdayAfternoon = LocalDateTime.of(2026, 9, 1, 15, 30);
        assertEquals(LocalDate.of(2026, 9, 2), calendar.resolveTDay(tuesdayAfternoon).date());
    }

    @Test
    @DisplayName("S-03：周六提交，T 日顺延至周一（2026-08-29 为周六）")
    void GIVEN_周六_WHEN_解析T日_THEN_T日为下周一() {
        LocalDateTime saturday = LocalDateTime.of(2026, 8, 29, 10, 0);
        assertEquals(LocalDate.of(2026, 8, 31), calendar.resolveTDay(saturday).date());
    }

    @Test
    @DisplayName("S-04：法定节假日提交，T 日顺延至节后首个交易日")
    void GIVEN_国庆节假日_WHEN_解析T日_THEN_T日为节后首个交易日() {
        // 2026-10-01（周四）为节假日
        LocalDateTime nationalDay = LocalDateTime.of(2026, 10, 1, 10, 0);
        assertEquals(LocalDate.of(2026, 10, 8), holidayCalendar.resolveTDay(nationalDay).date());
    }

    @Test
    @DisplayName("S-20：节假日前交易日 15:00 后提交，T 日跳过整个假期（2026-09-30 周三 15:30 → 10-08）")
    void GIVEN_节前交易日15点后_WHEN_解析T日_THEN_T日为10月8日() {
        LocalDateTime beforeHoliday = LocalDateTime.of(2026, 9, 30, 15, 30);
        assertEquals(LocalDate.of(2026, 10, 8), holidayCalendar.resolveTDay(beforeHoliday).date());
    }

    @Test
    @DisplayName("交易日判断：周末与节假日均非交易日")
    void 交易日判断() {
        // 2026-08-28 为周五（交易日）
        assertTrue(calendar.isTradeDay(LocalDate.of(2026, 8, 28)));
        // 2026-08-29 为周六
        assertFalse(calendar.isTradeDay(LocalDate.of(2026, 8, 29)));
        // 2026-10-01 为节假日
        assertFalse(holidayCalendar.isTradeDay(LocalDate.of(2026, 10, 1)));
        // 2026-10-08 为节后首个交易日（周四）
        assertTrue(holidayCalendar.isTradeDay(LocalDate.of(2026, 10, 8)));
    }

    @Test
    @DisplayName("下一交易日：周五的下一交易日为下周一")
    void 下一交易日() {
        assertEquals(LocalDate.of(2026, 8, 31),
                calendar.nextTradeDay(LocalDate.of(2026, 8, 28)));
    }
}
