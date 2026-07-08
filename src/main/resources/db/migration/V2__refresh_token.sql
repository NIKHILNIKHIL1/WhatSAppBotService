CREATE TABLE refresh_token (
    id             BIGSERIAL PRIMARY KEY,
    tenant_user_id BIGINT       NOT NULL REFERENCES tenant_user (id),
    token_hash     VARCHAR(128) NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_token_user ON refresh_token (tenant_user_id);
