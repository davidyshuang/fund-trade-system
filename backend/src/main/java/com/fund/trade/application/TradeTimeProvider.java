package com.fund.trade.application;

import java.time.LocalDateTime;

/**
 * 交易时间提供者端口（application 层定义，基础设施层实现）。
 * 用于 T 日解析（截单/顺延），测试中可替换为固定时钟。
 */
public interface TradeTimeProvider {

    LocalDateTime now();
}
