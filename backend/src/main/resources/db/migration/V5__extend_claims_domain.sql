-- V5: Extends the domain for the full claims-officer workflow.
-- Pre-release schema change: no production data exists yet, so dropping claims.rejection_reason
-- (now held in claim_decisions) loses nothing.

-- 1. Roles: ADJUSTER becomes CLAIMS_OFFICER; MANAGER is introduced.
ALTER TABLE users DROP CONSTRAINT ck_users_role;
UPDATE users SET role = 'CLAIMS_OFFICER' WHERE role = 'ADJUSTER';
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('CLAIMANT', 'CLAIMS_OFFICER', 'MANAGER', 'ADMIN'));

-- 2. Claimant profile (contact details and home market), 1:1 with a CLAIMANT user.
CREATE TABLE claimant_profiles (
    user_id    UUID                     PRIMARY KEY,
    phone      VARCHAR(30),
    market     VARCHAR(20)              NOT NULL,
    address    VARCHAR(300),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_claimant_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_claimant_profiles_market
        CHECK (market IN ('AUSTRALIA', 'SINGAPORE', 'HONG_KONG', 'INDIA', 'MALAYSIA', 'THAILAND'))
);

-- 3. Policies are issued in a market and denominated in a currency.
ALTER TABLE policies ADD COLUMN market VARCHAR(20) DEFAULT 'SINGAPORE' NOT NULL;
ALTER TABLE policies ADD COLUMN currency VARCHAR(3) DEFAULT 'SGD' NOT NULL;
ALTER TABLE policies ALTER COLUMN market DROP DEFAULT;
ALTER TABLE policies ALTER COLUMN currency DROP DEFAULT;
ALTER TABLE policies ADD CONSTRAINT ck_policies_market
    CHECK (market IN ('AUSTRALIA', 'SINGAPORE', 'HONG_KONG', 'INDIA', 'MALAYSIA', 'THAILAND'));
ALTER TABLE policies ADD CONSTRAINT ck_policies_currency
    CHECK (currency IN ('AUD', 'SGD', 'HKD', 'INR', 'MYR', 'THB', 'USD'));
CREATE INDEX idx_policies_market ON policies (market);

-- 4. Claims: roadmap terminology, classification, settlement and idempotent submission.
ALTER TABLE claims RENAME COLUMN claimed_amount TO estimated_loss;
ALTER TABLE claims RENAME COLUMN description TO incident_description;
ALTER TABLE claims RENAME COLUMN assigned_adjuster_id TO assigned_officer_id;
ALTER TABLE claims DROP CONSTRAINT fk_claims_adjuster;
ALTER TABLE claims ADD CONSTRAINT fk_claims_officer FOREIGN KEY (assigned_officer_id) REFERENCES users (id);
ALTER INDEX idx_claims_adjuster RENAME TO idx_claims_officer;
ALTER TABLE claims DROP CONSTRAINT ck_claims_claimed_amount;
ALTER TABLE claims ADD CONSTRAINT ck_claims_estimated_loss CHECK (estimated_loss > 0);
ALTER TABLE claims DROP COLUMN rejection_reason;

ALTER TABLE claims ADD COLUMN claim_type VARCHAR(20) DEFAULT 'MOTOR' NOT NULL;
ALTER TABLE claims ADD COLUMN market VARCHAR(20) DEFAULT 'SINGAPORE' NOT NULL;
ALTER TABLE claims ADD COLUMN currency VARCHAR(3) DEFAULT 'SGD' NOT NULL;
ALTER TABLE claims ADD COLUMN priority VARCHAR(10) DEFAULT 'NORMAL' NOT NULL;
ALTER TABLE claims ADD COLUMN incident_location VARCHAR(300);
ALTER TABLE claims ADD COLUMN settled_amount NUMERIC(15, 2);
ALTER TABLE claims ADD COLUMN settlement_date DATE;
ALTER TABLE claims ADD COLUMN settlement_reference VARCHAR(100);
ALTER TABLE claims ADD COLUMN submission_key VARCHAR(64);
ALTER TABLE claims ADD COLUMN closed_at TIMESTAMP WITH TIME ZONE;

UPDATE claims SET
    claim_type = (SELECT p.product_type FROM policies p WHERE p.id = claims.policy_id),
    market = (SELECT p.market FROM policies p WHERE p.id = claims.policy_id),
    currency = (SELECT p.currency FROM policies p WHERE p.id = claims.policy_id);

ALTER TABLE claims ALTER COLUMN claim_type DROP DEFAULT;
ALTER TABLE claims ALTER COLUMN market DROP DEFAULT;
ALTER TABLE claims ALTER COLUMN currency DROP DEFAULT;

ALTER TABLE claims DROP CONSTRAINT ck_claims_status;
ALTER TABLE claims ADD CONSTRAINT ck_claims_status CHECK (status IN
    ('SUBMITTED', 'UNDER_REVIEW', 'INFO_REQUESTED', 'APPROVED', 'REJECTED', 'SETTLED', 'WITHDRAWN'));
ALTER TABLE claims ADD CONSTRAINT ck_claims_type CHECK (claim_type IN ('MOTOR', 'HEALTH', 'TRAVEL', 'PROPERTY'));
ALTER TABLE claims ADD CONSTRAINT ck_claims_market
    CHECK (market IN ('AUSTRALIA', 'SINGAPORE', 'HONG_KONG', 'INDIA', 'MALAYSIA', 'THAILAND'));
ALTER TABLE claims ADD CONSTRAINT ck_claims_currency
    CHECK (currency IN ('AUD', 'SGD', 'HKD', 'INR', 'MYR', 'THB', 'USD'));
ALTER TABLE claims ADD CONSTRAINT ck_claims_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'));
ALTER TABLE claims ADD CONSTRAINT ck_claims_settled_amount CHECK (settled_amount IS NULL OR settled_amount > 0);
-- Idempotent submission: one claim per (claimant, client-supplied Idempotency-Key). NULL keys are not constrained.
ALTER TABLE claims ADD CONSTRAINT uk_claims_submission_key UNIQUE (claimant_id, submission_key);

CREATE INDEX idx_claims_market ON claims (market);
CREATE INDEX idx_claims_created_at ON claims (created_at);

-- 5. Assignment history (claims.assigned_officer_id holds the current officer).
CREATE TABLE claim_assignments (
    id          UUID                     PRIMARY KEY,
    claim_id    UUID                     NOT NULL,
    officer_id  UUID                     NOT NULL,
    assigned_by UUID                     NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_assignments_claim FOREIGN KEY (claim_id) REFERENCES claims (id) ON DELETE CASCADE,
    CONSTRAINT fk_assignments_officer FOREIGN KEY (officer_id) REFERENCES users (id),
    CONSTRAINT fk_assignments_assigned_by FOREIGN KEY (assigned_by) REFERENCES users (id)
);
CREATE INDEX idx_assignments_claim ON claim_assignments (claim_id, assigned_at);
CREATE INDEX idx_assignments_officer ON claim_assignments (officer_id);

-- 6. Officer assessment notes.
CREATE TABLE claim_assessments (
    id                 UUID                     PRIMARY KEY,
    claim_id           UUID                     NOT NULL,
    officer_id         UUID                     NOT NULL,
    notes              VARCHAR(4000)            NOT NULL,
    recommended_amount NUMERIC(15, 2),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_assessments_claim FOREIGN KEY (claim_id) REFERENCES claims (id) ON DELETE CASCADE,
    CONSTRAINT fk_assessments_officer FOREIGN KEY (officer_id) REFERENCES users (id),
    CONSTRAINT ck_assessments_amount CHECK (recommended_amount IS NULL OR recommended_amount >= 0)
);
CREATE INDEX idx_assessments_claim ON claim_assessments (claim_id, created_at);

-- 7. The approve/reject decision (at most one per claim).
CREATE TABLE claim_decisions (
    id         UUID                     PRIMARY KEY,
    claim_id   UUID                     NOT NULL,
    outcome    VARCHAR(10)              NOT NULL,
    amount     NUMERIC(15, 2),
    reason     VARCHAR(1000),
    decided_by UUID                     NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_decisions_claim UNIQUE (claim_id),
    CONSTRAINT fk_decisions_claim FOREIGN KEY (claim_id) REFERENCES claims (id) ON DELETE CASCADE,
    CONSTRAINT fk_decisions_decided_by FOREIGN KEY (decided_by) REFERENCES users (id),
    CONSTRAINT ck_decisions_outcome CHECK (outcome IN ('APPROVED', 'REJECTED')),
    CONSTRAINT ck_decisions_content CHECK (
        (outcome = 'APPROVED' AND amount IS NOT NULL) OR (outcome = 'REJECTED' AND reason IS NOT NULL))
);

-- 8. Requests for additional information and the claimant's responses.
CREATE TABLE claim_information_requests (
    id           UUID                     PRIMARY KEY,
    claim_id     UUID                     NOT NULL,
    requested_by UUID                     NOT NULL,
    message      VARCHAR(2000)            NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    response     VARCHAR(4000),
    responded_by UUID,
    responded_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_info_requests_claim FOREIGN KEY (claim_id) REFERENCES claims (id) ON DELETE CASCADE,
    CONSTRAINT fk_info_requests_requested_by FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT fk_info_requests_responded_by FOREIGN KEY (responded_by) REFERENCES users (id)
);
CREATE INDEX idx_info_requests_claim ON claim_information_requests (claim_id, requested_at);
