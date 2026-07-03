CREATE TABLE IF NOT EXISTS loans (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    book_id      BIGINT NOT NULL,
    loan_date    TIMESTAMPTZ NOT NULL,
    due_date     TIMESTAMPTZ NOT NULL,
    return_date  TIMESTAMPTZ,
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

CREATE INDEX IF NOT EXISTS idx_loans_user_id ON loans (user_id);
CREATE INDEX IF NOT EXISTS idx_loans_status ON loans (status);
