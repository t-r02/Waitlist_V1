# Testing Guide

## Quick Start

1. **Start Infrastructure**
   ```bash
   docker-compose up -d
   ```

2. **Start Services** (in separate terminals)
   ```bash
   # Terminal 1
   cd ingestion-service && ../gradlew bootRun
   
   # Terminal 2
   cd admin-service && ../gradlew bootRun
   
   # Terminal 3
   cd notification-service && ../gradlew bootRun
   ```

   Or use the batch script:
   ```bash
   start-all.bat
   ```

## Test Scenarios

### Scenario 1: Basic Signup Flow

1. **Sign up a user**
   ```bash
   curl -X POST http://localhost:8081/api/public/signup \
     -H "Content-Type: application/json" \
     -d '{"email":"alice@example.com","name":"Alice"}'
   ```
   
   Expected: 200 OK with referral code

2. **Check email in MailHog**
   - Open http://localhost:8025
   - Verify confirmation email received

3. **Try duplicate signup**
   ```bash
   curl -X POST http://localhost:8081/api/public/signup \
     -H "Content-Type: application/json" \
     -d '{"email":"alice@example.com","name":"Alice"}'
   ```
   
   Expected: Same referral code, duplicate=true

### Scenario 2: Email Normalization

1. **Sign up with different casing**
   ```bash
   curl -X POST http://localhost:8081/api/public/signup \
     -H "Content-Type: application/json" \
     -d '{"email":"ALICE@EXAMPLE.COM","name":"Alice"}'
   ```
   
   Expected: Duplicate detected (normalized to lowercase)

2. **Sign up with whitespace**
   ```bash
   curl -X POST http://localhost:8081/api/public/signup \
     -H "Content-Type: application/json" \
     -d '{"email":" alice@example.com ","name":"Alice"}'
   ```
   
   Expected: Duplicate detected (trimmed)

### Scenario 3: Referral System

1. **Sign up first user**
   ```bash
   curl -X POST http://localhost:8081/api/public/signup \
     -H "Content-Type: application/json" \
     -d '{"email":"bob@example.com","name":"Bob"}'
   ```
   
   Note the referralCode (e.g., "abc12345")

2. **Sign up with referral**
   ```bash
   curl -X POST http://localhost:8081/api/public/signup \
     -H "Content-Type: application/json" \
     -d '{"email":"charlie@example.com","name":"Charlie","referralCode":"abc12345"}'
   ```

3. **Check leaderboard**
   ```bash
   curl http://localhost:8081/api/public/leaderboard
   ```
   
   Expected: Bob has 10 points

4. **Add more referrals to earn badges**
   - 5+ points = BRONZE
   - 20+ points = SILVER
   - 50+ points = GOLD

### Scenario 4: Admin Operations

1. **Login as admin**
   ```bash
   curl -X POST http://localhost:8082/api/admin/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"admin123"}'
   ```
   
   Save the token from response

2. **List all entries**
   ```bash
   curl http://localhost:8082/api/admin/entries \
     -H "Authorization: Bearer YOUR_TOKEN"
   ```

3. **Filter by status**
   ```bash
   curl http://localhost:8082/api/admin/entries?status=PENDING \
     -H "Authorization: Bearer YOUR_TOKEN"
   ```

4. **Approve an entry**
   ```bash
   curl -X PATCH http://localhost:8082/api/admin/entries/1?status=APPROVED \
     -H "Authorization: Bearer YOUR_TOKEN"
   ```
   
   Check MailHog for status update email

5. **Invite approved user**
   ```bash
   curl -X PATCH http://localhost:8082/api/admin/entries/1?status=INVITED \
     -H "Authorization: Bearer YOUR_TOKEN"
   ```

### Scenario 5: State Machine Validation

1. **Try invalid transition**
   ```bash
   # Try to invite a pending user (should fail)
   curl -X PATCH http://localhost:8082/api/admin/entries/2?status=INVITED \
     -H "Authorization: Bearer YOUR_TOKEN"
   ```
   
   Expected: 500 with "Invalid transition"

2. **Valid transitions**
   - PENDING → APPROVED ✓
   - APPROVED → INVITED ✓
   - PENDING → REJECTED ✓
   - REJECTED → PENDING ✓
   - INVITED → (none) ✗

### Scenario 6: Bulk Operations

1. **Create multiple entries**
   ```bash
   for i in {1..5}; do
     curl -X POST http://localhost:8081/api/public/signup \
       -H "Content-Type: application/json" \
       -d "{\"email\":\"user$i@example.com\",\"name\":\"User $i\"}"
   done
   ```

2. **Bulk approve**
   ```bash
   curl -X POST http://localhost:8082/api/admin/entries/bulk \
     -H "Authorization: Bearer YOUR_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"ids":[1,2,3],"newStatus":"APPROVED"}'
   ```
   
   Expected: successCount=3, failures=[]

3. **Bulk with partial failure**
   ```bash
   # Try to invite mix of pending and approved
   curl -X POST http://localhost:8082/api/admin/entries/bulk \
     -H "Authorization: Bearer YOUR_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"ids":[1,2,4],"newStatus":"INVITED"}'
   ```
   
   Expected: 207 Multi-Status with some failures

### Scenario 7: Rate Limiting

1. **Rapid fire requests**
   ```bash
   for i in {1..150}; do
     curl -X POST http://localhost:8081/api/public/signup \
       -H "Content-Type: application/json" \
       -d "{\"email\":\"spam$i@example.com\"}"
   done
   ```
   
   Expected: Some requests return 429 after 100 requests

### Scenario 8: Idempotency

1. **Sign up user**
   ```bash
   curl -X POST http://localhost:8081/api/public/signup \
     -H "Content-Type: application/json" \
     -d '{"email":"test@example.com","name":"Test"}'
   ```

2. **Check MailHog** - 1 email

3. **Restart notification service**
   - Kafka will replay the message

4. **Check MailHog again** - Still 1 email (idempotent)

### Scenario 9: Event Flow

1. **Sign up** → Kafka event → Notification email + Admin sync
2. **Status change** → Kafka event → Notification email
3. **Check all services have data**
   - Ingestion: Has entry with referral code
   - Admin: Has synced entry
   - Notification: Has log entries

## Verification Checklist

- [ ] Signup creates entry in ingestion DB
- [ ] Signup syncs to admin DB via Kafka
- [ ] Signup sends confirmation email
- [ ] Duplicate detection works (case-insensitive)
- [ ] Referral tracking awards points
- [ ] Leaderboard shows correct rankings
- [ ] Admin login returns JWT token
- [ ] Admin can list/filter entries
- [ ] Status updates respect state machine
- [ ] Status changes send notification emails
- [ ] Bulk operations return 207 on partial failure
- [ ] Rate limiting blocks excessive requests
- [ ] Emails are idempotent (no duplicates on replay)
- [ ] Audit log tracks all status changes

## Troubleshooting

### Services won't start
- Check if ports 8081-8083 are available
- Verify Kafka is running: `docker ps`

### No emails in MailHog
- Check notification service logs
- Verify Kafka topics exist: `docker exec -it <kafka-container> kafka-topics --list --bootstrap-server localhost:9092`

### JWT authentication fails
- Token expires after 24h
- Get new token via /api/admin/auth/login

### Kafka connection errors
- Ensure docker-compose is running
- Wait 10-15 seconds after starting Kafka

## Clean Up

```bash
# Stop services (Ctrl+C in each terminal)

# Stop infrastructure
docker-compose down

# Clean databases (H2 in-memory, auto-cleaned on restart)
```
