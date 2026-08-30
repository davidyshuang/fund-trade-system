package com.fund.trade.api;

import com.fund.trade.application.BusinessException;
import com.fund.trade.application.ErrorCode;
import com.fund.trade.application.QueryAppService;
import com.fund.trade.domain.model.funds.FundsAccount;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 客户资产查询接口（客户/资金/TA 账户上下文 - 客户端）：持仓查询、资金账户查询。
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final QueryAppService queryAppService;

    public CustomerController(QueryAppService queryAppService) {
        this.queryAppService = queryAppService;
    }

    /** GET /api/customers/{customerId}/positions：客户全部持仓 */
    @GetMapping("/{customerId}/positions")
    public ApiResponse<List<QueryAppService.PositionView>> positions(@PathVariable String customerId) {
        return ApiResponse.ok(queryAppService.positionsOf(customerId));
    }

    /** GET /api/customers/{customerId}/funds-account：客户资金账户（余额/冻结/可用） */
    @GetMapping("/{customerId}/funds-account")
    public ApiResponse<FundsAccountView> fundsAccount(@PathVariable String customerId) {
        FundsAccount account = queryAppService.fundsAccountOf(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUNDS_ACCOUNT_NOT_FOUND));
        return ApiResponse.ok(FundsAccountView.from(account));
    }

    /** 资金账户视图 DTO */
    public record FundsAccountView(String accountId, String customerId,
                                   BigDecimal balance, BigDecimal frozenAmount,
                                   BigDecimal availableAmount) {
        static FundsAccountView from(FundsAccount account) {
            return new FundsAccountView(account.getAccountId(), account.getCustomerId(),
                    account.getBalance().value(), account.getFrozenAmount().value(),
                    account.availableAmount().value());
        }
    }
}
