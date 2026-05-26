# Quick Reference Card

## 🚀 Start Everything

```bash
# Start infrastructure
docker-compose up -d

# Start all services (Windows)
start-all.bat

# Or manually
cd ingestion-service && ../gradlew bootRun  # Terminal 1
cd admin-service && ../gradlew bootRun      # Terminal 2
cd notification-service && ../gradlew bootRun # Terminal 3
```

## 🔗 Service URLs

| Service | Port | URL |
|---------|------|-----|
| Ingestion API | 8081 | http://localhost:8081 |
| Admin API | 8082 | http://localhost:8082 |
| Notification | 8083 | http://localhost:8083 |
| MailHog UI | 8025 | http://localhost:8025 |
| Kafka | 9092 | localhost:9092 |
| Redis | 6379 | localhost:6379 |

## 📝 Common Commands

### Signup
```bash
curl -X POST http://localhost:8081/api/public/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","name":"John"}'
```

### Login (Admin)
```bash
curl -X POST http://localhost:8082/api/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### List Entries
```bash
curl http://localhost:8082/api/admin/entries \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Update Status
```bash
curl -X PATCH http://localhost:8082/api/admin/entries/1?status=APPROVED \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Bulk Update
```bash
curl -X POST http://localhost:8082/api/admin/entries/bulk \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"ids":[1,2,3],"newStatus":"APPROVED"}'
```

### Leaderboard
```bash
curl http://localhost:8081/api/public/leaderboard
```

## 🔄 State Transitions

```
PENDING ──→ APPROVED ──→ INVITED (terminal)
   │           │
   │           └──→ REJECTED
   │
   └──→ REJECTED ──→ PENDING
```

## 🎯 Default Credentials

- **Username**: admin
- **Password**: admin123

## 📊 Kafka Topics

- `waitlist.signup` - New signups
- `waitlist.status-changed` - Status updates
- `*.dlt` - Dead letter topics

## 🏆 Referral Badges

- **BRONZE**: 5+ points
- **SILVER**: 20+ points
- **GOLD**: 50+ points
- **Points per referral**: 10

## 🛑 Stop Everything

```bash
# Stop services (Ctrl+C in each terminal)

# Stop infrastructure
docker-compose down
```

## 🔍 Troubleshooting

| Issue | Solution |
|-------|----------|
| Port already in use | Check if services are already running |
| Kafka connection error | Wait 10-15s after starting docker-compose |
| No emails in MailHog | Check notification service logs |
| JWT expired | Login again to get new token |
| Invalid transition | Check state machine rules |

## 📦 Build All Services

```bash
./gradlew build
```

## 🧪 Quick Test Flow

1. Start infrastructure: `docker-compose up -d`
2. Start services: `start-all.bat`
3. Signup: `POST /api/public/signup`
4. Check email: http://localhost:8025
5. Login: `POST /api/admin/auth/login`
6. List entries: `GET /api/admin/entries`
7. Approve: `PATCH /api/admin/entries/1?status=APPROVED`
8. Check email again

## 📚 Documentation Files

- `README.md` - Overview & setup
- `TESTING.md` - Test scenarios
- `ARCHITECTURE.md` - System design
- `SUMMARY.md` - Implementation details
- `postman-collection.json` - API collection

## 🎨 HTTP Status Codes

- `200` - Success
- `207` - Multi-Status (partial success)
- `400` - Bad request (validation error)
- `401` - Unauthorized (invalid credentials)
- `429` - Too many requests (rate limit)
- `500` - Server error (invalid transition, etc.)
