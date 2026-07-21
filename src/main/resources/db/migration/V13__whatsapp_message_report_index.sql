CREATE INDEX IF NOT EXISTS idx_wa_message_tenant_date ON whatsapp_message (tenant_id, created_at);
