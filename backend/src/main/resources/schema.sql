-- 基金申购赎回管理系统表结构（PostgreSQL）
-- 金额/份额/净值/费率均以 TEXT 存储（BigDecimal 字符串），避免浮点精度丢失

CREATE TABLE IF NOT EXISTS fund_product (
    product_id             TEXT PRIMARY KEY,
    product_code           TEXT NOT NULL,
    product_name           TEXT NOT NULL,
    status                 TEXT NOT NULL,
    min_subscription_amount TEXT NOT NULL,
    subscription_fee_rate  TEXT NOT NULL,
    risk_level             TEXT NOT NULL,
    redemption_fee_rules   TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS funds_account (
    account_id    TEXT PRIMARY KEY,
    customer_id   TEXT NOT NULL UNIQUE,
    balance       TEXT NOT NULL,
    frozen_amount TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS share_position (
    ta_account_id    TEXT PRIMARY KEY,
    customer_id      TEXT NOT NULL,
    product_id       TEXT NOT NULL,
    total_shares     TEXT NOT NULL,
    frozen_shares    TEXT NOT NULL,
    last_credit_date TEXT,
    UNIQUE (customer_id, product_id)
);

CREATE TABLE IF NOT EXISTS subscription_order (
    order_id            TEXT PRIMARY KEY,
    customer_id         TEXT NOT NULL,
    product_id          TEXT NOT NULL,
    subscription_amount TEXT NOT NULL,
    t_day               TEXT NOT NULL,
    status              TEXT NOT NULL,
    confirmed_net_value TEXT,
    confirmed_shares    TEXT,
    confirmed_fee       TEXT,
    fail_reason         TEXT
);

CREATE TABLE IF NOT EXISTS redemption_order (
    order_id            TEXT PRIMARY KEY,
    customer_id         TEXT NOT NULL,
    product_id          TEXT NOT NULL,
    redemption_shares   TEXT NOT NULL,
    t_day               TEXT NOT NULL,
    status              TEXT NOT NULL,
    confirmed_net_value TEXT,
    redemption_amount   TEXT,
    redemption_fee      TEXT,
    fail_reason         TEXT
);

CREATE TABLE IF NOT EXISTS net_value (
    product_id TEXT NOT NULL,
    nav_date   TEXT NOT NULL,
    nav        TEXT NOT NULL,
    PRIMARY KEY (product_id, nav_date)
);

CREATE TABLE IF NOT EXISTS trade_calendar_holiday (
    holiday TEXT PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS order_status_trace (
    id            BIGSERIAL PRIMARY KEY,
    order_id      TEXT NOT NULL,
    occurred_at   TEXT NOT NULL,
    from_status   TEXT,
    to_status     TEXT NOT NULL,
    trigger_event TEXT NOT NULL
);
