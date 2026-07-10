-- Closed-by-default WhatsApp ordering: only customers the vendor has registered (and not blocked)
-- can transact. Vendors can opt back into open ordering per tenant from the settings page.
ALTER TABLE tenant
    ADD COLUMN require_customer_registration BOOLEAN NOT NULL DEFAULT TRUE;

-- Customer phone numbers are being normalized to the canonical '+<digits>' form (historically
-- Meta-created customers were stored without the '+' while vendor/API-created ones had it, so a
-- registered customer could never be matched to their own inbound messages).
--
-- Step 1: merge rows that normalization would collide — the same tenant+number stored in two
-- formats is the same real-world customer twice (e.g. '+918308930917' registered by the vendor
-- and '918308930917' auto-created from a webhook). The oldest row survives; orders, message logs
-- and notifications are repointed onto it, and a missing name is backfilled from the duplicate
-- before it is deleted.
DO $$
DECLARE
    dup RECORD;
BEGIN
    FOR dup IN
        SELECT d.id AS dup_id, k.id AS keep_id
        FROM customer d
        JOIN customer k
          ON k.tenant_id = d.tenant_id
         AND '+' || regexp_replace(k.phone_number, '\D', '', 'g')
             = '+' || regexp_replace(d.phone_number, '\D', '', 'g')
         AND k.id < d.id
        WHERE k.id = (
            SELECT MIN(c.id) FROM customer c
            WHERE c.tenant_id = d.tenant_id
              AND '+' || regexp_replace(c.phone_number, '\D', '', 'g')
                  = '+' || regexp_replace(d.phone_number, '\D', '', 'g'))
        ORDER BY dup_id
    LOOP
        UPDATE order_header SET customer_id = dup.keep_id WHERE customer_id = dup.dup_id;
        UPDATE whatsapp_message SET customer_id = dup.keep_id WHERE customer_id = dup.dup_id;
        UPDATE notification SET recipient_id = dup.keep_id
        WHERE recipient_type = 'CUSTOMER' AND recipient_id = dup.dup_id;

        UPDATE customer keep
        SET full_name = COALESCE(NULLIF(keep.full_name, ''), dup_row.full_name),
            -- If either representation of this customer was blocked, blocked wins.
            status = CASE WHEN dup_row.status = 'BLOCKED' THEN 'BLOCKED' ELSE keep.status END
        FROM customer dup_row
        WHERE keep.id = dup.keep_id AND dup_row.id = dup.dup_id;

        DELETE FROM customer WHERE id = dup.dup_id;
    END LOOP;
END $$;

-- Step 2: now that each tenant+number exists exactly once, normalize the stored format.
UPDATE customer
SET phone_number = '+' || regexp_replace(phone_number, '\D', '', 'g')
WHERE phone_number <> '+' || regexp_replace(phone_number, '\D', '', 'g');
