package com.fund.trade.domain.repository;

import com.fund.trade.domain.model.product.FundProduct;

import java.util.List;
import java.util.Optional;

/**
 * 基金产品仓储接口（领域层定义，基础设施层实现）。
 */
public interface FundProductRepository {

    void save(FundProduct product);

    Optional<FundProduct> findById(String productId);

    /** 在售产品分页查询（productName 模糊匹配，可为空） */
    List<FundProduct> findOnSale(String productNameLike, int offset, int limit);

    long countOnSale(String productNameLike);
}
