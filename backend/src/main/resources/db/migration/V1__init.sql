-- 리베이트 정책 기간 중복 방지(EXCLUDE 제약)에 필요
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ===== 기준정보 =====

CREATE TABLE partner_grade (
    grade_code  VARCHAR(20)  PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL
);

CREATE TABLE rebate_policy (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grade_code   VARCHAR(20)  NOT NULL REFERENCES partner_grade (grade_code),
    rebate_rate  NUMERIC(5,4) NOT NULL CHECK (rebate_rate >= 0 AND rebate_rate < 1),
    valid_from   DATE         NOT NULL,
    valid_to     DATE,        -- 미포함(exclusive), NULL이면 현재 적용 중
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to > valid_from),
    -- 같은 등급에서 적용 기간이 겹치면 등록 불가
    EXCLUDE USING gist (
        grade_code WITH =,
        daterange(valid_from, valid_to, '[)') WITH &&
    )
);

CREATE TABLE partner (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    grade_code  VARCHAR(20)  NOT NULL REFERENCES partner_grade (grade_code),
    status      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login_id       VARCHAR(50)  NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    name           VARCHAR(50)  NOT NULL,
    role           VARCHAR(10)  NOT NULL CHECK (role IN ('ADMIN', 'PARTNER')),
    partner_id     BIGINT       REFERENCES partner (id),
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    -- 거래처 사용자는 반드시 거래처에 소속, 관리자는 소속 없음
    CHECK ((role = 'PARTNER' AND partner_id IS NOT NULL)
        OR (role = 'ADMIN'   AND partner_id IS NULL))
);

CREATE TABLE product (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku         VARCHAR(30)   NOT NULL UNIQUE,
    name        VARCHAR(200)  NOT NULL,
    category    VARCHAR(30)   NOT NULL,
    unit_price  NUMERIC(15,0) NOT NULL CHECK (unit_price >= 0),
    stock_qty   INT           NOT NULL DEFAULT 0 CHECK (stock_qty >= 0),
    status      VARCHAR(10)   NOT NULL DEFAULT 'ON_SALE' CHECK (status IN ('ON_SALE', 'STOPPED')),
    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT now()
);

-- ===== 주문 =====

CREATE TABLE sales_order (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_no            VARCHAR(20)   NOT NULL UNIQUE,
    partner_id          BIGINT        NOT NULL REFERENCES partner (id),
    partner_grade_code  VARCHAR(20)   NOT NULL REFERENCES partner_grade (grade_code), -- 주문 시점 등급
    status              VARCHAR(10)   NOT NULL DEFAULT 'REQUESTED'
                        CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'CANCELED', 'SHIPPED')),
    total_amount        NUMERIC(15,0) NOT NULL CHECK (total_amount >= 0),
    ordered_at          TIMESTAMP     NOT NULL DEFAULT now(),
    approved_at         TIMESTAMP,
    shipped_at          TIMESTAMP,
    version             INT           NOT NULL DEFAULT 0,
    CHECK (status <> 'SHIPPED' OR shipped_at IS NOT NULL)
);

CREATE INDEX idx_sales_order_partner ON sales_order (partner_id, ordered_at);
CREATE INDEX idx_sales_order_status  ON sales_order (status, ordered_at);

CREATE TABLE sales_order_item (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    BIGINT        NOT NULL REFERENCES sales_order (id),
    line_no     INT           NOT NULL,
    product_id  BIGINT        NOT NULL REFERENCES product (id),
    qty         INT           NOT NULL CHECK (qty > 0),
    unit_price  NUMERIC(15,0) NOT NULL CHECK (unit_price >= 0),
    amount      NUMERIC(15,0) NOT NULL CHECK (amount >= 0),
    UNIQUE (order_id, line_no)
);

CREATE TABLE order_status_history (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id     BIGINT       NOT NULL REFERENCES sales_order (id),
    from_status  VARCHAR(10),
    to_status    VARCHAR(10)  NOT NULL,
    reason       VARCHAR(500),
    changed_by   BIGINT       NOT NULL REFERENCES app_user (id),
    changed_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_status_history_order ON order_status_history (order_id, changed_at);

CREATE TABLE sales_return (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_item_id  BIGINT        NOT NULL REFERENCES sales_order_item (id),
    qty            INT           NOT NULL CHECK (qty > 0),
    amount         NUMERIC(15,0) NOT NULL CHECK (amount >= 0),
    reason         VARCHAR(500),
    returned_at    TIMESTAMP     NOT NULL DEFAULT now(),
    created_by     BIGINT        NOT NULL REFERENCES app_user (id)
);

CREATE INDEX idx_sales_return_item ON sales_return (order_item_id);

-- ===== 정산 =====

CREATE TABLE settlement_period (
    yyyymm     CHAR(6)     PRIMARY KEY CHECK (yyyymm ~ '^[0-9]{4}(0[1-9]|1[0-2])$'),
    status     VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    closed_at  TIMESTAMP,
    closed_by  BIGINT      REFERENCES app_user (id)
);

CREATE TABLE settlement (
    yyyymm         CHAR(6)       NOT NULL REFERENCES settlement_period (yyyymm),
    partner_id     BIGINT        NOT NULL REFERENCES partner (id),
    ship_amount    NUMERIC(15,0) NOT NULL,
    return_amount  NUMERIC(15,0) NOT NULL,
    net_amount     NUMERIC(15,0) NOT NULL,
    rebate_amount  NUMERIC(15,0) NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT now(),
    PRIMARY KEY (yyyymm, partner_id)
);

-- 프로시저가 상세를 먼저 만들고 합계를 나중에 만들기 때문에
-- settlement가 아니라 settlement_period, partner를 직접 참조한다
CREATE TABLE settlement_detail (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    yyyymm         CHAR(6)       NOT NULL REFERENCES settlement_period (yyyymm),
    partner_id     BIGINT        NOT NULL REFERENCES partner (id),
    source_type    VARCHAR(10)   NOT NULL CHECK (source_type IN ('SHIP', 'RETURN')),
    source_id      BIGINT        NOT NULL, -- SHIP: sales_order_item.id, RETURN: sales_return.id
    amount         NUMERIC(15,0) NOT NULL,
    rebate_rate    NUMERIC(5,4)  NOT NULL,
    rebate_amount  NUMERIC(15,0) NOT NULL
);

CREATE INDEX idx_settlement_detail_partner ON settlement_detail (yyyymm, partner_id);

CREATE TABLE settlement_run_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    yyyymm      CHAR(6)       NOT NULL REFERENCES settlement_period (yyyymm),
    run_type    VARCHAR(10)   NOT NULL CHECK (run_type IN ('PREVIEW', 'CLOSE')),
    row_count   INT,
    elapsed_ms  INT,
    result      VARCHAR(10)   NOT NULL CHECK (result IN ('SUCCESS', 'FAILED')),
    message     VARCHAR(1000),
    run_by      BIGINT        NOT NULL REFERENCES app_user (id),
    run_at      TIMESTAMP     NOT NULL DEFAULT now()
);
