CREATE TABLE policies (
    id              UUID                     PRIMARY KEY,
    policy_number   VARCHAR(30)              NOT NULL,
    holder_id       UUID                     NOT NULL,
    product_type    VARCHAR(20)              NOT NULL,
    coverage_amount NUMERIC(15, 2)           NOT NULL,
    deductible      NUMERIC(15, 2)           NOT NULL DEFAULT 0,
    start_date      DATE                     NOT NULL,
    end_date        DATE                     NOT NULL,
    status          VARCHAR(20)              NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_policies_number UNIQUE (policy_number),
    CONSTRAINT fk_policies_holder FOREIGN KEY (holder_id) REFERENCES users (id),
    CONSTRAINT ck_policies_product CHECK (product_type IN ('MOTOR', 'HEALTH', 'TRAVEL', 'PROPERTY')),
    CONSTRAINT ck_policies_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_policies_coverage CHECK (coverage_amount > 0),
    CONSTRAINT ck_policies_deductible CHECK (deductible >= 0),
    CONSTRAINT ck_policies_period CHECK (end_date > start_date)
);

CREATE INDEX idx_policies_holder ON policies (holder_id);
