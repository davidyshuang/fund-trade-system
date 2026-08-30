package com.fund.trade.infrastructure.persistence;

import com.fund.trade.domain.model.funds.FundsAccount;
import com.fund.trade.domain.model.product.FundProduct;
import com.fund.trade.domain.model.product.RiskLevel;
import com.fund.trade.domain.repository.FundProductRepository;
import com.fund.trade.domain.repository.FundsAccountRepository;
import com.fund.trade.domain.repository.HolidayRepository;
import com.fund.trade.domain.valueobject.Money;
import com.fund.trade.domain.valueobject.Rate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDate;

/**
 * 数据库初始化器：应用启动时确保表结构就绪，并为全新库预置演示数据
 * （2 只在售产品、2 个客户资金账户、2026 年节假日）。
 * 幂等：表结构使用 IF NOT EXISTS；演示数据仅在 fund_product 为空时写入。
 */
@Component
public class DatabaseInitializer implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final FundProductRepository fundProductRepository;
    private final FundsAccountRepository fundsAccountRepository;
    private final HolidayRepository holidayRepository;

    public DatabaseInitializer(DataSource dataSource, JdbcTemplate jdbc,
                               FundProductRepository fundProductRepository,
                               FundsAccountRepository fundsAccountRepository,
                               HolidayRepository holidayRepository) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.fundProductRepository = fundProductRepository;
        this.fundsAccountRepository = fundsAccountRepository;
        this.holidayRepository = holidayRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(conn,
                    new EncodedResource(new ClassPathResource("schema.sql"), StandardCharsets.UTF_8));
        }
        seedDemoDataIfEmpty();
    }

    private void seedDemoDataIfEmpty() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM fund_product", Long.class);
        if (count != null && count > 0) {
            return;
        }
        // 演示产品
        fundProductRepository.save(FundProduct.onSale("P001", "000001", "华夏成长混合",
                Money.of("1000.00"), Rate.of("0.01"), RiskLevel.L3));
        fundProductRepository.save(FundProduct.onSale("P002", "000002", "华夏稳健债券",
                Money.of("500.00"), Rate.of("0.005"), RiskLevel.L2));
        // 演示客户资金账户
        fundsAccountRepository.save(new FundsAccount("FA-C001", "C001", Money.of("20000.00")));
        fundsAccountRepository.save(new FundsAccount("FA-C002", "C002", Money.of("50000.00")));
        // 2026 年节假日（国庆 10-01 ~ 10-07、元旦 01-01）
        for (int day = 1; day <= 7; day++) {
            holidayRepository.add(LocalDate.of(2026, 10, day));
        }
        holidayRepository.add(LocalDate.of(2026, 1, 1));
        holidayRepository.add(LocalDate.of(2027, 1, 1));
    }
}
