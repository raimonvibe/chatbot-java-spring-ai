# Prayer-Chat System Overzicht

**Laatste Update:** 2025-12-19  
**Status:** Production-Ready met OAuth2-only authenticatie

---

## 📋 Inhoudsopgave

1. [Architectuur Overzicht](#architectuur-overzicht)
2. [Authenticatie & Security Flow](#authenticatie--security-flow)
3. [Componenten Structuur](#componenten-structuur)
4. [API Endpoints](#api-endpoints)
5. [Data Model](#data-model)
6. [External Integrations](#external-integrations)
7. [Test Infrastructuur](#test-infrastructuur)
8. [Security Features](#security-features)

---

## 🏗️ Architectuur Overzicht

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend (React)                         │
│              http://localhost:3000                          │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTPS/REST API
                       │ JWT Bearer Token
┌──────────────────────▼──────────────────────────────────────┐
│              Spring Boot Backend                            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Security Layer                           │  │
│  │  • JwtAuthenticationFilter                           │  │
│  │  • RateLimitingFilter                                │  │
│  │  • OAuth2AuthenticationSuccessHandler                │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Controller Layer                         │  │
│  │  • ChatbotController                                  │  │
│  │  • ChatController                                     │  │
│  │  • SubscriptionController                             │  │
│  │  • AuthController (alleen /me endpoint)              │  │
│  │  • StripeWebhookController                            │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Service Layer                             │  │
│  │  • AiChatbotService (Anthropic Claude)                │  │
│  │  • ChatbotService                                     │  │
│  │  • StripeService                                      │  │
│  │  • CustomOAuth2UserService                           │  │
│  │  • CustomUserDetailsService                           │  │
│  │  • AuditService                                       │  │
│  │  • FraudDetectionService                              │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Repository Layer (JPA)                    │  │
│  │  • UserRepository                                    │  │
│  │  • ChatbotRepository                                 │  │
│  │  • SubscriptionRepository                            │  │
│  │  • ConversationRepository                           │  │
│  │  • MessageRepository                                │  │
│  └──────────────────────────────────────────────────────┘  │
└──────────────────────┬──────────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
┌───────▼──────┐ ┌────▼─────┐ ┌─────▼────────┐
│  PostgreSQL  │ │ External │ │   Google     │
│  Database    │ │   APIs   │ │   OAuth2     │
│              │ │           │ │              │
│  • Users     │ │ • Stripe  │ │ • Login      │
│  • Chatbots  │ │ • Anthropic│ │ • User Info │
│  • Subs      │ │ • Cohere  │ │              │
│  • Messages  │ │           │ │              │
└──────────────┘ └───────────┘ └──────────────┘
```

---

## 🔐 Authenticatie & Security Flow

### OAuth2 Login Flow (Google)

```
1. User clicks "Login with Google"
   ↓
2. Frontend redirects to: /oauth2/authorization/google
   ↓
3. Spring Security redirects to Google OAuth
   ↓
4. User authenticates with Google
   ↓
5. Google redirects back: /login/oauth2/code/google?code=...
   ↓
6. CustomOAuth2UserService.loadUser()
   • Checks if user exists by Google ID
   • Creates new user if not exists
   • Links Google account to existing user if email matches
   ↓
7. OAuth2AuthenticationSuccessHandler.onAuthenticationSuccess()
   • Checks subscription status
   • Creates FREE subscription if none exists
   • Redirects to dashboard or pricing
   ↓
8. JWT Token generated (JwtTokenProvider)
   • Token contains: email, roles, expiration
   • Token sent to frontend via redirect
   ↓
9. Frontend stores JWT token
   ↓
10. Subsequent API calls include: Authorization: Bearer <token>
```

### JWT Authentication Flow

```
1. Request arrives with: Authorization: Bearer <token>
   ↓
2. JwtAuthenticationFilter.doFilterInternal()
   • Extracts token from header
   • Validates token (JwtTokenProvider.validateToken)
   • Extracts email from token
   ↓
3. CustomUserDetailsService.loadUserByUsername(email)
   • Searches by username OR email
   • Returns UserDetails
   ↓
4. Creates CustomOAuth2User from UserDetails
   • Sets as Authentication principal
   ↓
5. SecurityContext.setAuthentication()
   ↓
6. Controller receives @AuthenticationPrincipal CustomOAuth2User
```

### Subscription-Based Access Control

```
User Request → Controller
   ↓
Check Subscription (SubscriptionRepository.findByUserId)
   ↓
┌─────────────────┬─────────────────┐
│ Has Subscription│ No Subscription │
│                 │                 │
│ Check Status:   │ → 403 Forbidden │
│ • ACTIVE        │                 │
│ • TRIALING      │                 │
│ • FREE (ACTIVE) │                 │
│ → Allow         │                 │
│                 │                 │
│ • INACTIVE      │                 │
│ • CANCELED      │                 │
│ • PAST_DUE      │                 │
│ → 403 Forbidden │                 │
└─────────────────┴─────────────────┘
```

---

## 🧩 Componenten Structuur

### Controllers

| Controller | Endpoints | Authentication | Purpose |
|------------|-----------|----------------|---------|
| **AuthController** | `/api/auth/me` | Required | Get current user info |
| **ChatbotController** | `/api/chatbots/**` | Required (write), Public (read) | CRUD chatbots |
| **ChatController** | `/api/chat/{id}` | Public | Chat with chatbot |
| **SubscriptionController** | `/api/subscription/**` | Required | Manage subscriptions |
| **StripeWebhookController** | `/api/webhooks/stripe` | Public (signed) | Stripe events |
| **AuditLogController** | `/api/admin/audit-logs/**` | Admin only | Audit logs |

### Services

#### AI & Chat Services
- **AiChatbotService**: Main chatbot logic, integrates with Anthropic Claude
- **BibleVerseService**: Specialized content service
- **ChatbotService**: Business logic for chatbot management

#### Payment Services
- **StripeService**: Stripe API integration
- **SubscriptionService**: Subscription lifecycle management

#### Security Services
- **CustomOAuth2UserService**: Handles OAuth2 user creation/linking
- **CustomUserDetailsService**: User authentication (username/email)
- **AuditService**: Security audit logging
- **FraudDetectionService**: Fraud detection and prevention

#### Content Services
- **WebsiteAnalysisService**: Website crawling and analysis
- **ConversationExportService**: Export conversation data
- **WebhookService**: Webhook delivery with SSRF protection

#### Validation Services
- **UrlValidationService**: SSRF protection, URL validation

### Security Components

#### Filters
- **JwtAuthenticationFilter**: Validates JWT tokens, sets authentication
- **RateLimitingFilter**: Request throttling (Bucket4j)

#### Providers
- **JwtTokenProvider**: JWT generation and validation

#### Handlers
- **OAuth2AuthenticationSuccessHandler**: Post-OAuth2 login handling

#### Configuration
- **SecurityConfig**: Main security configuration (production)
- **TestSecurityConfig**: Test security configuration

---

## 🌐 API Endpoints

### Public Endpoints
```
GET  /api/health                    - Health check
GET  /api/chatbots/{id}             - Get chatbot (public read)
POST /api/chat/{chatbotId}          - Chat with chatbot
POST /api/webhooks/stripe           - Stripe webhook (signed)
GET  /oauth2/authorization/google   - OAuth2 login
GET  /login/oauth2/code/google      - OAuth2 callback
```

### Authenticated Endpoints
```
GET    /api/auth/me                 - Get current user
GET    /api/chatbots                - List user's chatbots
POST   /api/chatbots                - Create chatbot
PUT    /api/chatbots/{id}           - Update chatbot
DELETE /api/chatbots/{id}           - Delete chatbot
GET    /api/subscription/status     - Get subscription status
POST   /api/subscription/create-checkout-session - Create Stripe checkout
```

### Admin Endpoints
```
GET  /api/admin/audit-logs          - Get audit logs
GET  /api/admin/audit-logs/export  - Export audit logs
```

---

## 💾 Data Model

### Core Entities

#### User
```java
- id: Long
- email: String (unique)
- username: String (unique)
- googleId: String (unique, nullable)
- authProvider: AuthProvider (GOOGLE, LOCAL)
- roles: Set<String> (USER, ADMIN)
- enabled: Boolean
- accountNonLocked: Boolean
- lastLogin: LocalDateTime
```

#### Subscription
```java
- id: Long
- user: User (OneToOne)
- stripeCustomerId: String
- stripeSubscriptionId: String
- status: SubscriptionStatus (ACTIVE, INACTIVE, CANCELED, PAST_DUE, TRIALING)
- plan: SubscriptionPlan (FREE, BASIC, PRO, ENTERPRISE)
- currentPeriodStart: LocalDateTime
- currentPeriodEnd: LocalDateTime
- canceledAt: LocalDateTime
- paymentRetryCount: Integer
- gracePeriodEnd: LocalDateTime
```

#### Chatbot
```java
- id: Long
- name: String
- description: String
- websiteUrl: String
- owner: User
- primaryLanguage: String
- customPrompt: String
- embedCode: String (unique)
- active: Boolean
- createdAt: LocalDateTime
```

#### Conversation
```java
- id: Long
- chatbot: Chatbot
- sessionId: String
- language: String
- createdAt: LocalDateTime
```

#### Message
```java
- id: Long
- conversation: Conversation
- role: MessageRole (USER, ASSISTANT, SYSTEM)
- content: String
- timestamp: LocalDateTime
```

---

## 🔌 External Integrations

### Google OAuth 2.0
- **Purpose**: User authentication
- **Flow**: Authorization Code Flow
- **Scopes**: email, profile
- **Configuration**: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`

### Anthropic Claude (AI)
- **Purpose**: Chatbot AI responses
- **API Key**: `ANTHROPIC_API_KEY`
- **Model**: Claude 3 (via Spring AI)

### Cohere (Embeddings)
- **Purpose**: Text embeddings
- **API Key**: `COHERE_API_KEY`
- **Model**: Cohere Embed (via Spring AI)

### Stripe
- **Purpose**: Payment processing
- **Webhook**: `/api/webhooks/stripe`
- **Events**: `invoice.payment_succeeded`, `customer.subscription.updated`, etc.
- **Configuration**: `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`

---

## 🧪 Test Infrastructuur

### Test Types

#### Unit Tests
- **Location**: `backend/src/test/java/com/tjanabot/chatbot/unit/`
- **Framework**: JUnit 5, Mockito
- **Status**: ✅ 82 tests, 0 failures

#### Integration Tests
- **Location**: `backend/src/test/java/com/tjanabot/chatbot/integration/`
- **Framework**: `@SpringBootTest`, MockMvc
- **Status**: ✅ Working

#### E2E Tests
- **Location**: `backend/src/test/java/com/tjanabot/chatbot/e2e/`
- **Framework**: Testcontainers (PostgreSQL), WireMock, REST Assured
- **Status**: ⚠️ Some failures (REST Assured NullPointerException)

### Test Configuration

#### TestSecurityConfig
- **Purpose**: Simplified security for tests
- **Profile**: `test`
- **Features**: Disabled OAuth2, mock authentication

#### E2ETestBase
- **Purpose**: Base class for E2E tests
- **Features**:
  - PostgreSQL Testcontainers
  - WireMock server
  - ApiTestClient helper
  - OAuth2 user creation helpers

---

## 🔒 Security Features

### Authentication
- ✅ **OAuth2-only**: Email/password login removed
- ✅ **JWT Tokens**: Stateless authentication
- ✅ **Session Management**: IF_REQUIRED (for OAuth2 flow)

### Authorization
- ✅ **Role-Based Access Control**: USER, ADMIN roles
- ✅ **Subscription-Based Access**: Active subscription required
- ✅ **Resource Ownership**: Users can only access their own resources

### Input Validation
- ✅ **Bean Validation**: `@Valid`, `@NotBlank`, `@Size`, `@Pattern`
- ✅ **XSS Prevention**: `XssSanitizer`
- ✅ **SSRF Prevention**: `UrlValidationService`
- ✅ **SQL Injection**: JPA parameterized queries

### Security Headers
- ✅ **CORS**: Configured for allowed origins
- ✅ **CSRF**: Disabled for API endpoints
- ✅ **Content Security Policy**: Configured
- ✅ **HSTS**: Strict-Transport-Security header

### Rate Limiting
- ✅ **RateLimitingFilter**: Bucket4j-based throttling
- ✅ **Configurable**: Per endpoint limits

### Audit & Logging
- ✅ **AuditService**: Security event logging
- ✅ **LogSanitizer**: Sensitive data redaction
- ✅ **Fraud Detection**: Suspicious activity detection

---

## 📊 Current Status

### ✅ Working
- OAuth2 authentication (Google)
- JWT token generation and validation
- Subscription management
- Chatbot CRUD operations
- AI chat integration (Anthropic)
- Security tests (86 tests, 0 failures)
- Integration tests

### ⚠️ In Progress
- E2E test fixes (REST Assured NullPointerException)
- Some E2E tests failing due to connection/authentication issues

### 🔄 Recent Changes
- Removed email/password login (OAuth2-only)
- Updated all tests to use OAuth2
- Fixed security test suite
- Improved error handling in ApiTestClient

---

## 🚀 Deployment

### Environment Variables Required
```bash
# OAuth2
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...

# AI Services
ANTHROPIC_API_KEY=...
COHERE_API_KEY=...

# Stripe
STRIPE_SECRET_KEY=...
STRIPE_WEBHOOK_SECRET=...

# JWT
JWT_SECRET=...

# Database
DATABASE_URL=... (PostgreSQL)
```

### Profiles
- **default**: Production configuration
- **test**: Test configuration (TestSecurityConfig)

---

**Laatste Update:** 2025-12-19  
**Versie:** 1.0

