package com.fund.trade.domain.model.funds;

import com.fund.trade.domain.valueobject.Money;

import java.util.Objects;

/**
 * 资金账户聚合根（资金上下文）。
 * <p>核心不变量：</p>
 * <ul>
 *   <li>可用余额 = 总余额 − 冻结金额（availableAmount 为派生值）；</li>
 *   <li>冻结金额不可超过可用余额；解冻 / 扣款不可超过已冻结金额；</li>
 *   <li>金额均非负。</li>
 * </ul>
 */
public class FundsAccount {

    private final String accountId;
    private final String customerId;
    private Money balance;
    private Money frozenAmount;

    /** 开立账户（含初始余额，冻结金额为零） */
    public FundsAccount(String accountId, String customerId, Money initialBalance) {
        this(accountId, customerId, initialBalance, Money.zero());
    }

    /** 全参构造（含已冻结金额，供仓储层重建聚合使用） */
    public FundsAccount(String accountId, String customerId, Money balance, Money frozenAmount) {
        this.accountId = Objects.requireNonNull(accountId, "资金账户ID不能为空");
        this.customerId = Objects.requireNonNull(customerId, "客户ID不能为空");
        this.balance = Objects.requireNonNull(balance, "总余额不能为空");
        this.frozenAmount = Objects.requireNonNull(frozenAmount, "冻结金额不能为空");
        validateInvariant();
    }

    /** 可用余额 = 总余额 − 冻结金额 */
    public Money availableAmount() {
        return balance.subtract(frozenAmount);
    }

    /** 冻结资金（申购下单时调用） */
    public void freeze(Money amount) {
        requirePositive(amount, "冻结金额");
        if (amount.compareTo(availableAmount()) > 0) {
            throw new IllegalStateException("资金不足");
        }
        this.frozenAmount = frozenAmount.add(amount);
    }

    /** 解冻资金（申购确认失败时退回可用） */
    public void unfreeze(Money amount) {
        requirePositive(amount, "解冻金额");
        if (amount.compareTo(frozenAmount) > 0) {
            throw new IllegalStateException("解冻金额不能超过已冻结金额");
        }
        this.frozenAmount = frozenAmount.subtract(amount);
    }

    /** 扣除冻结资金（申购确认成功时实际扣款） */
    public void deductFrozen(Money amount) {
        requirePositive(amount, "扣款金额");
        if (amount.compareTo(frozenAmount) > 0) {
            throw new IllegalStateException("扣款金额不能超过已冻结金额");
        }
        this.balance = balance.subtract(amount);
        this.frozenAmount = frozenAmount.subtract(amount);
        validateInvariant();
    }

    /** 资金入账（赎回确认成功时赎回款到账） */
    public void credit(Money amount) {
        requirePositive(amount, "入账金额");
        this.balance = balance.add(amount);
    }

    private void requirePositive(Money amount, String label) {
        Objects.requireNonNull(amount, label + "不能为空");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException(label + "必须大于 0");
        }
    }

    /** 校验不变量：总余额 ≥ 冻结金额 ≥ 0 */
    private void validateInvariant() {
        if (balance.compareTo(frozenAmount) < 0) {
            throw new IllegalStateException("不变量被破坏：总余额不能小于冻结金额");
        }
    }

    public String getAccountId() {
        return accountId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public Money getBalance() {
        return balance;
    }

    public Money getFrozenAmount() {
        return frozenAmount;
    }
}
