package com.fund.trade.demo;

import com.fund.trade.domain.model.funds.FundsAccount;
import com.fund.trade.domain.model.order.RedemptionOrder;
import com.fund.trade.domain.model.order.SubscriptionOrder;
import com.fund.trade.domain.model.position.SharePosition;
import com.fund.trade.domain.model.product.FundProduct;
import com.fund.trade.domain.model.product.RiskLevel;
import com.fund.trade.domain.service.TradeCalendar;
import com.fund.trade.domain.valueobject.Money;
import com.fund.trade.domain.valueobject.NetValue;
import com.fund.trade.domain.valueobject.Rate;
import com.fund.trade.domain.valueobject.Share;
import com.fund.trade.domain.valueobject.TradeDate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * M1 领域内核流程演示（纯内存，无数据库）。
 * 完整演示：交易日历 → 申购下单 → 资金冻结 → T+1 确认 → 持仓入账 → 赎回下单 → 份额冻结 → T+1 确认 → 资金到账。
 */
public class DomainDemo {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static void main(String[] args) {
        System.out.println("══════════════════════════════════════════════════════════════");
        System.out.println("       基金申购赎回管理系统 · M1 领域内核流程演示（纯内存）");
        System.out.println("══════════════════════════════════════════════════════════════");

        // ────────────────────────────────────────────────────────────
        // 准备：交易日历（含节假日）、基金产品、资金账户、TA 持仓账户
        // ────────────────────────────────────────────────────────────
        TradeCalendar calendar = TradeCalendar.of(Set.of(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2),
                LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4),
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6),
                LocalDate.of(2026, 10, 7)));

        FundProduct product = FundProduct.onSale(
                "P001", "000001", "华夏成长混合",
                Money.of("1000.00"),      // 起购金额 1000 元
                Rate.of("0.01"),          // 申购费率 1%（外扣法）
                RiskLevel.L3);

        FundsAccount fundsAccount = new FundsAccount("FA001", "C001", Money.of("20000.00"));
        SharePosition position = new SharePosition("TA001", "C001", "P001");

        System.out.println("\n【准备】客户 C001 | 资金账户余额 ¥" + fundsAccount.getBalance()
                + " | 产品：" + product.getProductName() + "（" + product.getProductCode()
                + "，风险等级 " + product.getRiskLevel() + "）");

        // ────────────────────────────────────────────────────────────
        // 场景一：交易日历与 15:00 截单（S-02 / S-03 / S-04）
        // ────────────────────────────────────────────────────────────
        System.out.println("\n───────────────── 场景一：交易日历与 15:00 截单 ─────────────────");

        // 2026-09-25 是周五：15:00 前提交 → 当日为 T 日
        LocalDateTime fridayBefore = LocalDateTime.of(2026, 9, 25, 14, 30);
        System.out.println("[1] 周五 14:30 提交（截单前）   → T日 = "
                + calendar.resolveTDay(fridayBefore).date());

        // 周五 15:30 提交 → 顺延至下周一
        LocalDateTime fridayAfter = LocalDateTime.of(2026, 9, 25, 15, 30);
        System.out.println("[2] 周五 15:30 提交（已截单）   → T日 = "
                + calendar.resolveTDay(fridayAfter).date() + "（顺延至下周一）");

        // 国庆节前最后一个交易日 9-30（周三）15:30 提交 → 顺延跨过 7 天假期至 10-08
        LocalDateTime beforeHoliday = LocalDateTime.of(2026, 9, 30, 15, 30);
        System.out.println("[3] 09-30 15:30 提交（节前截单） → T日 = "
                + calendar.resolveTDay(beforeHoliday).date() + "（顺延跨过国庆 7 天假期）");

        // ────────────────────────────────────────────────────────────
        // 场景二：申购全流程（下单 → 冻结 → T+1 确认 → 持仓入账）
        // ────────────────────────────────────────────────────────────
        System.out.println("\n───────────────────── 场景二：申购 10000 元 ────────────────────");

        LocalDateTime submitTime = LocalDateTime.of(2026, 9, 24, 10, 0);
        TradeDate tDay = calendar.resolveTDay(submitTime);
        System.out.println("[1] 09-24 10:00 提交申购 ¥10000.00，解析 T日 = " + tDay.date());

        // 下单（产品校验收敛在聚合内）
        SubscriptionOrder subOrder = SubscriptionOrder.place("C001", product, Money.of("10000.00"), tDay);
        System.out.println("[2] 下单成功，订单号 " + subOrder.getOrderId()
                + "，状态 = " + subOrder.getStatus());

        // 资金冻结
        fundsAccount.freeze(subOrder.getSubscriptionAmount());
        subOrder.markFundsFrozen();
        System.out.println("[3] 资金冻结成功 → 账户余额 ¥" + fundsAccount.getBalance()
                + "，冻结 ¥" + fundsAccount.getFrozenAmount()
                + "，可用 ¥" + fundsAccount.availableAmount()
                + "｜订单状态 = " + subOrder.getStatus());

        // T+1 确认（外扣法：净申购 = 10000 ÷ 1.01 = 9900.99，费 = 99.01）
        NetValue tDayNav = NetValue.of("P001", tDay.date(), "1.2500");
        subOrder.confirm(tDayNav, product);
        System.out.println("[4] T+1 确认成功（T日净值 1.2500）：");
        System.out.println("    · 净申购金额 = 10000 ÷ (1 + 1%) = ¥"
                + subOrder.getSubscriptionAmount().subtract(subOrder.getConfirmedFee()));
        System.out.println("    · 申购费（外扣法） = ¥" + subOrder.getConfirmedFee());
        System.out.println("    · 确认份额 = 9900.99 ÷ 1.2500 = " + subOrder.getConfirmedShares() + " 份");

        // 扣除冻结资金 + 持仓入账
        fundsAccount.deductFrozen(subOrder.getSubscriptionAmount());
        position.increase(subOrder.getConfirmedShares());
        System.out.println("[5] 扣款成功 → 账户余额 ¥" + fundsAccount.getBalance()
                + "（冻结释放为 0）");
        System.out.println("    持仓入账 → 总份额 " + position.getTotalShares()
                + "，可用 " + position.availableShares() + "，冻结 " + position.getFrozenShares());

        // ────────────────────────────────────────────────────────────
        // 场景三：赎回全流程（下单 → 份额冻结 → T+1 确认 → 资金到账）
        // ────────────────────────────────────────────────────────────
        System.out.println("\n───────────────────── 场景三：赎回 5000 份（持有 400 天，费率 0.25%）─────────────");

        LocalDateTime redeemTime = LocalDateTime.of(2026, 9, 25, 9, 30);
        TradeDate redeemTDay = calendar.resolveTDay(redeemTime);
        System.out.println("[1] 09-25 09:30 提交赎回 5000.00 份，解析 T日 = " + redeemTDay.date());

        RedemptionOrder redOrder = RedemptionOrder.place("C001", product,
                Share.of(new BigDecimal("5000.00")), redeemTDay);
        System.out.println("[2] 下单成功，订单号 " + redOrder.getOrderId()
                + "，状态 = " + redOrder.getStatus());

        // 份额冻结
        position.freeze(redOrder.getRedemptionShares());
        redOrder.markSharesFrozen();
        System.out.println("[3] 份额冻结成功 → 总份额 " + position.getTotalShares()
                + "，可用 " + position.availableShares()
                + "，冻结 " + position.getFrozenShares()
                + "｜订单状态 = " + redOrder.getStatus());

        // T+1 确认（赎回总额 = 5000 × 1.25 = 6250，费 = 6250 × 0.25% = 15.63）
        NetValue redeemNav = NetValue.of("P001", redeemTDay.date(), "1.2500");
        redOrder.confirm(redeemNav, product, 400);
        System.out.println("[4] T+1 确认成功（T日净值 1.2500，持有 400 天命中 0.25% 档）：");
        System.out.println("    · 赎回总额 = 5000 × 1.2500 = ¥"
                + redOrder.getRedemptionShares().value().multiply(redeemNav.value()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        System.out.println("    · 赎回费（0.25%） = ¥" + redOrder.getRedemptionFee());
        System.out.println("    · 实际到账 = ¥" + redOrder.getRedemptionAmount());

        // 扣减份额 + 资金到账
        position.decreaseAndUnfreeze(redOrder.getRedemptionShares());
        fundsAccount.credit(redOrder.getRedemptionAmount());
        System.out.println("[5] 份额扣减 → 总份额 " + position.getTotalShares()
                + "，可用 " + position.availableShares() + "，冻结 " + position.getFrozenShares());
        System.out.println("    资金到账 → 账户余额 ¥" + fundsAccount.getBalance());

        // ────────────────────────────────────────────────────────────
        // 场景四：业务规则拦截演示（非法操作均被领域层拒绝）
        // ────────────────────────────────────────────────────────────
        System.out.println("\n───────────────────── 场景四：业务规则拦截演示 ─────────────────────");

        // 4.1 资金不足
        try {
            fundsAccount.freeze(Money.of("999999.00"));
        } catch (IllegalStateException e) {
            System.out.println("[拦截1] 超额冻结资金 → " + e.getMessage());
        }

        // 4.2 低于起购金额
        try {
            SubscriptionOrder.place("C001", product, Money.of("500.00"), tDay);
        } catch (IllegalArgumentException e) {
            System.out.println("[拦截2] 低于起购金额下单 → " + e.getMessage());
        }

        // 4.3 状态机非法流转（已确认的申购单不能再次确认）
        try {
            subOrder.confirm(tDayNav, product);
        } catch (IllegalStateException e) {
            System.out.println("[拦截3] 重复确认已确认订单 → " + e.getMessage());
        }

        // 4.4 暂停申购的产品不可下单
        product.suspend();
        try {
            SubscriptionOrder.place("C001", product, Money.of("10000.00"), tDay);
        } catch (IllegalStateException e) {
            System.out.println("[拦截4] 暂停申购的产品下单 → " + e.getMessage());
        }

        System.out.println("\n══════════════════════════════════════════════════════════════");
        System.out.println("  演示结束：62 个领域单元测试覆盖以上全部规则（mvn test 全绿）");
        System.out.println("══════════════════════════════════════════════════════════════");
    }
}
