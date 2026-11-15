CREATE SEQUENCE claim_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE claims (
    id                   UUID                     PRIMARY KEY,
    claim_number         VARCHAR(30)              NOT NULL,
    policy_id            UUID                     NOT NULL,
    claimant_id          UUID                     NOT NULL,
    assigned_adjuster_id UUID,
    incident_date        DATE                     NOT NULL,
    description          VARCHAR(2000)            NOT NULL,
    claimed_amount       NUMERIC(15, 2)           NOT NULL,
    approved_amount      NUMERIC(15, 2),
    rejection_reason     VARCHAR(1000),
    status               VARCHAR(20)              NOT NULL,
    version              BIGINT                   NOT NULL DEFAULT 0,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_claims_number UNIQUE (claim_number),
    CONSTRAINT fk_claims_policy FOREIGN KEY (policy_id) REFERENCES policies (id),
    CONSTRAINT fk_claims_claimant FOREIGN KEY (claimant_id) REFERENCES users (id),
    CONSTRAINT fk_claims_adjuster FOREIGN KEY (assigned_adjuster_id) REFERENCES users (id),
    CONSTRAINT ck_claims_status CHECK (status IN ('SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'SETTLED', 'WITHDRAWN')),
    CONSTRAINT ck_claims_claimed_amount CHECK (claimed_amount > 0),
    CONSTRAINT ck_claims_approved_amount CHECK (approved_amount IS NULL OR approved_amount >= 0)
);

CREATE INDEX idx_claims_claimant ON claims (claimant_id);
CREATE INDEX idx_claims_policy ON claims (policy_id);
CREATE INDEX idx_claims_adjuster ON claims (assigned_adjuster_id);
CREATE INDEX idx_claims_status_created ON claims (status, created_at DESC);

CREATE TABLE claim_status_history (
    id          UUID                     PRIMARY KEY,
    claim_id    UUID                     NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)              NOT NULL,
    changed_by  UUID                     NOT NULL,
    comment     VARCHAR(1000),
    changed_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_history_claim FOREIGN KEY (claim_id) REFERENCES claims (id) ON DELETE CASCADE,
    CONSTRAINT fk_history_changed_by FOREIGN KEY (changed_by) REFERENCES users (id)
);

CREATE INDEX idx_history_claim ON claim_status_history (claim_id, changed_at);
