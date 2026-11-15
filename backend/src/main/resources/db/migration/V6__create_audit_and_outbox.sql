-- V6: Audit trail and transactional outbox.

CREATE TABLE audit_logs (
    id             UUID                     PRIMARY KEY,
    actor_id       UUID,
    action         VARCHAR(60)              NOT NULL,
    entity_type    VARCHAR(40)              NOT NULL,
    entity_id      VARCHAR(64)              NOT NULL,
    details        VARCHAR(2000),
    correlation_id VARCHAR(64),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES users (id)
);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id, created_at);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_id);
CREATE INDEX idx_audit_logs_created ON audit_logs (created_at);

-- Events are written here in the same transaction as the business change and relayed to Kafka afterwards.
CREATE TABLE outbox_events (
    id             UUID                     PRIMARY KEY,
    aggregate_type VARCHAR(40)              NOT NULL,
    aggregate_id   UUID                     NOT NULL,
    event_type     VARCHAR(60)              NOT NULL,
    topic          VARCHAR(200)             NOT NULL,
    message_key    VARCHAR(100)             NOT NULL,
    payload        VARCHAR(8000)            NOT NULL,
    correlation_id VARCHAR(64),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at   TIMESTAMP WITH TIME ZONE,
    attempts       INTEGER                  DEFAULT 0 NOT NULL,
    last_error     VARCHAR(1000)
);
CREATE INDEX idx_outbox_pending ON outbox_events (published_at, created_at);
