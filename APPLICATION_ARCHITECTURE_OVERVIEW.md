# Prayer-Chat AI Chatbot - Application Architecture Overview

**Document Version:** 1.0
**Last Updated:** December 2, 2025
**Status:** Production Architecture with Future Enhancements

---

## Executive Summary

Prayer-Chat is a sophisticated AI-powered chatbot platform built with Spring Boot, featuring Google OAuth 2.0 authentication, Stripe payment integration, and comprehensive security measures. This document provides a complete architectural overview for development, testing, and deployment planning.

---

## Table of Contents

1. [System Architecture](#system-architecture)
2. [Technology Stack](#technology-stack)
3. [Core Components](#core-components)
4. [Authentication & Authorization](#authentication--authorization)
5. [Payment Processing](#payment-processing)
6. [Security Features](#security-features)
7. [Data Architecture](#data-architecture)
8. [External Integrations](#external-integrations)
9. [API Endpoints](#api-endpoints)
10. [Future Enhancements](#future-enhancements)

---

## 1. System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend Layer                        │
│                     (React Application)                      │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTPS/REST
┌──────────────────────▼──────────────────────────────────────┐
│                    Spring Boot Backend                       │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │Controllers │  │  Services  │  │Security    │            │
│  │            │  │            │  │Filters     │            │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘            │
│        │                │                │                   │
│  ┌─────▼────────────────▼────────────────▼──────┐           │
│  │         Application Core Services             │           │
│  │  - AI Chat  - Audit  - Fraud Detection      │           │
│  └───────────────────────┬───────────────────────┘           │
└────────────────────────┬─┴────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
┌───────▼────────┐ ┌────▼─────┐  ┌──────▼────────┐
│   PostgreSQL   │ │  Redis   │  │External APIs  │
│  (Production)  │ │(Rate     │  │- Anthropic AI │
│   H2 (Dev)     │ │Limiting) │  │- Cohere       │
└────────────────┘ └──────────┘  │- Stripe       │
                                  │- Google OAuth │
                                  └───────────────┘
```

### Architecture Patterns

- **Layered Architecture:** Clear separation between controllers, services, and repositories
- **Dependency Injection:** Spring IoC container manages all beans
- **Repository Pattern:** JPA repositories abstract database access
- **Service Layer Pattern:** Business logic encapsulated in service classes
- **DTO Pattern:** Data Transfer Objects for API communication
- **Builder Pattern:** Used for complex object creation (e.g., AuditLog)
- **Strategy Pattern:** Multiple AI model implementations
- **Observer Pattern:** Event-driven audit logging

---

## 2. Technology Stack

### Backend Core
- **Framework:** Spring Boot 3.2.0
- **Language:** Java 17+
- **Build Tool:** Maven
- **Server:** Embedded Tomcat

### Spring Modules
- Spring Web (REST APIs)
- Spring Data JPA (Database access)
- Spring Security (Authentication & Authorization)
- Spring OAuth2 Client (Google login)
- Spring AI (Anthropic & Cohere)
- Spring Validation (Input validation)

### Database
- **Development:** H2 (In-memory)
- **Production:** PostgreSQL (Recommended)
- **Caching/Rate Limiting:** Redis (Future)

### External Services
- **AI Services:**
  - Anthropic Claude (Chat)
  - Cohere (Embeddings)
- **Authentication:** Google OAuth 2.0
- **Payment Processing:** Stripe
- **Monitoring:** SLF4J + Logback

### Security
- BCrypt (Password hashing)
- JWT (Token-based auth)
- OAuth 2.0 (SSO)
- HTTPS (Production)
- CORS (Cross-origin policies)
- Rate Limiting (Bucket4j)

---

## 3. Core Components

### 3.1 Controller Layer

**Purpose:** Handle HTTP requests and responses

**Controllers:**
1. **ChatbotController** - CRUD operations for chatbot management
2. **ChatController** - Chat message handling
3. **SubscriptionController** - Subscription management
4. **StripeWebhookController** - Stripe event processing
5. **AuditLogController** - Audit log access and export
6. **UserController** - User management (future)

**Responsibilities:**
- Request validation (Bean Validation)
- Response formatting
- Exception handling
- Security principal extraction
- HTTP status code management

### 3.2 Service Layer

**Purpose:** Business logic and orchestration

**Services:**

1. **AI Services**
   - `AiChatbotService` - Main chatbot logic
   - `BibleVerseService` - Specialized content

2. **Payment Services**
   - `StripeService` - Payment processing
   - `SubscriptionService` - Subscription management

3. **Security Services**
   - `AuditService` - Audit logging
   - `FraudDetectionService` - Fraud detection
   - `CustomOAuth2UserService` - OAuth user handling
   - `CustomUserDetailsService` - User authentication

4. **Content Services**
   - `WebsiteAnalysisService` - Website crawling
   - `ConversationExportService` - Data export

5. **Validation Services**
   - `UrlValidationService` - SSRF protection

**Responsibilities:**
- Business rule enforcement
- Transaction management
- External API integration
- Data transformation
- Error handling and recovery

### 3.3 Repository Layer

**Purpose:** Data access abstraction

**Repositories:**
- `UserRepository`
- `ChatbotRepository`
- `ConversationRepository`
- `MessageRepository`
- `SubscriptionRepository`
- `AuditLogRepository`
- `WebsiteContentRepository`

**Features:**
- JPA query methods
- Custom JPQL queries
- Pagination support
- Sorting capabilities
- Transaction support

### 3.4 Security Layer

**Components:**

1. **Filters**
   - `JwtAuthenticationFilter` - JWT token validation
   - `RateLimitingFilter` - Request throttling

2. **Providers**
   - `JwtTokenProvider` - JWT generation and validation

3. **Handlers**
   - `OAuth2AuthenticationSuccessHandler` - Post-login handling
   - Custom error handlers

4. **Configuration**
   - `SecurityConfig` - Security policies
   - CORS configuration
   - Content Security Policy

---

## 4. Authentication & Authorization

### Authentication Flow

```
User Login Request
      ↓
Google OAuth 2.0
      ↓
Authorization Code Exchange
      ↓
Google Access Token
      ↓
CustomOAuth2UserService
      ↓
User Created/Retrieved
      ↓
JWT Token Generated
      ↓
Session Established
```

### OAuth 2.0 Integration

**Provider:** Google

**Scopes:**
- email
- profile

**Endpoints:**
- Authorization URI: `https://accounts.google.com/o/oauth2/v2/auth`
- Token URI: `https://oauth2.googleapis.com/token`
- User Info URI: `https://www.googleapis.com/oauth2/v3/userinfo`

**Implementation:**
- `CustomOAuth2User` - Custom principal wrapper
- `CustomOAuth2UserService` - User provisioning
- Automatic account linking
- First-time user creation

### Authorization Model

**Resource Ownership:**
- Users can only access their own resources
- Chatbot ownership verified on every operation
- Subscription status checked for paid features

**Access Control Levels:**
1. **Public:** Landing pages (no auth)
2. **Authenticated:** Dashboard, settings
3. **Subscribed:** Chatbot creation and usage
4. **Admin:** System management (future)

---

## 5. Payment Processing

### Stripe Integration Architecture

```
User Initiates Payment
      ↓
Create Checkout Session
      ↓
Stripe Hosted Checkout
      ↓
Payment Success/Failure
      ↓
Webhook Event to Backend
      ↓
Process Event (Async)
      ↓
Update Subscription Status
      ↓
Grant/Revoke Access
```

### Subscription Plans

**Tiers:**
1. **FREE** - No access to chatbots
2. **BASIC** - $4.98/month (default)
3. **PRO** - Future pricing
4. **ENTERPRISE** - Future custom pricing

### Subscription States

| State | Description | Access |
|-------|-------------|--------|
| INACTIVE | No subscription | ❌ No access |
| TRIALING | Trial period | ✅ Full access |
| ACTIVE | Paid subscription | ✅ Full access |
| PAST_DUE | Payment failed, in grace period | ⚠️ Limited access |
| UNPAID | Grace period expired | ❌ No access |
| CANCELED | User canceled | ❌ No access |
| INCOMPLETE | Setup not finished | ❌ No access |

### Payment Features

**Current:**
- ✅ Checkout session creation
- ✅ Webhook signature verification
- ✅ Subscription lifecycle management
- ✅ Grace period (7 days)
- ✅ Payment retry logic (3 attempts)
- ✅ Plan upgrades (immediate, prorated)
- ✅ Plan downgrades (end of period)

**Future:**
- [ ] Multiple payment methods
- [ ] Invoice generation
- [ ] Refund processing
- [ ] Discount codes
- [ ] Annual billing

---

## 6. Security Features

### Implemented Security Measures

#### 6.1 Authentication Security
- OAuth 2.0 SSO (Google)
- JWT token-based sessions
- Secure token storage
- Token expiration (24 hours)
- Refresh token rotation (future)

#### 6.2 Authorization Security
- Resource ownership verification
- Subscription-based access control
- Role-based permissions (future)
- Multi-level authorization checks

#### 6.3 Input Validation
- Bean Validation annotations
- Message length limits (1-2000 chars)
- Pattern validation for IDs
- URL format validation
- SQL injection prevention (parameterized queries)
- XSS protection (input sanitization)

#### 6.4 SSRF Protection
- `UrlValidationService` blocks:
  - Localhost (127.0.0.1, ::1)
  - Private IPs (10.0.0.0/8, 192.168.0.0/16, 172.16.0.0/12)
  - Cloud metadata endpoints
  - Dangerous ports (22, 23, 3389)
  - Non-HTTP/HTTPS protocols

#### 6.5 Logging Security
- `LogSanitizer` for sensitive data redaction
- API key masking
- Password removal
- Email partial redaction
- IP address partial masking
- Log injection prevention

#### 6.6 HTTP Security Headers
- X-Frame-Options: SAMEORIGIN
- X-Content-Type-Options: nosniff
- X-XSS-Protection: 1; mode=block
- Strict-Transport-Security (HSTS)
- Content-Security-Policy (CSP)
- Referrer-Policy
- Permissions-Policy

#### 6.7 Rate Limiting
- Bucket4j implementation
- Per-user rate limits
- Brute force protection
- DoS mitigation

#### 6.8 Audit Trail
- Comprehensive event logging
- IP address tracking
- User agent capture
- Metadata support
- CSV/JSON export
- Date range filtering

#### 6.9 Fraud Detection
- Failed login monitoring (5 attempts/30 min)
- Payment failure detection (3 failures/7 days)
- Account takeover detection
- Subscription abuse detection
- Risk scoring (NONE/LOW/MEDIUM/HIGH/CRITICAL)

#### 6.10 Payment Security
- Stripe webhook signature verification
- PCI DSS compliance (via Stripe)
- API key environment storage
- HTTPS-only in production
- Secure checkout flow

---

## 7. Data Architecture

### Database Schema

#### Core Entities

**Users**
```sql
users
├── id (BIGINT, PK)
├── username (VARCHAR, UNIQUE)
├── email (VARCHAR, UNIQUE)
├── password (VARCHAR) -- BCrypt hashed
├── auth_provider (ENUM: LOCAL, GOOGLE)
├── google_id (VARCHAR)
├── created_at (TIMESTAMP)
├── updated_at (TIMESTAMP)
└── last_login (TIMESTAMP)
```

**Chatbots**
```sql
chatbots
├── id (BIGINT, PK)
├── owner_id (BIGINT, FK -> users)
├── name (VARCHAR)
├── description (TEXT)
├── system_prompt (TEXT)
├── language (VARCHAR)
├── website_url (VARCHAR)
├── is_active (BOOLEAN)
├── created_at (TIMESTAMP)
└── updated_at (TIMESTAMP)
```

**Subscriptions**
```sql
subscriptions
├── id (BIGINT, PK)
├── user_id (BIGINT, FK -> users, UNIQUE)
├── stripe_customer_id (VARCHAR)
├── stripe_subscription_id (VARCHAR)
├── stripe_price_id (VARCHAR)
├── status (ENUM: ACTIVE, PAST_DUE, etc.)
├── plan (ENUM: FREE, BASIC, PRO, ENTERPRISE)
├── current_period_start (TIMESTAMP)
├── current_period_end (TIMESTAMP)
├── canceled_at (TIMESTAMP)
├── payment_retry_count (INT)
├── last_payment_attempt (TIMESTAMP)
├── grace_period_end (TIMESTAMP)
├── created_at (TIMESTAMP)
└── updated_at (TIMESTAMP)
```

**Audit Logs**
```sql
audit_logs
├── id (BIGINT, PK)
├── user_id (BIGINT, FK -> users)
├── event_type (ENUM: 20+ types)
├── severity (ENUM: INFO, WARNING, ERROR, CRITICAL)
├── action (VARCHAR)
├── description (TEXT)
├── resource_type (VARCHAR)
├── resource_id (VARCHAR)
├── ip_address (VARCHAR)
├── user_agent (VARCHAR)
├── created_at (TIMESTAMP)
└── metadata (MAP<String, String>)

Indexes:
├── idx_user_id
├── idx_event_type
├── idx_created_at
└── idx_severity
```

**Conversations & Messages**
```sql
conversations
├── id (BIGINT, PK)
├── chatbot_id (BIGINT, FK -> chatbots)
├── session_id (VARCHAR)
├── started_at (TIMESTAMP)
└── last_message_at (TIMESTAMP)

messages
├── id (BIGINT, PK)
├── conversation_id (BIGINT, FK -> conversations)
├── sender (ENUM: USER, BOT)
├── content (TEXT)
├── language (VARCHAR)
└── timestamp (TIMESTAMP)
```

### Data Relationships

```
users (1) ─────────────> (*) chatbots
users (1) ─────────────> (1) subscription
users (1) ─────────────> (*) audit_logs
chatbots (1) ──────────> (*) conversations
chatbots (1) ──────────> (*) website_content
conversations (1) ─────> (*) messages
```

---

## 8. External Integrations

### 8.1 Anthropic AI (Claude)

**Purpose:** Conversational AI

**Model:** claude-3-haiku-20240307

**Configuration:**
- Temperature: 0.7
- Max tokens: 1000
- Base URL: https://api.anthropic.com

**Usage:**
- Chat responses
- Context-aware conversations
- Multilingual support

### 8.2 Cohere

**Purpose:** Text embeddings

**Model:** embed-multilingual-v3.0

**Configuration:**
- Dimensions: 1024
- Base URL: https://api.cohere.com

**Usage:**
- Website content vectorization
- Semantic search
- Context retrieval

### 8.3 Google OAuth 2.0

**Purpose:** Authentication

**Endpoints:**
- Authorization: https://accounts.google.com/o/oauth2/v2/auth
- Token: https://oauth2.googleapis.com/token
- User Info: https://www.googleapis.com/oauth2/v3/userinfo

**Configuration:**
- Client ID: Environment variable
- Client Secret: Environment variable
- Scopes: email, profile

### 8.4 Stripe

**Purpose:** Payment processing

**API Version:** Latest

**Configuration:**
- Secret Key: Environment variable
- Webhook Secret: Environment variable
- Price ID: Environment variable

**Events Handled:**
- customer.subscription.created
- customer.subscription.updated
- customer.subscription.deleted
- invoice.payment_succeeded
- invoice.payment_failed

---

## 9. API Endpoints

### Authentication
```
POST   /login/oauth2/code/google    - OAuth callback
GET    /api/user/profile             - Get current user
POST   /api/auth/logout              - Logout
```

### Chatbots
```
GET    /api/chatbots                 - List user's chatbots
GET    /api/chatbots/{id}            - Get chatbot details
POST   /api/chatbots                 - Create chatbot
PUT    /api/chatbots/{id}            - Update chatbot
DELETE /api/chatbots/{id}            - Delete chatbot
POST   /api/chatbots/{id}/analyze    - Analyze website
POST   /api/chatbots/{id}/index      - Index content
```

### Chat
```
POST   /api/chat/{embedCode}         - Send chat message
GET    /api/chat/{embedCode}/history - Get chat history
```

### Subscriptions
```
GET    /api/subscription/status      - Get subscription status
GET    /api/subscription/details     - Get subscription details
POST   /api/subscription/create-checkout-session - Create payment
POST   /api/subscription/cancel      - Cancel subscription
POST   /api/subscription/upgrade     - Upgrade plan
POST   /api/subscription/downgrade   - Downgrade plan
POST   /api/subscription/change-plan - Change plan (auto-detect)
```

### Webhooks
```
POST   /stripe/webhook               - Stripe events
```

### Audit Logs
```
GET    /api/audit                    - Get audit logs (paginated)
GET    /api/audit/export/csv         - Export as CSV
GET    /api/audit/export/json        - Export as JSON
GET    /api/audit/security-events    - Get security events
```

---

## 10. Future Enhancements

### Short-term (Next 3-6 months)
- [ ] Implement honeypot fields for bot detection
- [ ] API key rotation mechanism
- [ ] Real-time security event alerting
- [ ] Multi-factor authentication (MFA)
- [ ] Advanced Redis-based rate limiting
- [ ] WebSocket support for real-time chat

### Medium-term (6-12 months)
- [ ] Admin dashboard
- [ ] User management interface
- [ ] Advanced fraud detection with ML
- [ ] Subscription analytics dashboard
- [ ] Multiple AI model support
- [ ] Custom model fine-tuning
- [ ] White-label options

### Long-term (12+ months)
- [ ] SOC 2 compliance certification
- [ ] GDPR compliance tools
- [ ] Multi-tenancy support
- [ ] Kubernetes deployment
- [ ] Microservices architecture
- [ ] GraphQL API
- [ ] Mobile app support
- [ ] Plugin/extension system

---

## Deployment Architecture

### Development Environment
- Local development with H2 database
- Environment variables from .env file
- Hot reload enabled
- Debug logging

### Staging Environment
- PostgreSQL database
- Redis for caching
- HTTPS enforced
- Realistic test data
- Stripe test mode

### Production Environment
- Kubernetes cluster (future)
- PostgreSQL with replication
- Redis cluster
- CDN for static assets
- Load balancer
- Auto-scaling
- Monitoring (Prometheus/Grafana)
- Logging aggregation (ELK stack)
- Backup strategy (daily)
- Disaster recovery plan

---

## Performance Considerations

### Current Optimizations
- JPA lazy loading
- Database connection pooling
- Async audit logging
- Rate limiting
- Query indexing

### Future Optimizations
- Redis caching layer
- Database query optimization
- CDN for static content
- Response compression
- WebSocket for real-time features
- Database read replicas
- Elasticsearch for search

---

## Scalability Strategy

### Horizontal Scaling
- Stateless application design
- Session management via JWT
- Database connection pooling
- Load balancer ready

### Vertical Scaling
- JVM tuning options
- Memory optimization
- Thread pool configuration
- Connection pool sizing

---

## Monitoring & Observability

### Current
- SLF4J logging
- Logback configuration
- Audit trail system
- Fraud detection alerts

### Planned
- Application Performance Monitoring (APM)
- Distributed tracing
- Metrics collection (Prometheus)
- Dashboard (Grafana)
- Error tracking (Sentry)
- Uptime monitoring
- Performance profiling

---

## Compliance & Regulations

### Current Compliance
- PCI DSS Level 1 (via Stripe)
- OWASP Top 10 coverage
- Security best practices

### Future Compliance
- GDPR (EU users)
- CCPA (California users)
- SOC 2 Type II
- ISO 27001
- HIPAA (if healthcare data)

---

*This document is maintained as a living document and should be updated with each major architectural change.*
