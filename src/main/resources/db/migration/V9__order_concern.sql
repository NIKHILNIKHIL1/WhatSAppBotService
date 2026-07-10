-- Post-delivery concerns: a customer photo (damaged/wrong item) sent in the WhatsApp chat,
-- linked to their most recent order when one exists, forwarded to the vendor's WhatsApp and
-- tracked here so it can be resolved from the console instead of scrolling away in chat.
-- media_reference is Meta's media id (or Twilio's media URL) — the image itself lives with the
-- provider and is delivered to the vendor's phone; this row is the audit/tracking record.
CREATE TABLE order_concern (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL REFERENCES tenant (id),
    order_id        BIGINT       REFERENCES order_header (id),
    customer_id     BIGINT       NOT NULL REFERENCES customer (id),
    media_reference VARCHAR(500),
    caption         TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    version         BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_order_concern_tenant ON order_concern (tenant_id);
CREATE INDEX idx_order_concern_order ON order_concern (order_id);
