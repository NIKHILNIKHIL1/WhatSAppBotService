CREATE TABLE password_reset_code (
    id             BIGSERIAL PRIMARY KEY,
    tenant_user_id BIGINT       NOT NULL REFERENCES tenant_user (id),
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
CREATE INDEX idx_password_reset_code_user ON password_reset_code (tenant_user_id);
