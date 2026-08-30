package com.fund.trade.application;

import com.fund.trade.domain.model.funds.FundsAccount;
import com.fund.trade.domain.model.order.RedemptionOrder;
import com.fund.trade.domain.model.order.RedemptionStatus;
import com.fund.trade.domain.model.order.SubscriptionOrder;
import com.fund.trade.domain.model.order.SubscriptionStatus;
import com.fund.trade.domain.model.position.SharePosition;
import com.fund.trade.domain.model.product.FundProduct;
import com.fund.trade.domain.repository.FundProductRepository;
import com.fund.trade.domain.repository.FundsAccountRepository;
import com.fund.trade.domain.repository.OrderTraceRepository;
import com.fund.trade.domain.repository.RedemptionOrderRepository;
import com.fund.trade.domain.repository.SharePositionRepository;
import com.fund.trade.domain.repository.SubscriptionOrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 查询应用服务：产品列表、持仓、资金账户、订单列表与详情（含状态轨迹）。
 * 查询侧不做业务规则计算，仅做数据组装。
 */
@Service
public class QueryAppService {

    /** 持仓查询视图（聚合产品名称） */
    public record PositionView(String productId, String productName, BigDecimal totalShares,
                               BigDecimal frozenShares, BigDecimal availableShares) {
    }

    /** 分页结果 */
    public record PageResult<T>(List<T> list, long total) {
    }

    private final FundProductRepository fundProductRepository;
    private final FundsAccountRepository fundsAccountRepository;
    private final SharePositionRepository sharePositionRepository;
    private final SubscriptionOrderRepository subscriptionOrderRepository;
    private final RedemptionOrderRepository redemptionOrderRepository;
    private final OrderTraceRepository orderTraceRepository;

    public QueryAppService(FundProductRepository fundProductRepository,
                           FundsAccountRepository fundsAccountRepository,
                           SharePositionRepository sharePositionRepository,
                           SubscriptionOrderRepository subscriptionOrderRepository,
                           RedemptionOrderRepository redemptionOrderRepository,
                           OrderTraceRepository orderTraceRepository) {
        this.fundProductRepository = fundProductRepository;
        this.fundsAccountRepository = fundsAccountRepository;
        this.sharePositionRepository = sharePositionRepository;
        this.subscriptionOrderRepository = subscriptionOrderRepository;
        this.redemptionOrderRepository = redemptionOrderRepository;
        this.orderTraceRepository = orderTraceRepository;
    }

    /** 在售产品分页查询 */
    public PageResult<FundProduct> productsOnSale(String productName, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<FundProduct> list = fundProductRepository.findOnSale(productName, offset, pageSize);
        return new PageResult<>(list, fundProductRepository.countOnSale(productName));
    }

    /** 客户全部持仓（含产品名称） */
    public List<PositionView> positionsOf(String customerId) {
        return sharePositionRepository.findByCustomerId(customerId).stream()
                .map(this::toPositionView)
                .toList();
    }

    private PositionView toPositionView(SharePosition position) {
        String productName = fundProductRepository.findById(position.getProductId())
                .map(FundProduct::getProductName)
                .orElse("");
        return new PositionView(position.getProductId(), productName,
                position.getTotalShares().value(),
                position.getFrozenShares().value(),
                position.availableShares().value());
    }

    /** 客户资金账户 */
    public Optional<FundsAccount> fundsAccountOf(String customerId) {
        return fundsAccountRepository.findByCustomerId(customerId);
    }

    /** 申购单详情 */
    public Optional<SubscriptionOrder> subscriptionOf(String orderId) {
        return subscriptionOrderRepository.findById(orderId);
    }

    /** 赎回单详情 */
    public Optional<RedemptionOrder> redemptionOf(String orderId) {
        return redemptionOrderRepository.findById(orderId);
    }

    /** 订单状态流转轨迹（按时间正序） */
    public List<OrderTraceRepository.OrderTraceEntry> tracesOf(String orderId) {
        return orderTraceRepository.findByOrderId(orderId);
    }

    /** 申购单多条件分页查询 */
    public PageResult<SubscriptionOrder> querySubscriptions(String customerId, SubscriptionStatus status,
                                                            String productId, LocalDate dateFrom,
                                                            LocalDate dateTo, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<SubscriptionOrder> list = subscriptionOrderRepository.query(
                customerId, status, productId, dateFrom, dateTo, offset, pageSize);
        return new PageResult<>(list, subscriptionOrderRepository.count(
                customerId, status, productId, dateFrom, dateTo));
    }

    /** 赎回单多条件分页查询 */
    public PageResult<RedemptionOrder> queryRedemptions(String customerId, RedemptionStatus status,
                                                        String productId, LocalDate dateFrom,
                                                        LocalDate dateTo, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<RedemptionOrder> list = redemptionOrderRepository.query(
                customerId, status, productId, dateFrom, dateTo, offset, pageSize);
        return new PageResult<>(list, redemptionOrderRepository.count(
                customerId, status, productId, dateFrom, dateTo));
    }
}
