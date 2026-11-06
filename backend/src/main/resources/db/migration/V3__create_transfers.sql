
CREATE TABLE transfers (

    id UUID PRIMARY KEY,

    from_account_id UUID NOT NULL
        REFERENCES accounts(id),

    to_account_id UUID NOT NULL
        REFERENCES accounts(id),

    amount NUMERIC(19,2) NOT NULL
        CHECK (amount > 0),

    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_different_transfer_accounts
        CHECK (from_account_id <> to_account_id)
);

ALTER TABLE account_transactions
ADD COLUMN transfer_id UUID
    REFERENCES transfers(id);

CREATE INDEX idx_account_transactions_transfer_id
ON account_transactions(transfer_id);
