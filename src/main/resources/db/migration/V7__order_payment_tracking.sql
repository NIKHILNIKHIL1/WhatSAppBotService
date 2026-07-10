-- Payment tracking on orders: status is derived from amount_paid vs total_amount by the service
-- layer; amount_paid supports partial (khata-style) settlement across multiple payments.
ALTER TABLE order_header
    ADD COLUMN payment_status    VARCHAR(20) NOT NULL DEFAULT 'UNPAID'
        CHECK (payment_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID', 'REFUNDED')),
    ADD COLUMN payment_method    VARCHAR(20)
        CHECK (payment_method IN ('CASH', 'UPI', 'BANK_TRANSFER', 'OTHER')),
    ADD COLUMN amount_paid       NUMERIC(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN paid_at           TIMESTAMPTZ,
    ADD COLUMN payment_reference VARCHAR(150);

-- Backs the dashboard "outstanding" tile and the ?paymentStatus= list filter.
CREATE INDEX idx_order_header_payment_status ON order_header (tenant_id, payment_status);
