package com.fund.trade.api;

import com.fund.trade.application.QueryAppService;
import com.fund.trade.domain.model.product.FundProduct;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 基金产品查询接口（产品上下文 - 客户端）。
 */
@RestController
@RequestMapping("/api")
public class ProductController {

    private final QueryAppService queryAppService;

    public ProductController(QueryAppService queryAppService) {
        this.queryAppService = queryAppService;
    }

    /** GET /api/products：在售产品分页列表（productName 模糊搜索） */
    @GetMapping("/products")
    public ApiResponse<ProductPageView> products(
            @RequestParam(required = false) String productName,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        int page = Math.max(1, pageNum);
        int size = Math.min(Math.max(1, pageSize), 100);
        QueryAppService.PageResult<FundProduct> result =
                queryAppService.productsOnSale(productName, page, size);
        List<ProductView> list = result.list().stream().map(ProductView::from).toList();
        return ApiResponse.ok(new ProductPageView(list, result.total()));
    }

    /** 产品视图 DTO */
    public record ProductView(String productId, String productCode, String productName,
                              String status, BigDecimal minSubscriptionAmount,
                              BigDecimal subscriptionFeeRate, String riskLevel) {
        static ProductView from(FundProduct product) {
            return new ProductView(product.getProductId(), product.getProductCode(),
                    product.getProductName(), product.getStatus().name(),
                    product.getMinSubscriptionAmount().value(),
                    product.getSubscriptionFeeRate().value(),
                    product.getRiskLevel().name());
        }
    }

    /** 产品分页视图 DTO */
    public record ProductPageView(List<ProductView> list, long total) {
    }
}
