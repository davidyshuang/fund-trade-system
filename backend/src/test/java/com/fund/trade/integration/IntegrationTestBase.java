package com.fund.trade.integration;

import com.fund.trade.domain.model.funds.FundsAccount;
import com.fund.trade.domain.model.product.FundProduct;
import com.fund.trade.domain.model.product.RiskLevel;
import com.fund.trade.domain.repository.FundProductRepository;
import com.fund.trade.domain.repository.FundsAccountRepository;
import com.fund.trade.domain.valueobject.Money;
import com.fund.trade.domain.valueobject.Rate;
import com.fund.trade.testsupport.PgTestConfig;
import com.fund.trade.testsupport.TestTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * 集成测试基类：PostgreSQL 测试数据库 + 可变时钟。
 * 每个测试方法前重建表结构并注入基础种子数据（在售产品 + 客户资金账户）。
 */
@SpringBootTest
@Import(PgTestConfig.class)
public abstract class IntegrationTestBase {

    @Autowired
    protected FundProductRepository fundProductRepository;
    @Autowired
    protected FundsAccountRepository fundsAccountRepository;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected TestTimeProvider timeProvider;

    @BeforeEach
    void resetDatabase() throws SQLException {
        dropAllTables();
        executeSchema();
        seedBaseData();
        timeProvider.setFixed(LocalDateTime.of(2026, 9, 24, 10, 0));
    }

    private void dropAllTables() throws SQLException {
        try (Connection conn = jdbcTemplate.getDataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            for (String table : new String[]{"order_status_trace", "subscription_order",
                    "redemption_order", "net_value", "share_position", "funds_account",
                    "fund_product", "trade_calendar_holiday"}) {
                stmt.execute("DROP TABLE IF EXISTS " + table);
            }
        }
    }

    private void executeSchema() throws SQLException {
        try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(conn,
                    new EncodedResource(new ClassPathResource("schema.sql"), StandardCharsets.UTF_8));
        }
    }

    /** 基础种子：在售产品 P001（起购 1000，申购费率 1%）+ 客户 C001 资金账户余额 20000 */
    private void seedBaseData() {
        FundProduct product = FundProduct.onSale("P001", "000001", "华夏成长混合",
                Money.of("1000.00"), Rate.of("0.01"), RiskLevel.L3);
        fundProductRepository.save(product);
        fundsAccountRepository.save(new FundsAccount("FA-C001", "C001", Money.of("20000.00")));
    }
}
