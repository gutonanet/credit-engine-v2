CREATE TABLE rate_vigencies (
    id              UUID PRIMARY KEY,
    pair            VARCHAR(32)     NOT NULL,
    rate            NUMERIC(18, 8)  NOT NULL,
    effective_at    TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_rate_vigencies_pair_effective
    ON rate_vigencies (pair, effective_at DESC);

CREATE TABLE receivables (
    id                UUID PRIMARY KEY,
    assignor_id       VARCHAR(128)    NOT NULL,
    face_value        NUMERIC(19, 2)  NOT NULL,
    due_date          DATE            NOT NULL,
    receivable_type   VARCHAR(64)     NOT NULL,
    payment_currency  VARCHAR(8)      NOT NULL,
    status            VARCHAR(16)     NOT NULL,
    version           BIGINT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ     NOT NULL
);

CREATE TABLE settlements (
    id                  UUID PRIMARY KEY,
    receivable_id       UUID            NOT NULL UNIQUE REFERENCES receivables (id),
    idempotency_key     UUID            NOT NULL UNIQUE,
    request_body_hash   VARCHAR(64)     NOT NULL,
    assignor_id         VARCHAR(128)    NOT NULL,
    face_value          NUMERIC(19, 2)  NOT NULL,
    term_months         INT             NOT NULL,
    receivable_type     VARCHAR(64)     NOT NULL,
    base_rate           NUMERIC(18, 8)  NOT NULL,
    spread              NUMERIC(18, 8)  NOT NULL,
    present_value_brl   NUMERIC(19, 2)  NOT NULL,
    discount_brl        NUMERIC(19, 2)  NOT NULL,
    payment_amount      NUMERIC(19, 2)  NOT NULL,
    payment_currency    VARCHAR(8)      NOT NULL,
    fx_rate_usd_brl     NUMERIC(18, 8),
    settled_at          TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_settlements_report
    ON settlements (settled_at, assignor_id, payment_currency);
