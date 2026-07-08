-- Core schema for the multi-tenant inventory & WhatsApp ordering platform.
-- Every tenant-scoped table carries tenant_id + created_at/by, updated_at/by, version
-- so tenant isolation and auditing are enforced consistently across the domain.

CREATE TABLE tenant (
    id                          BIGSERIAL PRIMARY KEY,
    name                        VARCHAR(255)        NOT NULL,
    slug                        VARCHAR(100)        NOT NULL UNIQUE,
    whatsapp_phone_number_id    VARCHAR(64)         UNIQUE,
    whatsapp_business_account_id VARCHAR(64),
    default_language_code       VARCHAR(10)         NOT NULL DEFAULT 'en',
    currency_code               VARCHAR(3)          NOT NULL DEFAULT 'INR',
    timezone                    VARCHAR(64)         NOT NULL DEFAULT 'UTC',
    status                      VARCHAR(20)         NOT NULL DEFAULT 'ACTIVE'
                                CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED')),
    created_at                  TIMESTAMPTZ         NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ         NOT NULL DEFAULT now(),
    created_by                  VARCHAR(255),
    updated_by                  VARCHAR(255),
    version                     BIGINT              NOT NULL DEFAULT 0
);

CREATE TABLE tenant_user (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT REFERENCES tenant (id),
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    full_name      VARCHAR(255) NOT NULL,
    role           VARCHAR(30)  NOT NULL
                   CHECK (role IN ('SUPER_ADMIN', 'VENDOR_ADMIN', 'VENDOR_STAFF')),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_tenant_user_tenant_role CHECK (
        (role = 'SUPER_ADMIN' AND tenant_id IS NULL) OR
        (role <> 'SUPER_ADMIN' AND tenant_id IS NOT NULL)
    )
);
CREATE INDEX idx_tenant_user_tenant ON tenant_user (tenant_id);

CREATE TABLE language (
    code        VARCHAR(10) PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    native_name VARCHAR(100),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE configuration (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT REFERENCES tenant (id),
    config_key   VARCHAR(150) NOT NULL,
    config_value TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   VARCHAR(255),
    updated_by   VARCHAR(255),
    version      BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_configuration_tenant_key ON configuration (COALESCE(tenant_id, 0), config_key);

CREATE TABLE category (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL REFERENCES tenant (id),
    parent_category_id  BIGINT REFERENCES category (id),
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    display_order       INTEGER      NOT NULL DEFAULT 0,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    version             BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_category_tenant ON category (tenant_id);

CREATE TABLE product (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL REFERENCES tenant (id),
    category_id  BIGINT REFERENCES category (id),
    sku          VARCHAR(100) NOT NULL,
    name         VARCHAR(255) NOT NULL,
    description  TEXT,
    unit         VARCHAR(30)  NOT NULL,
    price        NUMERIC(12, 2) NOT NULL,
    currency_code VARCHAR(3)  NOT NULL DEFAULT 'INR',
    image_url    VARCHAR(500),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   VARCHAR(255),
    updated_by   VARCHAR(255),
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_product_tenant_sku UNIQUE (tenant_id, sku)
);
CREATE INDEX idx_product_tenant ON product (tenant_id);
CREATE INDEX idx_product_category ON product (category_id);

CREATE TABLE inventory (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT      NOT NULL REFERENCES tenant (id),
    product_id          BIGINT      NOT NULL REFERENCES product (id),
    quantity_on_hand    NUMERIC(14, 3) NOT NULL DEFAULT 0,
    reorder_level       NUMERIC(14, 3) NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_inventory_tenant_product UNIQUE (tenant_id, product_id)
);
CREATE INDEX idx_inventory_tenant ON inventory (tenant_id);

CREATE TABLE inventory_transaction (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT      NOT NULL REFERENCES tenant (id),
    inventory_id     BIGINT      NOT NULL REFERENCES inventory (id),
    product_id       BIGINT      NOT NULL REFERENCES product (id),
    transaction_type VARCHAR(30) NOT NULL
                     CHECK (transaction_type IN ('RECEIPT', 'SALE', 'ADJUSTMENT', 'RETURN', 'RESERVATION', 'RELEASE')),
    quantity_delta   NUMERIC(14, 3) NOT NULL,
    quantity_after   NUMERIC(14, 3) NOT NULL,
    reference_type   VARCHAR(30),
    reference_id     BIGINT,
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    version          BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_inventory_transaction_tenant ON inventory_transaction (tenant_id);
CREATE INDEX idx_inventory_transaction_inventory ON inventory_transaction (inventory_id);

CREATE TABLE customer (
    id                       BIGSERIAL PRIMARY KEY,
    tenant_id                BIGINT       NOT NULL REFERENCES tenant (id),
    phone_number             VARCHAR(20)  NOT NULL,
    full_name                VARCHAR(255),
    preferred_language_code  VARCHAR(10)  NOT NULL DEFAULT 'en',
    status                   VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                             CHECK (status IN ('ACTIVE', 'BLOCKED')),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               VARCHAR(255),
    updated_by               VARCHAR(255),
    version                  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_customer_tenant_phone UNIQUE (tenant_id, phone_number)
);
CREATE INDEX idx_customer_tenant ON customer (tenant_id);

CREATE TABLE order_header (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL REFERENCES tenant (id),
    order_number    VARCHAR(40)  NOT NULL,
    customer_id     BIGINT       NOT NULL REFERENCES customer (id),
    status          VARCHAR(20)  NOT NULL DEFAULT 'NEW'
                    CHECK (status IN ('NEW', 'CONFIRMED', 'ACCEPTED', 'PICKING', 'PACKED', 'DISPATCHED', 'DELIVERED', 'CANCELLED')),
    channel         VARCHAR(20)  NOT NULL DEFAULT 'WHATSAPP'
                    CHECK (channel IN ('WHATSAPP', 'WEB', 'API')),
    currency_code   VARCHAR(3)   NOT NULL DEFAULT 'INR',
    subtotal_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_amount    NUMERIC(14, 2) NOT NULL DEFAULT 0,
    notes           TEXT,
    idempotency_key VARCHAR(150),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_order_tenant_number UNIQUE (tenant_id, order_number)
);
CREATE UNIQUE INDEX uq_order_tenant_idempotency ON order_header (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_order_header_tenant ON order_header (tenant_id);
CREATE INDEX idx_order_header_customer ON order_header (customer_id);

CREATE TABLE order_item (
    id                     BIGSERIAL PRIMARY KEY,
    tenant_id              BIGINT      NOT NULL REFERENCES tenant (id),
    order_id               BIGINT      NOT NULL REFERENCES order_header (id),
    product_id             BIGINT      NOT NULL REFERENCES product (id),
    product_name_snapshot  VARCHAR(255) NOT NULL,
    unit_price_snapshot    NUMERIC(12, 2) NOT NULL,
    quantity               NUMERIC(14, 3) NOT NULL,
    line_total             NUMERIC(14, 2) NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             VARCHAR(255),
    updated_by             VARCHAR(255),
    version                BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_order_item_tenant ON order_item (tenant_id);
CREATE INDEX idx_order_item_order ON order_item (order_id);

CREATE TABLE order_status_history (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT      NOT NULL REFERENCES tenant (id),
    order_id     BIGINT      NOT NULL REFERENCES order_header (id),
    from_status  VARCHAR(20),
    to_status    VARCHAR(20) NOT NULL,
    notes        TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   VARCHAR(255),
    updated_by   VARCHAR(255),
    version      BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_order_status_history_tenant ON order_status_history (tenant_id);
CREATE INDEX idx_order_status_history_order ON order_status_history (order_id);

CREATE TABLE whatsapp_message (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT       NOT NULL REFERENCES tenant (id),
    customer_id      BIGINT REFERENCES customer (id),
    wa_message_id    VARCHAR(150) NOT NULL,
    direction        VARCHAR(10)  NOT NULL CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    from_phone_number VARCHAR(20) NOT NULL,
    to_phone_number   VARCHAR(20) NOT NULL,
    message_type     VARCHAR(30)  NOT NULL,
    payload          JSONB        NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED'
                     CHECK (status IN ('RECEIVED', 'SENT', 'DELIVERED', 'READ', 'FAILED')),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_whatsapp_message_wa_id UNIQUE (wa_message_id)
);
CREATE INDEX idx_whatsapp_message_tenant ON whatsapp_message (tenant_id);
CREATE INDEX idx_whatsapp_message_customer ON whatsapp_message (customer_id);

CREATE TABLE notification (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT      NOT NULL REFERENCES tenant (id),
    recipient_type  VARCHAR(20) NOT NULL CHECK (recipient_type IN ('CUSTOMER', 'VENDOR')),
    recipient_id    BIGINT      NOT NULL,
    channel         VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP'
                    CHECK (channel IN ('WHATSAPP', 'EMAIL', 'SMS')),
    template_code   VARCHAR(100) NOT NULL,
    payload         JSONB,
    order_id        BIGINT REFERENCES order_header (id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    version         BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_notification_tenant ON notification (tenant_id);
CREATE INDEX idx_notification_order ON notification (order_id);

CREATE TABLE audit_log (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT REFERENCES tenant (id),
    entity_name  VARCHAR(100) NOT NULL,
    entity_id    VARCHAR(100) NOT NULL,
    action       VARCHAR(20)  NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    old_value    JSONB,
    new_value    JSONB,
    performed_by VARCHAR(255),
    ip_address   VARCHAR(64),
    channel      VARCHAR(20)  NOT NULL DEFAULT 'API'
                 CHECK (channel IN ('WEB', 'WHATSAPP', 'API')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_tenant ON audit_log (tenant_id);
CREATE INDEX idx_audit_log_entity ON audit_log (entity_name, entity_id);

INSERT INTO language (code, name, native_name) VALUES
    ('en', 'English', 'English'),
    ('hi', 'Hindi', 'हिन्दी');
