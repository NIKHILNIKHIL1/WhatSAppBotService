-- Seed the two new languages this feature adds. 'en'/'hi' already exist from V1.
INSERT INTO language (code, name, native_name) VALUES
    ('fr', 'French', 'Français'),
    ('wo', 'Wolof', 'Wolof')
ON CONFLICT (code) DO NOTHING;

-- Defensive: default_language_code was never validated against a fixed set before this
-- feature, so normalize any pre-existing tenant whose value isn't one of the 4 known
-- codes before FK-ing tenant_language against `language`.
UPDATE tenant SET default_language_code = 'en'
WHERE default_language_code NOT IN ('en', 'fr', 'wo', 'hi');

CREATE TABLE tenant_language (
    tenant_id      BIGINT      NOT NULL REFERENCES tenant (id),
    language_code  VARCHAR(10) NOT NULL REFERENCES language (code),
    PRIMARY KEY (tenant_id, language_code)
);
CREATE INDEX idx_tenant_language_tenant ON tenant_language (tenant_id);

-- Backfill: every existing tenant supports (at least) its current default language.
INSERT INTO tenant_language (tenant_id, language_code)
SELECT id, default_language_code FROM tenant
ON CONFLICT DO NOTHING;

CREATE TABLE product_translation (
    product_id     BIGINT       NOT NULL REFERENCES product (id),
    language_code  VARCHAR(10)  NOT NULL REFERENCES language (code),
    name           VARCHAR(255) NOT NULL,
    description    TEXT,
    PRIMARY KEY (product_id, language_code)
);

CREATE TABLE category_translation (
    category_id    BIGINT       NOT NULL REFERENCES category (id),
    language_code  VARCHAR(10)  NOT NULL REFERENCES language (code),
    name           VARCHAR(255) NOT NULL,
    description    TEXT,
    PRIMARY KEY (category_id, language_code)
);
