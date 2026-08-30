package com.fund.trade.api;

import com.fund.trade.application.ConfirmationAppService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 管理端接口（估值/交易上下文）：净值发布、T+1 确认批处理。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ConfirmationAppService confirmationAppService;

    public AdminController(ConfirmationAppService confirmationAppService) {
        this.confirmationAppService = confirmationAppService;
    }

    /** POST /api/admin/net-values：发布某产品某日净值 */
    @PostMapping("/net-values")
    public ApiResponse<Void> publishNetValue(@RequestBody PublishNetValueRequest request) {
        LocalDate navDate = parseDate(request.navDate());
        confirmationAppService.publishNetValue(request.productId(), navDate, request.nav());
        return ApiResponse.ok();
    }

    /** POST /api/admin/confirmations/run：触发指定 T 日的 T+1 确认批处理 */
    @PostMapping("/confirmations/run")
    public ApiResponse<ConfirmationSummaryView> runConfirmations(
            @RequestBody RunConfirmationRequest request) {
        LocalDate tDay = parseDate(request.tDay());
        ConfirmationAppService.ConfirmationSummary summary =
                confirmationAppService.runConfirmations(tDay);
        return ApiResponse.ok(ConfirmationSummaryView.from(summary));
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            throw new com.fund.trade.application.BusinessException(
                    com.fund.trade.application.ErrorCode.PARAM_INVALID, "日期不能为空");
        }
        try {
            return LocalDate.parse(date);
        } catch (IllegalArgumentException e) {
            throw new com.fund.trade.application.BusinessException(
                    com.fund.trade.application.ErrorCode.PARAM_INVALID, "日期格式不合法：" + date);
        }
    }

    /** 净值发布请求 DTO */
    public record PublishNetValueRequest(String productId, String navDate, String nav) {
    }

    /** 确认批处理请求 DTO */
    public record RunConfirmationRequest(String tDay) {
    }

    /** 确认批处理结果视图 DTO */
    public record ConfirmationSummaryView(int subscriptionConfirmed, int subscriptionFailed,
                                           int redemptionConfirmed, int redemptionFailed) {
        static ConfirmationSummaryView from(ConfirmationAppService.ConfirmationSummary summary) {
            return new ConfirmationSummaryView(summary.subscriptionConfirmed(),
                    summary.subscriptionFailed(), summary.redemptionConfirmed(),
                    summary.redemptionFailed());
        }
    }
}
