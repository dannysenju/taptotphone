-- PostgreSQL Database Schema Setup for SoftPOS Backend Core
-- Enforces compliance by storing primary card numbers only in format-preserving encrypted and masked columns.

CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY,
    masked_pan VARCHAR(19) NOT NULL,
    encrypted_pan VARCHAR(19) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    response_code VARCHAR(2),
    approval_code VARCHAR(6),
    merchant_id VARCHAR(15) NOT NULL,
    terminal_id VARCHAR(8) NOT NULL,
    transmission_date_time VARCHAR(10) NOT NULL,
    stan VARCHAR(6) NOT NULL,
    status VARCHAR(15) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS mpoc_attestations (
    id UUID PRIMARY KEY,
    terminal_id VARCHAR(8) NOT NULL,
    device_hardware_id VARCHAR(255) NOT NULL,
    attestation_status VARCHAR(15) NOT NULL,
    attestation_date_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    signature VARCHAR(1024) NOT NULL,
    hce_token VARCHAR(512) NOT NULL
);

-- Indexes for lightning-fast lookups (essential for ISO-8583 MUX trace checking)
CREATE INDEX IF NOT EXISTS idx_transactions_stan_term ON transactions(stan, terminal_id);
CREATE INDEX IF NOT EXISTS idx_attestations_term_time ON mpoc_attestations(terminal_id, attestation_date_time DESC);
