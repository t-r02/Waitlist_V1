# Waitlist & Notification Platform

A microservices-based waitlist management system with event-driven architecture using Kafka.

## Architecture

### Services

1. **Ingestion Service** (Port 8081)
   - Public signup API
   - Deduplication & normalization
   - Referral tracking & leaderboard
   - Rate limiting with Bucket4j

2. **Admin Service** (Port 8082)
   - Entry management (list, filter, update)
   - Bulk operations with partial success (207 Multi-Status)
   - State machine for legal transitions
   - JWT authentication
   - Audit logging

3. **Notification Service** (Port 8083)
   - Event-driven email notifications
   - Idempotency checks
   - Thymeleaf templates
   - Dead Letter Topic (DLT) handling

### Infrastructure

- **Kafka**: Message broker for inter-service communication
- **Redis**: Leaderboard caching
- **MailHog**: Local SMTP server for testing emails
- **H2**: In-memory databases (separate per service)

## Setup

### Prerequisites

- Java 17+
- Gradle (wrapper included)
- Docker & Docker Compose

### Start Infrastructure

```bash
docker-compose up -d
```

This starts:
- Kafka (localhost:9092)
- Zookeeper (localhost:2181)
- Redis (localhost:6379)
- MailHog SMTP (localhost:1025) & UI (http://localhost:8025)

### Build & Run Services

```bash
# Ingestion Service
cd ingestion-service
../gradlew bootRun

# Admin Service
cd admin-service
../gradlew bootRun

# Notification Service
cd notification-service
../gradlew bootRun
```

Or use the batch script:
```bash
start-all.bat
```

## API Usage

### Public Signup

```bash
curl -X POST http://localhost:8081/api/public/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "name": "John Doe",
    "company": "Acme Inc",
    "referralCode": "abc123"
  }'
```

Response:
```json
{
  "message": "Successfully registered",
  "referralCode": "xyz789",
  "duplicate": false
}
```

### Leaderboard

```bash
curl http://localhost:8081/api/public/leaderboard
```

### Admin Login

```bash
curl -X POST http://localhost:8082/api/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### List Entries

```bash
# All entries
curl http://localhost:8082/api/admin/entries \
  -H "Authorization: Bearer <token>"

# Filter by status
curl http://localhost:8082/api/admin/entries?status=PENDING \
  -H "Authorization: Bearer <token>"
```

### Update Single Entry

```bash
curl -X PATCH http://localhost:8082/api/admin/entries/1?status=APPROVED \
  -H "Authorization: Bearer <token>"
```

### Bulk Update

```bash
curl -X POST http://localhost:8082/api/admin/entries/bulk \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "ids": [1, 2, 3],
    "newStatus": "APPROVED"
  }'
```

Response (207 Multi-Status if partial failure):
```json
{
  "successCount": 2,
  "failures": ["ID 3: Invalid transition"]
}
```

## State Machine

Valid transitions:
- PENDING → APPROVED, REJECTED
- APPROVED → INVITED, REJECTED
- REJECTED → PENDING
- INVITED → (terminal state)

## Features

### Level 1 - Capture & Triage
✅ Public signup with deduplication (email normalization)
✅ Double-click protection (idempotent)
✅ Admin list, filter, inspect entries
✅ Individual & batch status updates
✅ State machine with legal transitions
✅ Bulk operations with partial success reporting
✅ Seeded admin user (admin/admin123)

### Level 2 - Microservices
✅ Three independent services
✅ Kafka event broker
✅ Separate databases per service
✅ Event-driven notifications
✅ MailHog integration
✅ Referral system with points & badges
✅ Leaderboard (Redis-backed)

## Kafka Topics

- `waitlist.signup` - New signups
- `waitlist.status-changed` - Status updates
- `*.dlt` - Dead letter topics for failed messages

## Email Templates

View sent emails at: http://localhost:8025

Templates:
- `confirmation.html` - Welcome email with referral code
- `invitation.html` - Status change notifications

## Referral System

- Each signup gets a unique referral code
- Referrer earns 10 points per successful referral
- Badges: BRONZE (5+), SILVER (20+), GOLD (50+)
- Leaderboard shows top 10 referrers

## Design Decisions

### Deduplication
- Email normalized to lowercase and trimmed
- Unique constraint on email column
- Returns existing referral code for duplicates

### Rate Limiting
- Bucket4j: 100 requests/minute per service instance
- Returns 429 Too Many Requests when exceeded

### Partial Success
- Bulk operations return 207 Multi-Status
- Response includes successCount and failures array
- Each failure includes ID and reason

### Idempotency
- Notifications use composite event keys
- Prevents duplicate emails on replay
- NotificationLog tracks sent messages

### Authentication
- JWT tokens (24h expiry)
- Hardcoded secret for demo (use env vars in prod)
- Seeded admin user on startup

## Monitoring

- MailHog UI: http://localhost:8025
- H2 Consoles (if enabled): http://localhost:808{1,2,3}/h2-console

## Production Considerations

- Replace H2 with PostgreSQL/MySQL
- Externalize JWT secret to env vars
- Add proper retry policies with exponential backoff
- Implement distributed rate limiting (Redis)
- Add observability (Prometheus, Grafana)
- Use proper secret management (AWS Secrets Manager)
- Add API gateway for routing
- Implement circuit breakers (Resilience4j)
