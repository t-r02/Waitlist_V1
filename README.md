# Waitlist

Three Spring Boot microservices (Java 21) that handle public signups, admin triage, and transactional email — wired together with Kafka, backed by PostgreSQL, and cached in Redis. Built as a learning project to practise event-driven design at a scale small enough to fit in a laptop `docker compose up`.

---

## Architecture

```
                        ┌─────────────────────────────────────────┐
                        │            ingestion-service :8081       │
                        │                                          │
  POST /api/public/  ──▶│  SignupController                        │
  signup                │    └─ SignupService                      │
                        │         └─ SignupPersistenceService ─────┼──▶ PostgreSQL
                        │              └─ OutboxEntry (DB write)   │     waitlist_entry
                        │                                          │     outbox_entry
                        │  ┌─ OutboxPublisher (@Scheduled 500 ms) ─┼──▶ Kafka
                        │  │   FOR UPDATE SKIP LOCKED              │   waitlist.signup
                        │  │                                       │
                        │  └─ StatusChangedConsumer ───────────────┼◀── Kafka
                        │       awards/reverses referral points    │   waitlist.status-changed
                        │       syncs Redis leaderboard            │
                        └─────────────────────────────────────────┘
                                           │
              ┌────────────────────────────┴────────────────────────┐
              ▼                                                      ▼
 ┌────────────────────────────┐                    ┌────────────────────────────────┐
 │   admin-service :8082      │                    │  notification-service :8083    │
 │                            │                    │                                │
 │  SignupEventConsumer ───────┼◀── waitlist.signup │  SignupEventConsumer           │
 │   projects signup into     │                    │   sends confirmation email     │
 │   local WaitlistEntry      │                    │                                │
 │                            │                    │  StatusChangedConsumer         │
 │  AdminEntryController      │                    │   sends invitation email       │
 │   PATCH /{id}?status=  ────┼──▶ PostgreSQL      │                                │
 │   POST  /bulk          ────┼──▶ StatusAuditLog  │  DltHandler                   │
 │                            │                    │   dead-letter sink + log       │
 │  OutboxPublisher ──────────┼──▶ Kafka           │                                │
 │   (@Scheduled 500 ms)      │   waitlist.        │  PostgreSQL: notification_log  │
 │                            │   status-changed   │  (eventId idempotency key)     │
 └────────────────────────────┘                    └────────────────────────────────┘
              │
              ▼
        MailHog :8025
        (local SMTP)
```

**Event flow in one sentence:** every state change in admin is written to the DB outbox first, then published by the poller — so Kafka never sees a signup or status change that wasn't already durably committed to the database.

---

## Run it

```bash
./run.sh       # docker compose up + gradle bootRun for all three services
./demo.sh      # fires a signup, approves it, and tails MailHog for the email
```

Services start on `:8081` (ingestion), `:8082` (admin), `:8083` (notification).  
MailHog UI at `http://localhost:8025`.

### Quick API reference

```bash
# Sign up
curl -s -X POST http://localhost:8081/api/public/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","name":"Alice","referralCode":""}'

# Sign up via referral
curl -s -X POST http://localhost:8081/api/public/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"bob@example.com","name":"Bob","referralCode":"<alice-code>"}'

# Leaderboard (all-time)
curl http://localhost:8081/api/public/leaderboard?window=all

# Leaderboard (current ISO week)
curl http://localhost:8081/api/public/leaderboard?window=week

# Admin login → get JWT
TOKEN=$(curl -s -X POST http://localhost:8082/api/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .token)

# List all entries
curl -H "Authorization: Bearer $TOKEN" http://localhost:8082/api/admin/entries

# Filter by status
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8082/api/admin/entries?status=PENDING"

# Approve a single entry
curl -s -X PATCH \
  "http://localhost:8082/api/admin/entries/1?status=APPROVED" \
  -H "Authorization: Bearer $TOKEN"

# Bulk approve (207 Multi-Status on partial failure)
curl -s -X POST http://localhost:8082/api/admin/entries/bulk \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"ids":[1,2,3],"newStatus":"APPROVED"}'
```

---

## Design decisions

### Kafka over RabbitMQ or Redis Streams
Kafka's log gives the admin projection and the notification service independent consumer groups that can replay from offset 0 after a bug fix or deployment gap. With a queue you get one delivery; with Kafka you get a rewindable history. The tradeoff is operational weight — Kafka requires Zookeeper (or KRaft) and topic pre-provisioning — which matters less once you're running Docker Compose anyway.

### Transactional Outbox over direct publish
Writing to `outbox_entry` in the same transaction as `waitlist_entry` means the DB commit is the source of truth: either both the signup row and the event land, or neither does. The poller (`FOR UPDATE SKIP LOCKED`, 500 ms interval) then publishes and marks the row sent. The cost is ~500 ms of added event latency and a permanent scheduled thread. The alternative — publishing to Kafka inside the transaction — risks a partial failure where the DB rolls back but the message is already in the broker.

### At-least-once delivery + eventId idempotency
Both notification-service and admin-service key their idempotency on `eventId` (a UUID set by the publisher). Duplicate delivery on retry causes a unique-constraint violation on `notification_log.event_id` / `referral_event_log.event_id`, which the consumer catches and ignores. The tradeoff versus exactly-once: retried messages still do redundant DB reads; but the alternative (Kafka transactions + `enable.idempotence`) would lock us into Kafka-native producers only and complicate the outbox pattern.

### Dead Letter Topic + bounded retries
After `spring.kafka.consumer.max-retries` attempts (default: 3) with exponential backoff, poison messages move to `waitlist.signup.dlt` / `waitlist.status-changed.dlt`. The `DltHandler` in notification-service logs them for human inspection. The tradeoff: a bad message is never retried past the bound, so transient infra blips that outlast the retry window will need a manual replay. The upside is partition forward-progress — one bad record can't hold up every record behind it.

### State machine lives in admin-service only
Ingestion is append-only: it accepts signups and tracks referrals, but has no opinion on status. Admin is the single source of truth for `PENDING → APPROVED → INVITED` (or `REJECTED → PENDING`). This prevents split-brain where two services disagree on a legal transition. The tradeoff is that ingestion must subscribe to `waitlist.status-changed` to award referral points, rather than reacting to a local state change.

### Points awarded on APPROVED, not on signup
Referral points (10 pts per referee) fire when the referee reaches `APPROVED`, not when they sign up. Points are reversed if an approved entry is subsequently rejected. This means the leaderboard reflects real conversions, not invite-spam. The tradeoff is that a referrer waits for admin action before seeing their score move — acceptable for a waitlist, wrong for a SaaS activation funnel.

### Per-IP rate limit + honeypot, not CAPTCHA
The signup endpoint enforces 10 requests/IP/min (Bucket4j + Caffeine) and a global 1000 req/min ceiling. The `website` field is a honeypot — bots fill it, real browsers leave it blank. No CAPTCHA because this is a research project: the friction cost of CAPTCHA on real users outweighs the risk of bot signups in a closed waitlist. Fingerprinting (SHA-256 IP hash, 5 referrals/IP/24 h threshold) catches coordinated self-referral without ever storing raw IPs.

---

## What I'd add given another week

- **OpenAPI spec** — annotate controllers with `springdoc-openapi` so the contract is machine-readable and the Swagger UI replaces most of this curl reference.
- **Distributed tracing** — add OpenTelemetry agent, export spans to Tempo/Jaeger. The `X-Correlation-Id` header is already propagated via MDC; OTel would close the gap for Kafka hops.
- **Proper RBAC for admin** — right now any valid JWT can do anything. A role claim (`ROLE_VIEWER` vs `ROLE_OPERATOR`) with method-level `@PreAuthorize` would be a one-afternoon addition.
- **Debezium CDC** instead of the polling outbox — replace `OutboxPublisher` with a Debezium connector that tails the Postgres WAL. Eliminates the 500 ms polling latency, removes the scheduled thread, and makes the outbox table append-only (no `published` flag needed).
- **Email hash partitioning** — key Kafka messages by `email` so all events for the same user land on the same partition and arrive in order. Currently the partition key is unset, so signup and approval events for the same user can arrive out of order at the notification consumer.
