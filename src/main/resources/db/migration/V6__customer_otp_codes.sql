CREATE TABLE customer_otp_code (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL REFERENCES tenant (id),
    phone_number   VARCHAR(32)  NOT NULL,
    code_hash      VARCHAR(128) NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    consumed_at    TIMESTAMPTZ,
    attempt_count  INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    version        BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_customer_otp_tenant_phone ON customer_otp_code (tenant_id, phone_number);
