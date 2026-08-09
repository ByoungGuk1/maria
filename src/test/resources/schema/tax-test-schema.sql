CREATE TABLE tax_rule (
    rule_id    BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_type  VARCHAR(20)   NOT NULL,
    rule_value DECIMAL(15,4) NOT NULL,
    valid_from DATE          NOT NULL,
    valid_to   DATE          NOT NULL,
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(30)
);

CREATE TABLE inbound (
    inbound_id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_id                 BIGINT        NOT NULL,
    requested_qty              DECIMAL(15,4) NOT NULL,
    current_holding_at_request DECIMAL(15,4),
    approved_qty               DECIMAL(15,4) NOT NULL,
    processed_at               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE inbound_detail (
    inbound_detail_id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    inbound_id                BIGINT        NOT NULL,
    foreign_product_id        BIGINT        NOT NULL,
    source_broker             VARCHAR(20),
    source_general_account_id BIGINT,
    recorded_at               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    purchase_date             DATETIME      NOT NULL,
    purchase_price            DECIMAL(15,4) NOT NULL,
    purchase_currency         VARCHAR(10)   NOT NULL,
    purchase_fx_rate          DECIMAL(15,4) NOT NULL,
    qty                       DECIMAL(15,4) NOT NULL,
    current_qty               DECIMAL(15,4) NOT NULL
);

CREATE TABLE sell_order (
    order_id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    inbound_detail_id  BIGINT,
    sell_qty           DECIMAL(15,4) NOT NULL,
    base_price         DECIMAL(15,4) NOT NULL,
    processed_at       DATETIME,
    status             VARCHAR(10)   NOT NULL,
    settlement_fx_rate DECIMAL(15,4)
);

CREATE TABLE krw_exchange (
    exchange_id        BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_id         BIGINT        NOT NULL,
    order_id           BIGINT        NOT NULL,
    provisional_amount DECIMAL(15,2) NOT NULL,
    provisional_at     DATETIME      NOT NULL,
    final_rate         DECIMAL(15,6),
    final_amount       DECIMAL(15,0),
    final_at           DATETIME,
    settlement_status  VARCHAR(12)   NOT NULL
);
