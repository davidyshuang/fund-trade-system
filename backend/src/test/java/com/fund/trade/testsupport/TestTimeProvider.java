package com.fund.trade.testsupport;

import com.fund.trade.application.TradeTimeProvider;

import java.time.LocalDateTime;

/**
 * 测试用可变时间提供者：可随时切换"当前时间"以覆盖截单/顺延等边界场景。
 */
public class TestTimeProvider implements TradeTimeProvider {

    /** 默认固定在 2026-09-24 10:00（周四，交易时段内） */
    private LocalDateTime current = LocalDateTime.of(2026, 9, 24, 10, 0);

    @Override
    public LocalDateTime now() {
        return current;
    }

    public void setFixed(LocalDateTime time) {
        this.current = time;
    }
}
