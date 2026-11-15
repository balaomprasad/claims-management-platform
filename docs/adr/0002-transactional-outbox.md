# ADR 0002: Transactional outbox

**Status:** Accepted. This supersedes the first version, which published after commit.

**Context.** The first version published to Kafka from an after-commit listener. If Kafka was unavailable at that moment, the event was lost, even though the claim change had committed (a dual-write problem).

**Decision.** Write each event to `outbox_events` in the same transaction as the claim change. `OutboxRelay` polls for unpublished rows every second:
- It locks them with `PESSIMISTIC_WRITE` and lock timeout -2, which is `SKIP LOCKED` on PostgreSQL.
- It publishes them in creation order and marks them as published.
- If a send fails, it stops the batch so later events for the same claim don't overtake the failed one.

**Consequences.**
- Events exist if and only if the business change committed.
- Delivery is **at least once**. A crash between the broker acknowledging a send and the row being updated causes a re-send, so consumers must be (and are) idempotent on `eventId`.
- There's up to about 1 second of extra latency, which is fine for notifications.
- Published rows are kept for traceability. A retention job, or a change-data-capture tool such as Debezium, would be the production next step.
