ALTER TABLE tenant ADD COLUMN messaging_provider VARCHAR(20) NOT NULL DEFAULT 'META';
ALTER TABLE tenant ADD COLUMN twilio_account_sid VARCHAR(64);
ALTER TABLE tenant ADD COLUMN twilio_auth_token TEXT;
ALTER TABLE tenant ADD COLUMN twilio_whatsapp_number VARCHAR(20);
ALTER TABLE tenant ADD CONSTRAINT uq_tenant_twilio_whatsapp_number UNIQUE (twilio_whatsapp_number);
