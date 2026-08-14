DROP TABLE IF EXISTS tax_snapshot;
DROP TABLE IF EXISTS tax_rule;
DROP TABLE IF EXISTS target_product_judgement;
DROP TABLE IF EXISTS krw_exchange;
DROP TABLE IF EXISTS sell_order;
DROP TABLE IF EXISTS inbound_detail;
DROP TABLE IF EXISTS account;
DROP TABLE IF EXISTS customer;

CREATE TABLE customer (
    customer_id   BIGINT PRIMARY KEY AUTO_INCREMENT,
    name          VARCHAR(50)  NOT NULL,
    birth_date    DATE         NOT NULL,
    investor_type VARCHAR(20)  NOT NULL,
    ci_hash       VARCHAR(64)  NOT NULL UNIQUE
);

CREATE TABLE account (
    account_id   BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id  BIGINT        NOT NULL UNIQUE,
    status       VARCHAR(20)   NOT NULL,
    opened_at    DATETIME,
    limit_amount DECIMAL(15, 0) NOT NULL DEFAULT 30000000,
    amount       DECIMAL(15, 0) NOT NULL DEFAULT 0,
    benefit      VARCHAR(12)
);

CREATE TABLE inbound_detail (
    inbound_detail_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    purchase_price    DECIMAL(15, 4) NOT NULL,
    purchase_fx_rate  DECIMAL(15, 4) NOT NULL
);

CREATE TABLE sell_order (
    order_id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    inbound_detail_id BIGINT        NOT NULL,
    sell_qty          DECIMAL(15, 4) NOT NULL,
    base_price        DECIMAL(15, 4) NOT NULL,
    status            VARCHAR(10)   NOT NULL,
    processed_at      DATETIME      NOT NULL
);

CREATE TABLE krw_exchange (
    exchange_id       BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_id        BIGINT        NOT NULL,
    order_id          BIGINT        NOT NULL,
    final_amount      DECIMAL(15, 0),
    settlement_status VARCHAR(12)   NOT NULL
);

CREATE TABLE target_product_judgement (
    judgement_id    BIGINT PRIMARY KEY AUTO_INCREMENT,
    mydata_trade_id BIGINT        NOT NULL UNIQUE,
    ci_hash         VARCHAR(64)   NOT NULL,
    is_target       BOOLEAN       NOT NULL,
    judged_at       DATETIME      NOT NULL,
    trade_date      DATE          NOT NULL,
    net_buy_amount  DECIMAL(15, 2) NOT NULL
);

CREATE TABLE tax_rule (
    rule_id    BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_type  VARCHAR(20)   NOT NULL,
    rule_value DECIMAL(15, 4) NOT NULL,
    valid_from DATE          NOT NULL,
    valid_to   DATE          NOT NULL
);

CREATE TABLE tax_snapshot (
    snapshot_id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_id               BIGINT        NOT NULL,
    calculated_at            DATETIME      NOT NULL,
    weighted_sell            DECIMAL(15, 2) NOT NULL,
    original_gain_amount     DECIMAL(15, 2) NOT NULL,
    weighted_gain            DECIMAL(15, 2) NOT NULL,
    weighted_external_amount DECIMAL(15, 2) NOT NULL,
    adjust_ratio             DECIMAL(7, 4) NOT NULL,
    final_deduction          DECIMAL(15, 2) NOT NULL,
    final_tax                DECIMAL(15, 2) NOT NULL,
    CONSTRAINT uk_tax_snapshot__account UNIQUE (account_id)
);
