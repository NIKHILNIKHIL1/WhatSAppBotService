-- The app's duplicate check (existsBySkuIgnoreCase) is case-insensitive, but the original
-- UNIQUE (tenant_id, sku) constraint was case-sensitive: two concurrent creates of 'CRM-MLK' and
-- 'crm-mlk' could both pass the app check AND both insert — after which the bot's
-- findBySkuIgnoreCase quick-order lookup matches two rows and throws. Enforce at the database
-- what the application assumes.
--
-- Existing rows are normalized the same way ProductService.create now normalizes input
-- (trim + uppercase). If a tenant already has two SKUs differing only in case/whitespace, this
-- migration fails loudly on the UPDATE or index creation — that data is genuinely ambiguous and
-- must be fixed by hand rather than silently merged.
UPDATE product SET sku = upper(btrim(sku));

ALTER TABLE product DROP CONSTRAINT uq_product_tenant_sku;

CREATE UNIQUE INDEX uq_product_tenant_sku_ci ON product (tenant_id, lower(sku));
