package com.fund.trade.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * 集成测试共享配置：PostgreSQL 测试库（默认本机 fund_trade_test）+ 可变时钟。
 * 连接信息可用环境变量覆盖（CI 中注入）：
 *   TEST_DB_URL / TEST_DB_USERNAME / TEST_DB_PASSWORD
 */
@TestConfiguration
@EnableTransactionManagement
public class PgTestConfig {

    @Bean
    public DataSource dataSource() {
        String url = envOr("TEST_DB_URL", "jdbc:postgresql://localhost:5432/fund_trade_test");
        String username = envOr("TEST_DB_USERNAME", System.getProperty("user.name"));
        String password = envOr("TEST_DB_PASSWORD", "");
        SingleConnectionDataSource dataSource =
                new SingleConnectionDataSource(url, username, password, true);
        dataSource.setSuppressClose(true);
        return dataSource;
    }

    /** 测试时钟标记为 @Primary，覆盖生产环境的 SystemTradeTimeProvider */
    @Bean
    @Primary
    public TestTimeProvider tradeTimeProvider() {
        return new TestTimeProvider();
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
