package com.fund.trade.domain.model.position;

import com.fund.trade.domain.valueobject.Share;

import java.util.Objects;

/**
 * 持仓聚合根（TA 账户上下文）：每客户每基金一条持仓记录。
 * <p>核心不变量：</p>
 * <ul>
 *   <li>总份额 = 可用份额 + 冻结份额（availableShares 为派生值）；</li>
 *   <li>冻结份额不可超过可用份额；解冻份额不可超过已冻结份额；</li>
 *   <li>各份额均非负。</li>
 * </ul>
 */
public class SharePosition {

    private final String taAccountId;
    private final String customerId;
    private final String productId;
    private Share totalShares;
    private Share frozenShares;

    /** 开立零持仓账户 */
    public SharePosition(String taAccountId, String customerId, String productId) {
        this(taAccountId, customerId, productId, Share.zero(), Share.zero());
    }

    /** 全参构造（含已有份额，供仓储层重建聚合使用） */
    public SharePosition(String taAccountId, String customerId, String productId,
                         Share totalShares, Share frozenShares) {
        this.taAccountId = Objects.requireNonNull(taAccountId, "TA账户ID不能为空");
        this.customerId = Objects.requireNonNull(customerId, "客户ID不能为空");
        this.productId = Objects.requireNonNull(productId, "产品ID不能为空");
        this.totalShares = Objects.requireNonNull(totalShares, "总份额不能为空");
        this.frozenShares = Objects.requireNonNull(frozenShares, "冻结份额不能为空");
        validateInvariant();
    }

    /** 可用份额 = 总份额 − 冻结份额 */
    public Share availableShares() {
        return totalShares.subtract(frozenShares);
    }

    /** 冻结份额（赎回下单时调用） */
    public void freeze(Share shares) {
        requirePositive(shares, "冻结份额");
        if (shares.compareTo(availableShares()) > 0) {
            throw new IllegalStateException("可用份额不足");
        }
        this.frozenShares = frozenShares.add(shares);
    }

    /** 解冻份额（赎回确认失败时恢复可用） */
    public void unfreeze(Share shares) {
        requirePositive(shares, "解冻份额");
        if (shares.compareTo(frozenShares) > 0) {
            throw new IllegalStateException("解冻份额不能超过已冻结份额");
        }
        this.frozenShares = frozenShares.subtract(shares);
    }

    /** 份额入账（申购确认成功时增加总份额） */
    public void increase(Share shares) {
        requirePositive(shares, "入账份额");
        this.totalShares = totalShares.add(shares);
    }

    /** 赎回确认成功：扣减总份额并释放冻结份额 */
    public void decreaseAndUnfreeze(Share shares) {
        requirePositive(shares, "赎回扣减份额");
        if (shares.compareTo(frozenShares) > 0) {
            throw new IllegalStateException("赎回扣减份额不能超过已冻结份额");
        }
        this.totalShares = totalShares.subtract(shares);
        this.frozenShares = frozenShares.subtract(shares);
        validateInvariant();
    }

    private void requirePositive(Share shares, String label) {
        Objects.requireNonNull(shares, label + "不能为空");
        if (!shares.isPositive()) {
            throw new IllegalArgumentException(label + "必须大于 0");
        }
    }

    /** 校验不变量：总份额 ≥ 冻结份额 ≥ 0 */
    private void validateInvariant() {
        if (totalShares.compareTo(frozenShares) < 0) {
            throw new IllegalStateException("不变量被破坏：总份额不能小于冻结份额");
        }
    }

    public String getTaAccountId() {
        return taAccountId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductId() {
        return productId;
    }

    public Share getTotalShares() {
        return totalShares;
    }

    public Share getFrozenShares() {
        return frozenShares;
    }
}
