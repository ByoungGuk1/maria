-- =====================================================================
-- RIA 관리자 시스템 DDL (MariaDB / MySQL 문법)
-- ERDCloud export를 실행 가능한 DDL로 변환
-- 시스템 경계(§8-8): myData/증권사 참조는 값만 저장, FK 없음
-- 변경: domestic_stock_balance를 증권사 -> RIA로 이동(account/domestic_product FK 복구)
-- =====================================================================

CREATE DATABASE IF NOT EXISTS ria_admin DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE ria_admin;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS admin_user;
DROP TABLE IF EXISTS settlement_batch_guard;
DROP TABLE IF EXISTS settlement_item;
DROP TABLE IF EXISTS settlement_batch;
DROP TABLE IF EXISTS tax_calculation;
DROP TABLE IF EXISTS tax_rule;
DROP TABLE IF EXISTS withdrawal_allocation;
DROP TABLE IF EXISTS withdrawal;
DROP TABLE IF EXISTS left_amount;
DROP TABLE IF EXISTS krw_exchange;
DROP TABLE IF EXISTS sell_order;
DROP TABLE IF EXISTS outbound;
DROP TABLE IF EXISTS inbound_min;
DROP TABLE IF EXISTS inbound_detail;
DROP TABLE IF EXISTS inbound;
DROP TABLE IF EXISTS domestic_stock_balance;
DROP TABLE IF EXISTS domestic_product;
DROP TABLE IF EXISTS foreign_product;
DROP TABLE IF EXISTS system_clock;
DROP TABLE IF EXISTS account_benefit_log;
DROP TABLE IF EXISTS account_status_log;
DROP TABLE IF EXISTS account;
DROP TABLE IF EXISTS customer_auth_password;
DROP TABLE IF EXISTS customer_auth;
DROP TABLE IF EXISTS customer;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- 고객 / 인증
-- ---------------------------------------------------------------------
CREATE TABLE customer (
    customer_id   BIGINT      NOT NULL AUTO_INCREMENT,
    name          VARCHAR(50) NOT NULL COMMENT '이름(표시/감사용, 해시 입력 아님)',
    birth_date    DATE        NOT NULL COMMENT '생년월일',
    phone         VARCHAR(20) NULL     COMMENT '연락처',
    investor_type VARCHAR(20) NOT NULL COMMENT '투자자유형',
    ci_hash       VARCHAR(64) NOT NULL COMMENT 'HMAC-SHA256(정규화 주민번호 13자리, 공유 PEPPER). 원본 미저장. 3개 시스템 공유키(CI 역할)',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '가입일시',
    PRIMARY KEY (customer_id),
    UNIQUE KEY uk_customer_ci_hash (ci_hash),
    CONSTRAINT chk_customer_investor_type CHECK (investor_type IN ('STABLE','CONSERVATIVE','NEUTRAL','ACTIVE','AGGRESSIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RIA 고객';

CREATE TABLE customer_auth (
    auth_id       BIGINT      NOT NULL AUTO_INCREMENT,
    customer_id   BIGINT      NOT NULL,
    login_id      VARCHAR(50) NOT NULL COMMENT '로그인ID',
    type          VARCHAR(10) NOT NULL COMMENT 'LOCAL/GOOGLE/KAKAO/NAVER',
    last_login_at DATETIME    NULL     COMMENT '마지막 로그인 일시',
    PRIMARY KEY (auth_id),
    CONSTRAINT chk_customer_auth_type CHECK (type IN ('LOCAL','GOOGLE','KAKAO','NAVER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='고객 인증수단';

CREATE TABLE customer_auth_password (
    auth_password_id BIGINT       NOT NULL AUTO_INCREMENT,
    auth_id          BIGINT       NOT NULL,
    password         VARCHAR(255) NOT NULL COMMENT '사용자 비밀번호(해시)',
    PRIMARY KEY (auth_password_id),
    UNIQUE KEY uk_cap_auth_id (auth_id) COMMENT 'type=LOCAL 1:1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LOCAL 계정 비밀번호(1:1)';

-- ---------------------------------------------------------------------
-- 계좌
-- ---------------------------------------------------------------------
CREATE TABLE account (
    account_id   BIGINT        NOT NULL AUTO_INCREMENT,
    customer_id  BIGINT        NOT NULL COMMENT '1인 1계좌 - unique',
    status       VARCHAR(20)   NOT NULL COMMENT 'APPLIED/OPENED/REJECTED/CLOSURE_REQUESTED/CLOSED',
    opened_at    DATETIME      NULL     COMMENT '개설일',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신청일',
    account_no   VARCHAR(10)   NULL     COMMENT '계좌번호(개설 후 부여)',
    limit_amount DECIMAL(15,0) NOT NULL COMMENT '고객 설정 매도한도(전 금융기관 합산 5천만원 이내)',
    amount       DECIMAL(15,0) NOT NULL DEFAULT 0 COMMENT '보유중인 금액(원화)',
    benefit      VARCHAR(12)   NULL     COMMENT '세제혜택: POSSIBLE/IMPOSSIBLE/REDUCED',
    last_domestic_trade_id BIGINT NULL COMMENT '국내거래 pull 커서(마지막 반영한 증권사 domestic_trade.trade_id)',
    PRIMARY KEY (account_id),
    UNIQUE KEY uk_account_customer_id (customer_id),
    CONSTRAINT chk_account_status  CHECK (status IN ('APPLIED','OPENED','REJECTED','CLOSURE_REQUESTED','CLOSED')),
    CONSTRAINT chk_account_benefit CHECK (benefit IN ('POSSIBLE','IMPOSSIBLE','REDUCED')),
    CONSTRAINT chk_account_limit   CHECK (limit_amount > 0 AND limit_amount <= 50000000),
    CONSTRAINT chk_account_no      CHECK (account_no IS NULL OR account_no REGEXP '^[0-9]{10}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RIA 계좌';

CREATE TABLE account_status_log (
    log_id      BIGINT       NOT NULL AUTO_INCREMENT,
    account_id  BIGINT       NOT NULL,
    prev_status VARCHAR(20)  NULL     COMMENT '이전상태',
    new_status  VARCHAR(20)  NOT NULL COMMENT '변경상태',
    changed_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '변경일시',
    reason      VARCHAR(200) NULL     COMMENT '변경사유',
    PRIMARY KEY (log_id),
    CONSTRAINT chk_asl_prev CHECK (prev_status IN ('APPLIED','OPENED','REJECTED','CLOSURE_REQUESTED','CLOSED')),
    CONSTRAINT chk_asl_new  CHECK (new_status  IN ('APPLIED','OPENED','REJECTED','CLOSURE_REQUESTED','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='계좌 상태 변경 이력';

CREATE TABLE account_benefit_log (
    benefit_id  BIGINT       NOT NULL AUTO_INCREMENT,
    account_id  BIGINT       NOT NULL,
    prev_status VARCHAR(12)  NULL     COMMENT '이전 상태',
    new_status  VARCHAR(12)  NOT NULL COMMENT '신규 상태',
    changed_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '변경일시',
    reason      VARCHAR(200) NULL     COMMENT '변경 사유',
    PRIMARY KEY (benefit_id),
    CONSTRAINT chk_abl_prev CHECK (prev_status IN ('POSSIBLE','IMPOSSIBLE','REDUCED')),
    CONSTRAINT chk_abl_new  CHECK (new_status  IN ('POSSIBLE','IMPOSSIBLE','REDUCED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='세제혜택 상태 변경 이력';

-- ---------------------------------------------------------------------
-- 시스템 시계
-- ---------------------------------------------------------------------
CREATE TABLE system_clock (
    clock_id         BIGINT   NOT NULL AUTO_INCREMENT,
    current_datetime DATETIME NOT NULL COMMENT '시스템이 지금으로 간주하는 시각. 항상 1행, UPDATE로만 갱신',
    PRIMARY KEY (clock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='주입 Clock 원천(1행)';

-- ---------------------------------------------------------------------
-- 시세 마스터
-- ---------------------------------------------------------------------
CREATE TABLE foreign_product (
    foreign_product_id BIGINT       NOT NULL AUTO_INCREMENT,
    ticker             VARCHAR(20)  NOT NULL COMMENT '종목코드',
    name               VARCHAR(100) NOT NULL COMMENT '종목명',
    market             VARCHAR(50)  NULL     COMMENT '거래소',
    currency           VARCHAR(10)  NULL     COMMENT '거래통화',
    type               VARCHAR(15)  NOT NULL COMMENT 'FOREIGN_STOCK/ETF/ETN',
    PRIMARY KEY (foreign_product_id),
    CONSTRAINT chk_foreign_product_type CHECK (type IN ('FOREIGN_STOCK','ETF','ETN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='해외 종목 마스터';

CREATE TABLE domestic_product (
    domestic_product_id BIGINT       NOT NULL AUTO_INCREMENT,
    ticker              VARCHAR(20)  NOT NULL COMMENT '종목코드',
    name                VARCHAR(100) NOT NULL COMMENT '종목명',
    market              VARCHAR(50)  NULL     COMMENT '거래소',
    type                VARCHAR(15)  NOT NULL COMMENT 'STOCK/FUND',
    domestic_stock_ratio DECIMAL(5,2) NULL COMMENT '국내주식 비중%(FUND만). RIA 편입 80% 판정. STOCK은 NULL',
    inception_date       DATE         NULL COMMENT '설정일(FUND만). 1개월 경과 판정. STOCK은 NULL',
    PRIMARY KEY (domestic_product_id),
    CONSTRAINT chk_domestic_product_type CHECK (type IN ('STOCK','FUND'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='국내 종목 마스터';

-- ---------------------------------------------------------------------
-- RIA 계좌 내 국내주식 잔고 (증권사 -> RIA 이동)
-- ---------------------------------------------------------------------
CREATE TABLE domestic_stock_balance (
    domestic_stock_balance_id BIGINT        NOT NULL AUTO_INCREMENT,
    account_id                BIGINT        NOT NULL COMMENT 'RIA account (동일 시스템, FK 복구)',
    domestic_product_id       BIGINT        NOT NULL COMMENT '해당 국내 종목',
    qty                       DECIMAL(15,4) NOT NULL COMMENT '보유수량',
    status                    VARCHAR(20)   NOT NULL COMMENT 'HOLDING/PARTIALLY_SOLD/SOLD_OUT/TRADE_RESTRICTED/TRADE_SUSPENDED/TERMINATED',
    last_purchase_date        DATETIME      NOT NULL COMMENT '마지막 매수일',
    avg_purchase_price        DECIMAL(15,4) NOT NULL COMMENT '평균매수단가',
    PRIMARY KEY (domestic_stock_balance_id),
    CONSTRAINT chk_dsb_status CHECK (status IN ('HOLDING','PARTIALLY_SOLD','SOLD_OUT','TRADE_RESTRICTED','TRADE_SUSPENDED','TERMINATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RIA 계좌 국내주식 포지션';

-- ---------------------------------------------------------------------
-- 입고 (3단 분리)
-- ---------------------------------------------------------------------
CREATE TABLE inbound (
    inbound_id                 BIGINT        NOT NULL AUTO_INCREMENT,
    account_id                 BIGINT        NOT NULL,
    requested_qty              DECIMAL(15,4) NOT NULL COMMENT 'Min 3번 인자: 고객 신청수량',
    current_holding_at_request DECIMAL(15,4) NULL     COMMENT 'Min 2번 인자: 요청시점 보유수량',
    approved_qty               DECIMAL(15,4) NOT NULL COMMENT 'Min(기준일보유,현재보유,신청) 결과',
    processed_at               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '입고일',
    PRIMARY KEY (inbound_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='입고 요청';

CREATE TABLE inbound_detail (
    inbound_detail_id         BIGINT        NOT NULL AUTO_INCREMENT,
    inbound_id                BIGINT        NOT NULL,
    foreign_product_id        BIGINT        NOT NULL,
    source_broker             VARCHAR(20)   NULL     COMMENT '타사 대체입고 시 출처 증권사명(source_general_account_id와 상호배타)',
    source_general_account_id BIGINT        NULL     COMMENT '당사 일반계좌 출처 시 general_account_id(FK없음). 타사면 NULL. 배당 비율배분 기준',
    recorded_at               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '기록일',
    purchase_date             DATETIME      NOT NULL COMMENT '매수일자',
    purchase_price            DECIMAL(15,4) NOT NULL COMMENT '매수단가',
    purchase_currency         VARCHAR(10)   NOT NULL COMMENT '취득통화',
    purchase_fx_rate          DECIMAL(15,4) NOT NULL COMMENT '매수 결제일 기준환율',
    qty                       DECIMAL(15,4) NOT NULL COMMENT '수량',
    current_qty               DECIMAL(15,4) NOT NULL COMMENT '잔량(애플리케이션에서 초기값 = qty 세팅)',
    PRIMARY KEY (inbound_detail_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='입고 상세(lot)';

CREATE TABLE inbound_min (
    inbound_min_id    BIGINT NOT NULL AUTO_INCREMENT,
    inbound_detail_id BIGINT NOT NULL COMMENT '결과 lot',
    requested_qty     DECIMAL(15,4) NULL COMMENT '요청 수량',
    approved_qty      DECIMAL(15,4) NULL COMMENT '보유중 수량',
    snapshot_qty      DECIMAL(15,4) NULL COMMENT '기준일 수량',
    PRIMARY KEY (inbound_min_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='3-way Min 산식 근거';

CREATE TABLE outbound (
    outbound_id       BIGINT        NOT NULL AUTO_INCREMENT,
    inbound_detail_id BIGINT        NOT NULL,
    qty               DECIMAL(15,4) NOT NULL COMMENT '출고수량',
    reason            VARCHAR(50)   NULL     COMMENT '매도의사철회/한도초과분/배당해외주식 등',
    processed_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (outbound_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='출고';

-- ---------------------------------------------------------------------
-- 매도 / 환전
-- ---------------------------------------------------------------------
CREATE TABLE sell_order (
    order_id          BIGINT        NOT NULL AUTO_INCREMENT,
    inbound_detail_id BIGINT        NOT NULL COMMENT '1 lot당 1건',
    sell_qty          DECIMAL(15,4) NOT NULL COMMENT '매도수량',
    base_price        DECIMAL(15,4) NOT NULL COMMENT '매도기준가(전일종가 x 환율)',
    processed_at      DATETIME      NULL     DEFAULT CURRENT_TIMESTAMP COMMENT '매도결제일',
    status            VARCHAR(10)   NOT NULL COMMENT 'RECEIVED/EXECUTED/REJECTED',
    purchase_fx_rate  DECIMAL(15,4) NULL     COMMENT '매도 결제일 기준환율',
    PRIMARY KEY (order_id),
    UNIQUE KEY uk_sell_order_inbound_detail (inbound_detail_id),
    CONSTRAINT chk_sell_order_status CHECK (status IN ('RECEIVED','EXECUTED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='매도 주문';

CREATE TABLE krw_exchange (
    exchange_id        BIGINT        NOT NULL AUTO_INCREMENT,
    account_id         BIGINT        NOT NULL,
    order_id           BIGINT        NOT NULL,
    provisional_amount DECIMAL(15,2) NOT NULL COMMENT '가환전액',
    provisional_at     DATETIME      NOT NULL COMMENT '가환전일시',
    final_rate         DECIMAL(15,6) NULL     COMMENT '확정환율',
    final_amount       DECIMAL(15,0) NULL     COMMENT '확정환전액(=납입일 기준금액)',
    final_at           DATETIME      NULL     COMMENT '확정일시 - 1년 인출 시계 기준점',
    settlement_status  VARCHAR(12)   NOT NULL COMMENT 'PROVISIONAL/FINALIZED',
    PRIMARY KEY (exchange_id),
    UNIQUE KEY uk_krw_exchange_order_id (order_id),
    CONSTRAINT chk_krw_exchange_status CHECK (settlement_status IN ('PROVISIONAL','FINALIZED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='원화 환전(가정산/확정산)';

-- ---------------------------------------------------------------------
-- 인출
-- ---------------------------------------------------------------------
CREATE TABLE left_amount (
    left_amount_id BIGINT        NOT NULL AUTO_INCREMENT,
    exchange_id    BIGINT        NOT NULL,
    cur_amount     DECIMAL(15,0) NOT NULL COMMENT '인출 후 남은 정산건별 원금(초기값 = krw_exchange.final_amount, 애플리케이션 세팅)',
    PRIMARY KEY (left_amount_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='정산건별 잔여 원금(FIFO)';

CREATE TABLE withdrawal (
    withdrawal_id                  BIGINT        NOT NULL AUTO_INCREMENT,
    account_id                     BIGINT        NOT NULL,
    requested_amount               DECIMAL(15,2) NOT NULL COMMENT '인출요청금액',
    processed_at                   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '인출 요청 일시',
    destination_account_no         VARCHAR(30)   NOT NULL COMMENT '이체 목적지 계좌번호(스냅샷)',
    destination_general_account_id BIGINT        NOT NULL COMMENT '인출 목적지 general_account 참조값(FK없음). 비율배분 없이 단일 선택',
    status                         VARCHAR(12)   NOT NULL DEFAULT 'REQUESTED' COMMENT 'REQUESTED/COMPLETED/CANCELLED/FAILED',
    PRIMARY KEY (withdrawal_id),
    CONSTRAINT chk_withdrawal_status CHECK (status IN ('REQUESTED','COMPLETED','CANCELLED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='인출 요청';

CREATE TABLE withdrawal_allocation (
    allocation_id    BIGINT        NOT NULL AUTO_INCREMENT,
    withdrawal_id    BIGINT        NOT NULL,
    left_amount_id   BIGINT        NULL     COMMENT 'FIFO 대상 정산건',
    allocated_amount DECIMAL(15,2) NOT NULL,
    withdrawal_at    DATETIME      NOT NULL COMMENT '인출 일시',
    type             VARCHAR(30)   NOT NULL COMMENT 'EARNINGS_ONLY/MATURED_PRINCIPAL_INCLUDED/IMMATURE_PRINCIPAL_INCLUDED',
    PRIMARY KEY (allocation_id),
    CONSTRAINT chk_wa_type CHECK (type IN ('EARNINGS_ONLY','MATURED_PRINCIPAL_INCLUDED','IMMATURE_PRINCIPAL_INCLUDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='인출 FIFO 회계 분개';

-- ---------------------------------------------------------------------
-- 세금 계산
-- ---------------------------------------------------------------------
CREATE TABLE tax_rule (
    rule_id    BIGINT        NOT NULL AUTO_INCREMENT,
    rule_type  VARCHAR(20)   NOT NULL COMMENT 'RELIEF_RATE/DEPOSIT_LIMIT/HOLDING_PERIOD',
    rule_value DECIMAL(15,4) NOT NULL,
    valid_from DATE          NOT NULL,
    valid_to   DATE          NOT NULL COMMENT '열린구간은 9999-12-31',
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(30)   NULL,
    PRIMARY KEY (rule_id),
    CONSTRAINT chk_tax_rule_type CHECK (rule_type IN ('RELIEF_RATE','DEPOSIT_LIMIT','HOLDING_PERIOD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='effective-dated 세금 규칙';

CREATE TABLE tax_calculation (
    calc_id       BIGINT        NOT NULL AUTO_INCREMENT,
    account_id    BIGINT        NOT NULL,
    calculated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    basis_type    VARCHAR(20)   NOT NULL COMMENT '확정신고/조기인출추징(미리보기는 저장 안 함)',
    sell_amount   DECIMAL(15,2) NOT NULL COMMENT '[1] 가중매도금액(조정비율 분모)',
    gain_amount   DECIMAL(15,2) NOT NULL COMMENT '비가중 총양도소득(F6용)',
    gain_weighted DECIMAL(15,2) NOT NULL COMMENT '[1] 가중양도소득 = 조정전공제액',
    ext_amount    DECIMAL(15,2) NOT NULL COMMENT '[2] 외부 순매수 가중합산(myData 스냅샷)',
    ratio         DECIMAL(7,4)  NOT NULL COMMENT '[3] 조정비율(0~1 clamp)',
    deduction     DECIMAL(15,2) NOT NULL COMMENT '[4] 최종공제액',
    tax           DECIMAL(15,2) NOT NULL COMMENT '[5] 최종세액',
    PRIMARY KEY (calc_id),
    CONSTRAINT chk_tax_calc_basis CHECK (basis_type IN ('확정신고','조기인출추징'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='세액 계산 확정 근거 스냅샷';

-- ---------------------------------------------------------------------
-- 정산 배치
-- ---------------------------------------------------------------------
CREATE TABLE settlement_batch (
    batch_id    BIGINT      NOT NULL AUTO_INCREMENT,
    executed_at DATETIME    NOT NULL COMMENT '배치 실행일시(일단위=익일정산)',
    status      VARCHAR(10) NOT NULL COMMENT 'RUNNING/COMPLETED/FAILED',
    run_id      VARCHAR(50) NOT NULL COMMENT 'idempotency 추적용 실행ID',
    PRIMARY KEY (batch_id),
    CONSTRAINT chk_settlement_batch_status CHECK (status IN ('RUNNING','COMPLETED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='정산 배치';

CREATE TABLE settlement_item (
    item_id      BIGINT      NOT NULL AUTO_INCREMENT,
    batch_id     BIGINT      NOT NULL,
    exchange_id  BIGINT      NOT NULL COMMENT '처리 대상 환전 ID',
    result       VARCHAR(10) NULL     COMMENT 'NULL(대기)/SUCCESS/FAILED',
    processed_at DATETIME    NULL     COMMENT '개별 확정산 완료일시',
    PRIMARY KEY (item_id),
    CONSTRAINT chk_settlement_item_result CHECK (result IN ('SUCCESS','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='정산 배치 항목';

-- 정산 배치 행 잠금 (같은 영업일 배치 중복 실행 방지: SELECT ... FOR UPDATE 대상)
CREATE TABLE settlement_batch_guard (
    business_date DATE     NOT NULL COMMENT '정산 대상 영업일(행 잠금 키)',
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='정산 배치 행 잠금';

-- ---------------------------------------------------------------------
-- 관리자
-- ---------------------------------------------------------------------
CREATE TABLE admin_user (
    admin_id      BIGINT       NOT NULL AUTO_INCREMENT,
    login_id      VARCHAR(50)  NOT NULL,
    name          VARCHAR(50)  NOT NULL COMMENT '관리자 이름',
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'VIEWER' COMMENT 'VIEWER(조회전용)/REVIEWER(심사담당)/SETTLEMENT(정산담당)/ADMIN(최고관리자). 계정 생성 시 기본 VIEWER, ADMIN이 추후 승격',
    PRIMARY KEY (admin_id),
    UNIQUE KEY uk_admin_login_id (login_id),
    CONSTRAINT chk_admin_role CHECK (role IN ('VIEWER','REVIEWER','SETTLEMENT','ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='백오피스 관리자';

CREATE TABLE audit_log (
    audit_id     BIGINT      NOT NULL AUTO_INCREMENT,
    admin_id     BIGINT      NOT NULL,
    target_table VARCHAR(50) NOT NULL COMMENT '대상테이블',
    target_pk    VARCHAR(50) NOT NULL COMMENT '대상 PK값',
    before_value TEXT        NULL     COMMENT '변경전값',
    after_value  TEXT        NULL     COMMENT '변경후값',
    reason_code  VARCHAR(30) NULL     COMMENT '사유코드',
    processed_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (audit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='감사 로그';

-- =====================================================================
-- FOREIGN KEYS (시스템 내부만. myData/증권사 참조값은 FK 없음)
-- =====================================================================
ALTER TABLE customer_auth          ADD CONSTRAINT fk_customer_auth__customer          FOREIGN KEY (customer_id)          REFERENCES customer (customer_id);
ALTER TABLE customer_auth_password ADD CONSTRAINT fk_cap__customer_auth               FOREIGN KEY (auth_id)              REFERENCES customer_auth (auth_id);
ALTER TABLE account                ADD CONSTRAINT fk_account__customer                FOREIGN KEY (customer_id)          REFERENCES customer (customer_id);
ALTER TABLE account_status_log     ADD CONSTRAINT fk_asl__account                     FOREIGN KEY (account_id)           REFERENCES account (account_id);
ALTER TABLE account_benefit_log    ADD CONSTRAINT fk_abl__account                     FOREIGN KEY (account_id)           REFERENCES account (account_id);
ALTER TABLE domestic_stock_balance ADD CONSTRAINT fk_dsb__account                     FOREIGN KEY (account_id)           REFERENCES account (account_id);
ALTER TABLE domestic_stock_balance ADD CONSTRAINT fk_dsb__domestic_product            FOREIGN KEY (domestic_product_id)  REFERENCES domestic_product (domestic_product_id);
ALTER TABLE inbound                ADD CONSTRAINT fk_inbound__account                 FOREIGN KEY (account_id)           REFERENCES account (account_id);
ALTER TABLE inbound_detail         ADD CONSTRAINT fk_inbound_detail__inbound          FOREIGN KEY (inbound_id)           REFERENCES inbound (inbound_id);
ALTER TABLE inbound_detail         ADD CONSTRAINT fk_inbound_detail__foreign_product  FOREIGN KEY (foreign_product_id)   REFERENCES foreign_product (foreign_product_id);
ALTER TABLE inbound_min            ADD CONSTRAINT fk_inbound_min__inbound_detail       FOREIGN KEY (inbound_detail_id)    REFERENCES inbound_detail (inbound_detail_id);
ALTER TABLE outbound               ADD CONSTRAINT fk_outbound__inbound_detail          FOREIGN KEY (inbound_detail_id)    REFERENCES inbound_detail (inbound_detail_id);
ALTER TABLE sell_order             ADD CONSTRAINT fk_sell_order__inbound_detail        FOREIGN KEY (inbound_detail_id)    REFERENCES inbound_detail (inbound_detail_id);
ALTER TABLE krw_exchange           ADD CONSTRAINT fk_krw_exchange__account             FOREIGN KEY (account_id)           REFERENCES account (account_id);
ALTER TABLE krw_exchange           ADD CONSTRAINT fk_krw_exchange__sell_order          FOREIGN KEY (order_id)             REFERENCES sell_order (order_id);
ALTER TABLE left_amount            ADD CONSTRAINT fk_left_amount__krw_exchange         FOREIGN KEY (exchange_id)          REFERENCES krw_exchange (exchange_id);
ALTER TABLE withdrawal             ADD CONSTRAINT fk_withdrawal__account               FOREIGN KEY (account_id)           REFERENCES account (account_id);
ALTER TABLE withdrawal_allocation  ADD CONSTRAINT fk_wa__withdrawal                    FOREIGN KEY (withdrawal_id)        REFERENCES withdrawal (withdrawal_id);
ALTER TABLE withdrawal_allocation  ADD CONSTRAINT fk_wa__left_amount                   FOREIGN KEY (left_amount_id)       REFERENCES left_amount (left_amount_id);
ALTER TABLE tax_calculation        ADD CONSTRAINT fk_tax_calc__account                 FOREIGN KEY (account_id)           REFERENCES account (account_id);
ALTER TABLE settlement_item        ADD CONSTRAINT fk_settlement_item__batch            FOREIGN KEY (batch_id)             REFERENCES settlement_batch (batch_id);
ALTER TABLE settlement_item        ADD CONSTRAINT fk_settlement_item__krw_exchange     FOREIGN KEY (exchange_id)          REFERENCES krw_exchange (exchange_id);
ALTER TABLE audit_log              ADD CONSTRAINT fk_audit_log__admin_user             FOREIGN KEY (admin_id)             REFERENCES admin_user (admin_id);
