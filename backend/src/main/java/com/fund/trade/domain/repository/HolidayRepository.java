package com.fund.trade.domain.repository;

import java.time.LocalDate;
import java.util.Set;

/**
 * 交易日历（节假日）仓储接口（领域层定义，基础设施层实现）。
 */
public interface HolidayRepository {

    /** 登记非交易日（节假日） */
    void add(LocalDate holiday);

    /** 全量节假日集合（构建 TradeCalendar 领域服务用） */
    Set<LocalDate> findAll();
}
