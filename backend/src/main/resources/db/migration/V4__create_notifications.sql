CREATE TABLE notifications (
    id         UUID                     PRIMARY KEY,
    user_id    UUID                     NOT NULL,
    claim_id   UUID                     NOT NULL,
    event_id   UUID                     NOT NULL,
    type       VARCHAR(40)              NOT NULL,
    message    VARCHAR(500)             NOT NULL,
    is_read    BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_notifications_event_user UNIQUE (event_id, user_id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_notifications_claim FOREIGN KEY (claim_id) REFERENCES claims (id) ON DELETE CASCADE
);

CREATE INDEX idx_notifications_user ON notifications (user_id, is_read, created_at DESC);
