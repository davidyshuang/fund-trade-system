package com.fund.trade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 基金申购赎回管理系统启动入口。
 * DDD 四层分包：api（Controller/DTO）、application（应用服务/事件编排）、
 * domain（聚合/值对象/领域服务/仓储接口/领域事件）、infrastructure（仓储实现/适配）。
 */
@SpringBootApplication
public class FundTradeApplication {

    public static void main(String[] args) {
        SpringApplication.run(FundTradeApplication.class, args);
    }
}
