package com.fund.trade.api;

import com.fund.trade.domain.model.funds.FundsAccount;
import com.fund.trade.domain.model.product.FundProduct;
import com.fund.trade.domain.model.product.RiskLevel;
import com.fund.trade.domain.repository.FundProductRepository;
import com.fund.trade.domain.repository.FundsAccountRepository;
import com.fund.trade.domain.valueobject.Money;
import com.fund.trade.domain.valueobject.Rate;
import com.fund.trade.testsupport.PgTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST API 冒烟测试：覆盖「申购下单 → 发布净值 → T+1 确认 → 持仓查询」端到端 HTTP 链路。
 * 时钟固定为 2026-09-24 10:00（周四），T 日 = 2026-09-24。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PgTestConfig.class)
@DisplayName("REST API 冒烟测试")
class ApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FundProductRepository fundProductRepository;
    @Autowired
    private FundsAccountRepository fundsAccountRepository;

    @BeforeEach
    void resetAndSeed() throws Exception {
        try (Connection conn = jdbcTemplate.getDataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            for (String table : new String[]{"order_status_trace", "subscription_order",
                    "redemption_order", "net_value", "share_position", "funds_account",
                    "fund_product", "trade_calendar_holiday"}) {
                stmt.execute("DROP TABLE IF EXISTS " + table);
            }
        }
        try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(conn,
                    new EncodedResource(new ClassPathResource("schema.sql"), StandardCharsets.UTF_8));
        }
        fundProductRepository.save(FundProduct.onSale("P001", "000001", "华夏成长混合",
                Money.of("1000.00"), Rate.of("0.01"), RiskLevel.L3));
        fundsAccountRepository.save(new FundsAccount("FA-C001", "C001", Money.of("20000.00")));
    }

    @Test
    @DisplayName("全链路：产品列表 → 申购下单 → 发布净值 → T+1 确认 → 持仓/资金/订单详情查询")
    void test_全链路() throws Exception {
        // 1. 产品列表
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].productId").value("P001"));

        // 2. 申购下单（时钟固定周四 10:00 → T日 2026-09-24）
        MvcResult subResult = mockMvc.perform(post("/api/orders/subscription")
                        .contentType("application/json")
                        .content("""
                                {"customerId":"C001","productId":"P001","subscriptionAmount":"10000.00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("FUNDS_FROZEN"))
                .andExpect(jsonPath("$.data.tDay").value("2026-09-24"))
                .andReturn();
        assertTrue(subResult.getResponse().getContentAsString().contains("orderId"));

        String orderId = extractField(subResult.getResponse().getContentAsString(), "orderId");

        // 3. 管理端发布 T 日净值
        mockMvc.perform(post("/api/admin/net-values")
                        .contentType("application/json")
                        .content("{\"productId\":\"P001\",\"navDate\":\"2026-09-24\",\"nav\":\"1.2500\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 4. 触发 T+1 确认批处理
        mockMvc.perform(post("/api/admin/confirmations/run")
                        .contentType("application/json")
                        .content("{\"tDay\":\"2026-09-24\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscriptionConfirmed").value(1));

        // 5. 持仓查询：份额 7920.79
        mockMvc.perform(get("/api/customers/C001/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].totalShares")
                        .value(7920.79));

        // 6. 资金账户查询：余额 10000，冻结 0
        mockMvc.perform(get("/api/customers/C001/funds-account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(10000.00))
                .andExpect(jsonPath("$.data.frozenAmount").value(0.00));

        // 7. 订单详情（含状态流转轨迹）
        mockMvc.perform(get("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.traces[0].toStatus").value("CREATED"))
                .andExpect(jsonPath("$.data.traces.length()").value(3));

        // 8. 申购单列表查询
        mockMvc.perform(get("/api/orders/subscription?customerId=C001&pageNum=1&pageSize=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("异常链路：资金不足下单返回 40003 业务错误码（HTTP 200 + 业务码）")
    void test_资金不足_返回业务错误码() throws Exception {
        mockMvc.perform(post("/api/orders/subscription")
                        .contentType("application/json")
                        .content("""
                                {"customerId":"C001","productId":"P001","subscriptionAmount":"50000.00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40003));
    }

    private String extractField(String body, String field) {
        String token = "\"" + field + "\":\"";
        int idx = body.indexOf(token);
        int start = idx + token.length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }
}
