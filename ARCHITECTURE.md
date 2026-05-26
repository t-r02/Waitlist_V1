# Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         WAITLIST PLATFORM                                │
└─────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐                    ┌──────────────────┐
│  Landing Page    │                    │  Admin Portal    │
│  (External)      │                    │  (Internal)      │
└────────┬─────────┘                    └────────┬─────────┘
         │                                       │
         │ POST /signup                          │ JWT Auth
         │ GET /leaderboard                      │ GET/PATCH /entries
         │                                       │ POST /bulk
         ▼                                       ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           API LAYER                                      │
├─────────────────────────────┬───────────────────────────────────────────┤
│  INGESTION SERVICE :8081    │    ADMIN SERVICE :8082                    │
│  ┌──────────────────────┐   │    ┌──────────────────────┐              │
│  │ SignupController     │   │    │ AdminEntryController │              │
│  │ - Rate Limiting      │   │    │ - JWT Filter         │              │
│  │ - Validation         │   │    │ - List/Filter        │              │
│  └──────────┬───────────┘   │    │ - Update Status      │              │
│             │                │    │ - Bulk Operations    │              │
│             ▼                │    └──────────┬───────────┘              │
│  ┌──────────────────────┐   │               │                           │
│  │ SignupService        │   │    ┌──────────▼───────────┐              │
│  │ - Deduplication      │   │    │ EntryManagementSvc   │              │
│  │ - Normalization      │   │    │ - StateMachineGuard  │              │
│  │ - Persist Entry      │   │    │ - Audit Logging      │              │
│  └──────────┬───────────┘   │    └──────────┬───────────┘              │
│             │                │               │                           │
│             ▼                │               ▼                           │
│  ┌──────────────────────┐   │    ┌──────────────────────┐              │
│  │ ReferralService      │   │    │ BulkStatusService    │              │
│  │ - Track Referrals    │   │    │ - Partial Success    │              │
│  │ - Award Points       │   │    │ - 207 Multi-Status   │              │
│  │ - Update Badges      │   │    └──────────────────────┘              │
│  └──────────────────────┘   │                                           │
└─────────────┬───────────────┴───────────────┬───────────────────────────┘
              │                               │
              │ Publish Events                │ Publish Events
              ▼                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      KAFKA MESSAGE BROKER                                │
│  ┌────────────────────────┐      ┌────────────────────────┐            │
│  │ waitlist.signup        │      │ waitlist.status-changed│            │
│  │ - email                │      │ - email                │            │
│  │ - name                 │      │ - oldStatus            │            │
│  │ - referralCode         │      │ - newStatus            │            │
│  └────────────────────────┘      └────────────────────────┘            │
└─────────────┬───────────────────────────────┬───────────────────────────┘
              │                               │
              │ Subscribe                     │ Subscribe
              ▼                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    NOTIFICATION SERVICE :8083                            │
│  ┌──────────────────────┐      ┌──────────────────────┐                │
│  │ SignupEventConsumer  │      │ StatusChangedConsumer│                │
│  └──────────┬───────────┘      └──────────┬───────────┘                │
│             │                              │                             │
│             └──────────────┬───────────────┘                             │
│                            ▼                                             │
│                 ┌──────────────────────┐                                │
│                 │ EmailService         │                                │
│                 │ - Idempotency Check  │                                │
│                 │ - Template Rendering │                                │
│                 │ - Send via SMTP      │                                │
│                 └──────────┬───────────┘                                │
│                            │                                             │
└────────────────────────────┼─────────────────────────────────────────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │   MAILHOG       │
                    │   SMTP :1025    │
                    │   UI :8025      │
                    └─────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                         DATA LAYER                                       │
├─────────────────────────────┬───────────────────────────────────────────┤
│  INGESTION DB (H2)          │    ADMIN DB (H2)      │  NOTIFICATION DB  │
│  ┌──────────────────────┐   │    ┌──────────────┐  │  ┌──────────────┐ │
│  │ WaitlistEntry        │   │    │ WaitlistEntry│  │  │ NotificationLog│
│  │ - email (unique)     │   │    │ - email      │  │  │ - eventKey   │ │
│  │ - name               │   │    │ - status     │  │  │ - email      │ │
│  │ - status             │   │    │ - version    │  │  │ - type       │ │
│  │ - referralCode       │   │    └──────────────┘  │  │ - sentAt     │ │
│  │ - referredBy         │   │    ┌──────────────┐  │  └──────────────┘ │
│  └──────────────────────┘   │    │ AdminUser    │  │                    │
│  ┌──────────────────────┐   │    │ - username   │  │                    │
│  │ Referral             │   │    │ - password   │  │                    │
│  │ - referrerEmail      │   │    └──────────────┘  │                    │
│  │ - refereeEmail       │   │    ┌──────────────┐  │                    │
│  │ - converted          │   │    │ StatusAuditLog│ │                    │
│  └──────────────────────┘   │    │ - entryId    │  │                    │
│  ┌──────────────────────┐   │    │ - oldStatus  │  │                    │
│  │ ReferralPoints       │   │    │ - newStatus  │  │                    │
│  │ - email              │   │    │ - changedBy  │  │                    │
│  │ - points             │   │    │ - changedAt  │  │                    │
│  │ - badge              │   │    └──────────────┘  │                    │
│  └──────────────────────┘   │                      │                    │
└─────────────────────────────┴──────────────────────┴────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                      SUPPORTING SERVICES                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                  │
│  │ Redis :6379  │  │ Zookeeper    │  │ Kafka :9092  │                  │
│  │ (Leaderboard)│  │ :2181        │  │              │                  │
│  └──────────────┘  └──────────────┘  └──────────────┘                  │
└─────────────────────────────────────────────────────────────────────────┘

KEY DESIGN PATTERNS:
═══════════════════

1. EVENT-DRIVEN ARCHITECTURE
   - Services communicate via Kafka events
   - Loose coupling between services
   - Each service owns its data

2. SAGA PATTERN
   - Distributed transactions via events
   - Eventual consistency
   - Compensating transactions possible

3. IDEMPOTENCY
   - Duplicate detection in ingestion
   - Event key tracking in notifications
   - Safe message replay

4. STATE MACHINE
   - Legal transition enforcement
   - Audit trail for all changes
   - Prevents invalid state transitions

5. CQRS (Light)
   - Ingestion handles writes
   - Admin handles reads + status updates
   - Event sync keeps data consistent

6. CIRCUIT BREAKER (Implicit)
   - Kafka retry mechanisms
   - Dead Letter Topics for failures
   - Service isolation

DATA FLOW EXAMPLES:
═══════════════════

SIGNUP FLOW:
1. User submits form → Ingestion API
2. Ingestion validates, deduplicates, saves
3. Ingestion publishes "waitlist.signup" event
4. Admin service consumes event, syncs entry
5. Notification service consumes event, sends email

STATUS CHANGE FLOW:
1. Admin updates status → Admin API
2. Admin validates transition, saves, audits
3. Admin publishes "waitlist.status-changed" event
4. Notification service consumes event, sends email

REFERRAL FLOW:
1. User signs up with referral code
2. Ingestion creates Referral record
3. Ingestion awards points to referrer
4. Points trigger badge update
5. Leaderboard cached in Redis
