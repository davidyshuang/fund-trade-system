package com.fund.trade.infrastructure;

import com.fund.trade.application.TradeTimeProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 系统时间提供者（生产环境使用系统时钟；测试环境由 TestTimeProvider 覆盖）。
 */
@Component
public class SystemTradeTimeProvider implements TradeTimeProvider {

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
