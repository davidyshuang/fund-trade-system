package com.fund.trade.api;

import com.fund.trade.application.BusinessException;
import com.fund.trade.application.ErrorCode;
import com.fund.trade.application.QueryAppService;
import com.fund.trade.application.RedemptionAppService;
import com.fund.trade.application.SubscriptionAppService;
import com.fund.trade.domain.model.order.RedemptionOrder;
import com.fund.trade.domain.model.order.RedemptionStatus;
import com.fund.trade.domain.model.order.SubscriptionOrder;
import com.fund.trade.domain.model.order.SubscriptionStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 交易订单接口（交易上下文 - 客户端）：
 * 申购下单、赎回下单、订单查询（列表/详情含状态轨迹）。
 */
@RestController
@RequestMapping("/api")
public class OrderController {

    private final SubscriptionAppService subscriptionAppService;
    private final RedemptionAppService redemptionAppService;
    private final QueryAppService queryAppService;

    public OrderController(SubscriptionAppService subscriptionAppService,
                           RedemptionAppService redemptionAppService,
                           QueryAppService queryAppService) {
        this.subscriptionAppService = subscriptionAppService;
        this.redemptionAppService = redemptionAppService;
        this.queryAppService = queryAppService;
    }

    /** POST /api/orders/subscription：申购下单 */
    @PostMapping("/orders/subscription")
    public ApiResponse<SubscriptionOrderView> placeSubscription(@RequestBody SubscriptionRequest request) {
        SubscriptionOrder order = subscriptionAppService.place(
                request.customerId(), request.productId(), request.subscriptionAmount());
        return ApiResponse.ok(SubscriptionOrderView.from(order, null));
    }

    /** POST /api/orders/redemption：赎回下单 */
    @PostMapping("/orders/redemption")
    public ApiResponse<RedemptionOrderView> placeRedemption(@RequestBody RedemptionRequest request) {
        RedemptionOrder order = redemptionAppService.place(
                request.customerId(), request.productId(), request.redemptionShares());
        return ApiResponse.ok(RedemptionOrderView.from(order, null));
    }

    /** GET /api/orders/{orderId}：订单详情（按前缀区分申购/赎回，含状态流转轨迹） */
    @GetMapping("/orders/{orderId}")
    public ApiResponse<?> orderDetail(@PathVariable String orderId) {
        List<TraceView> traces = queryAppService.tracesOf(orderId).stream()
                .map(TraceView::from).toList();
        if (orderId.startsWith("SUB-")) {
            SubscriptionOrder order = queryAppService.subscriptionOf(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
            return ApiResponse.ok(SubscriptionOrderView.from(order, traces));
        }
        if (orderId.startsWith("RED-")) {
            RedemptionOrder order = queryAppService.redemptionOf(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
            return ApiResponse.ok(RedemptionOrderView.from(order, traces));
        }
        throw new BusinessException(ErrorCode.PARAM_INVALID, "订单号格式不合法");
    }

    /** GET /api/orders/subscription：申购单多条件分页查询 */
    @GetMapping("/orders/subscription")
    public ApiResponse<SubscriptionPageView> querySubscriptions(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        SubscriptionStatus statusEnum = parseSubscriptionStatus(status);
        LocalDate from = parseDate(dateFrom);
        LocalDate to = parseDate(dateTo);
        int page = Math.max(1, pageNum);
        int size = Math.min(Math.max(1, pageSize), 100);
        QueryAppService.PageResult<SubscriptionOrder> result = queryAppService.querySubscriptions(
                customerId, statusEnum, productId, from, to, page, size);
        List<SubscriptionOrderView> list = result.list().stream()
                .map(o -> SubscriptionOrderView.from(o, null)).toList();
        return ApiResponse.ok(new SubscriptionPageView(list, result.total()));
    }

    /** GET /api/orders/redemption：赎回单多条件分页查询 */
    @GetMapping("/orders/redemption")
    public ApiResponse<RedemptionPageView> queryRedemptions(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        RedemptionStatus statusEnum = parseRedemptionStatus(status);
        LocalDate from = parseDate(dateFrom);
        LocalDate to = parseDate(dateTo);
        int page = Math.max(1, pageNum);
        int size = Math.min(Math.max(1, pageSize), 100);
        QueryAppService.PageResult<RedemptionOrder> result = queryAppService.queryRedemptions(
                customerId, statusEnum, productId, from, to, page, size);
        List<RedemptionOrderView> list = result.list().stream()
                .map(o -> RedemptionOrderView.from(o, null)).toList();
        return ApiResponse.ok(new RedemptionPageView(list, result.total()));
    }

    private SubscriptionStatus parseSubscriptionStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return SubscriptionStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的申购单状态：" + status);
        }
    }

    private RedemptionStatus parseRedemptionStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return RedemptionStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的赎回单状态：" + status);
        }
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "日期格式不合法：" + date);
        }
    }

    /** 申购下单请求 DTO */
    public record SubscriptionRequest(String customerId, String productId, String subscriptionAmount) {
    }

    /** 赎回下单请求 DTO */
    public record RedemptionRequest(String customerId, String productId, String redemptionShares) {
    }

    /** 订单状态轨迹视图 DTO */
    public record TraceView(String occurredAt, String fromStatus, String toStatus, String triggerEvent) {
        static TraceView from(com.fund.trade.domain.repository.OrderTraceRepository.OrderTraceEntry entry) {
            return new TraceView(entry.occurredAt(), entry.fromStatus(),
                    entry.toStatus(), entry.triggerEvent());
        }
    }

    /** 申购单视图 DTO */
    public record SubscriptionOrderView(String orderId, String customerId, String productId,
                                         BigDecimal subscriptionAmount, String tDay, String status,
                                         BigDecimal confirmedNetValue, BigDecimal confirmedShares,
                                         BigDecimal confirmedFee, String failReason,
                                         List<TraceView> traces) {
        static SubscriptionOrderView from(SubscriptionOrder order, List<TraceView> traces) {
            return new SubscriptionOrderView(order.getOrderId(), order.getCustomerId(),
                    order.getProductId(), order.getSubscriptionAmount().value(),
                    order.getTDay().date().toString(), order.getStatus().name(),
                    order.getConfirmedNetValue() == null ? null : order.getConfirmedNetValue().value(),
                    order.getConfirmedShares() == null ? null : order.getConfirmedShares().value(),
                    order.getConfirmedFee() == null ? null : order.getConfirmedFee().value(),
                    order.getFailReason(), traces);
        }
    }

    /** 赎回单视图 DTO */
    public record RedemptionOrderView(String orderId, String customerId, String productId,
                                       BigDecimal redemptionShares, String tDay, String status,
                                       BigDecimal confirmedNetValue, BigDecimal redemptionAmount,
                                       BigDecimal redemptionFee, String failReason,
                                       List<TraceView> traces) {
        static RedemptionOrderView from(RedemptionOrder order, List<TraceView> traces) {
            return new RedemptionOrderView(order.getOrderId(), order.getCustomerId(),
                    order.getProductId(), order.getRedemptionShares().value(),
                    order.getTDay().date().toString(), order.getStatus().name(),
                    order.getConfirmedNetValue() == null ? null : order.getConfirmedNetValue().value(),
                    order.getRedemptionAmount() == null ? null : order.getRedemptionAmount().value(),
                    order.getRedemptionFee() == null ? null : order.getRedemptionFee().value(),
                    order.getFailReason(), traces);
        }
    }

    /** 申购单分页视图 DTO */
    public record SubscriptionPageView(List<SubscriptionOrderView> list, long total) {
    }

    /** 赎回单分页视图 DTO */
    public record RedemptionPageView(List<RedemptionOrderView> list, long total) {
    }
}
