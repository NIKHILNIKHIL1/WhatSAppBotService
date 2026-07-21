-- Three read-path indexes that back every date-bucketed report query.
-- None of these columns are write-hot (created_at is set once on insert; product_id on
-- order_item is immutable), so the indexes carry negligible write overhead while cutting
-- full-table scans from every aggregation query in the report module down to index range scans.
--
-- Flyway wraps this in a single transaction; if any statement fails the whole migration rolls back.

-- Revenue and order-count queries filter/group by (tenant_id, created_at).
-- The tenant_id prefix keeps the index usable under Hibernate's @TenantId filter.
CREATE INDEX IF NOT EXISTS idx_order_header_tenant_date
    ON order_header (tenant_id, created_at);

-- Product-performance aggregations join order_item back to order_header filtered by
-- tenant_id, then group by product_id.  Covering both in one index avoids a heap fetch
-- for the most common report join.
CREATE INDEX IF NOT EXISTS idx_order_item_tenant_product
    ON order_item (tenant_id, product_id);

-- Stock-movement charts read inventory_transaction filtered by (tenant_id, created_at)
-- and group by date bucket.  Mirrors the order_header index pattern.
CREATE INDEX IF NOT EXISTS idx_inv_txn_tenant_date
    ON inventory_transaction (tenant_id, created_at);
