# 🚀 Implementation Plan: Google OAuth & Stripe Payment Integration

## 📊 Project Overview

**Objective**: Transform Prayer-Chat from a demo chatbot system into a SaaS chatbot generator platform with:
- Google OAuth authentication
- Stripe monthly subscription payments ($4.98/month per chatbot)
- Preview mode (5 free messages)
- Unlimited chatbot creation (each requires separate subscription)

**Business Model**:
- **Price**: $4.98/month per chatbot
- **Preview**: 5 messages free (with watermark)
- **Limit**: 500 messages/month per paid chatbot
- **Multi-bot**: Users can create unlimited chatbots (each needs separate subscription)

---

## 🎯 Key Decisions Confirmed

✅ **Pricing Model**: Monthly subscription ($4.98/month per chatbot)
✅ **Preview Limit**: 5 messages before payment required
✅ **Multi-Chatbot**: Unlimited chatbots per user, each requires payment
✅ **Message Limit**: 500 messages/month per paid chatbot
✅ **No Internal Chat**: Remove chat interface, keep only preview & embed widget

---

## 📐 Architecture Changes

### Current Architecture
```
User → Dashboard → Create Chatbot → Test Chat → Get Embed Code
                                    ↓
                              (No payment required)
```

### New Architecture
```
Google Login → Dashboard → Create Chatbot → Analyze Website
                              ↓
                        Preview (5 msgs max)
                              ↓
                   "Activate Chatbot" Button
                              ↓
                   Stripe Checkout ($4.98/mo)
                              ↓
                    Payment Success → Get Embed Code
                              ↓
                    Chatbot works on user's site (500 msgs/mo)
```

---

## 🗄️ Phase 1: Database Schema Changes

### 1.1 User Entity Updates

**Current `User.java`:**
```java
- username (String)
- email (String)
- password (String)
- roles (Set<String>)
- enabled (boolean)
```

**Add Fields:**
```java
@Column(unique = true)
private String googleId;  // Google OAuth user ID

@Enumerated(EnumType.STRING)
private AuthProvider authProvider = AuthProvider.LOCAL;  // LOCAL or GOOGLE

private String profilePictureUrl;  // From Google profile

@Column(nullable = true)  // Make nullable for OAuth users
private String password;
```

**New Enum: `AuthProvider.java`**
```java
public enum AuthProvider {
    LOCAL,
    GOOGLE
}
```

### 1.2 New Entity: Subscription

**Create `Subscription.java`:**
```java
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "chatbot_id")
    private Chatbot chatbot;

    @Column(unique = true)
    private String stripeCustomerId;

    @Column(unique = true)
    private String stripeSubscriptionId;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status = SubscriptionStatus.INACTIVE;

    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime canceledAt;
}
```

**New Enum: `SubscriptionStatus.java`**
```java
public enum SubscriptionStatus {
    ACTIVE,        // Paid and active
    INACTIVE,      // Not paid / preview mode
    PAST_DUE,      // Payment failed
    CANCELED,      // User canceled
    TRIALING       // Free trial (if implemented later)
}
```

### 1.3 Chatbot Entity Updates

**Add to `Chatbot.java`:**
```java
@OneToOne(mappedBy = "chatbot", cascade = CascadeType.ALL)
private Subscription subscription;

@Column(nullable = false)
private Integer messageCount = 0;  // Track usage

@Column(nullable = false)
private Integer messageLimit = 500;  // Monthly limit

@Column(nullable = false)
private Integer previewMessageCount = 0;  // Preview usage

@Column(nullable = false)
private Integer previewMessageLimit = 5;  // Preview limit

private LocalDateTime lastResetAt;  // For monthly reset
```

### 1.4 Migration SQL

**File: `V2__add_oauth_and_payments.sql`**
```sql
-- Update users table
ALTER TABLE users
    ADD COLUMN google_id VARCHAR(255) UNIQUE,
    ADD COLUMN auth_provider VARCHAR(20) DEFAULT 'LOCAL',
    ADD COLUMN profile_picture_url VARCHAR(500),
    MODIFY COLUMN password VARCHAR(255) NULL;

CREATE INDEX idx_users_google_id ON users(google_id);

-- Create subscriptions table
CREATE TABLE subscriptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    chatbot_id BIGINT NOT NULL UNIQUE,
    stripe_customer_id VARCHAR(255) UNIQUE,
    stripe_subscription_id VARCHAR(255) UNIQUE,
    status VARCHAR(20) DEFAULT 'INACTIVE',
    current_period_start TIMESTAMP,
    current_period_end TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    canceled_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (chatbot_id) REFERENCES chatbots(id) ON DELETE CASCADE
);

CREATE INDEX idx_subscriptions_stripe_customer ON subscriptions(stripe_customer_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);

-- Update chatbots table
ALTER TABLE chatbots
    ADD COLUMN message_count INT DEFAULT 0,
    ADD COLUMN message_limit INT DEFAULT 500,
    ADD COLUMN preview_message_count INT DEFAULT 0,
    ADD COLUMN preview_message_limit INT DEFAULT 5,
    ADD COLUMN last_reset_at TIMESTAMP NULL;
```

---

## 🔐 Phase 2: Google OAuth Implementation

### 2.1 Dependencies

**Add to `backend/pom.xml`:**
```xml
<!-- OAuth2 Client -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

### 2.2 Configuration

**Update `application.yml`:**
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope:
              - profile
              - email
            redirect-uri: "{baseUrl}/login/oauth2/code/google"
        provider:
          google:
            authorization-uri: https://accounts.google.com/o/oauth2/v2/auth
            token-uri: https://oauth2.googleapis.com/token
            user-info-uri: https://www.googleapis.com/oauth2/v3/userinfo
            user-name-attribute: sub
```

**Environment Variables:**
```bash
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-google-client-secret
```

### 2.3 New Components

**File: `config/OAuth2LoginConfig.java`**
- Configure OAuth2 login
- Custom success handler
- Custom failure handler

**File: `security/CustomOAuth2UserService.java`**
- Extends `DefaultOAuth2UserService`
- Processes Google user info
- Creates/updates User entity
- Links Google ID to user

**File: `security/OAuth2LoginSuccessHandler.java`**
- Handles post-login actions
- Generates JWT token
- Redirects to dashboard

**File: `dto/OAuth2UserInfo.java`**
```java
public class OAuth2UserInfo {
    private String id;
    private String name;
    private String email;
    private String picture;

    // Extract from OAuth2User attributes
    public static OAuth2UserInfo fromGoogleUser(OAuth2User oAuth2User) {
        // Parse Google user attributes
    }
}
```

### 2.4 Security Configuration Updates

**Update `SecurityConfig.java`:**
```java
@Override
protected void configure(HttpSecurity http) throws Exception {
    http
        // ... existing config
        .oauth2Login()
            .loginPage("/login")
            .userInfoEndpoint()
                .userService(customOAuth2UserService)
                .and()
            .successHandler(oAuth2LoginSuccessHandler)
            .failureHandler(oAuth2LoginFailureHandler);
}
```

### 2.5 User Flow

1. User visits landing page → Clicks "Sign in with Google"
2. Redirects to Google OAuth consent screen
3. User authorizes app
4. Google redirects back with authorization code
5. `CustomOAuth2UserService` processes user info:
   - Check if user exists by `googleId`
   - If exists: update user info
   - If not: create new user with `authProvider = GOOGLE`
6. `OAuth2LoginSuccessHandler` generates JWT token
7. Redirect to dashboard with JWT token

---

## 💳 Phase 3: Stripe Integration

### 3.1 Dependencies

**Add to `backend/pom.xml`:**
```xml
<dependency>
    <groupId>com.stripe</groupId>
    <artifactId>stripe-java</artifactId>
    <version>24.3.0</version>
</dependency>
```

### 3.2 Stripe Setup (Dashboard)

**Products & Prices:**
1. Create Product: "Prayer-Chat Chatbot Subscription"
2. Create Price: $4.98/month recurring
3. Copy Price ID: `price_xxxxxxxxxxxxx`

**Webhooks:**
1. Create webhook endpoint: `https://yourdomain.com/api/webhooks/stripe`
2. Subscribe to events:
   - `checkout.session.completed`
   - `customer.subscription.created`
   - `customer.subscription.updated`
   - `customer.subscription.deleted`
   - `invoice.payment_succeeded`
   - `invoice.payment_failed`

### 3.3 Configuration

**Update `application.yml`:**
```yaml
stripe:
  api-key: ${STRIPE_SECRET_KEY}
  publishable-key: ${STRIPE_PUBLISHABLE_KEY}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET}
  price-id: ${STRIPE_PRICE_ID}
  success-url: ${APP_URL}/payment/success?session_id={CHECKOUT_SESSION_ID}
  cancel-url: ${APP_URL}/payment/cancel

payment:
  monthly-price: 4.98
  currency: usd
  preview-message-limit: 5
  paid-message-limit: 500
```

**Environment Variables:**
```bash
STRIPE_SECRET_KEY=sk_test_xxxxxxxxxxxxx
STRIPE_PUBLISHABLE_KEY=pk_test_xxxxxxxxxxxxx
STRIPE_WEBHOOK_SECRET=whsec_xxxxxxxxxxxxx
STRIPE_PRICE_ID=price_xxxxxxxxxxxxx
APP_URL=https://yourdomain.com
```

### 3.4 New Components

**File: `service/StripeService.java`**
```java
@Service
public class StripeService {

    // Initialize Stripe with API key

    // createCheckoutSession(chatbotId, userId)
    // createCustomer(user)
    // createSubscription(customerId, priceId)
    // cancelSubscription(subscriptionId)
    // getSubscription(subscriptionId)
    // updateSubscription(subscriptionId, params)
}
```

**File: `service/SubscriptionService.java`**
```java
@Service
public class SubscriptionService {

    // createSubscription(chatbot, user)
    // activateSubscription(chatbotId, stripeData)
    // cancelSubscription(chatbotId)
    // checkSubscriptionStatus(chatbotId)
    // resetMonthlyUsage() // Cron job
}
```

**File: `controller/PaymentController.java`**
```java
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    // POST /create-checkout-session
    // - Creates Stripe Checkout session for chatbot
    // - Returns checkout URL

    // GET /success
    // - Handles successful payment redirect
    // - Shows embed code

    // GET /cancel
    // - Handles canceled payment
    // - Redirect to dashboard
}
```

**File: `controller/StripeWebhookController.java`**
```java
@RestController
@RequestMapping("/api/webhooks/stripe")
public class StripeWebhookController {

    // POST /
    // - Verify webhook signature
    // - Handle different event types:
    //   - checkout.session.completed → Activate subscription
    //   - customer.subscription.updated → Update status
    //   - customer.subscription.deleted → Deactivate
    //   - invoice.payment_failed → Mark past_due
}
```

### 3.5 Payment Flow Implementation

**Step 1: User Creates Chatbot**
```java
// ChatbotController.java
@PostMapping("/chatbots")
public ResponseEntity<Chatbot> createChatbot(@RequestBody ChatbotRequest request) {
    // Create chatbot
    Chatbot chatbot = new Chatbot(request.getName(), request.getWebsiteUrl());
    chatbot.setOwner(currentUser);
    chatbotRepository.save(chatbot);

    // Create inactive subscription
    Subscription subscription = new Subscription();
    subscription.setUser(currentUser);
    subscription.setChatbot(chatbot);
    subscription.setStatus(SubscriptionStatus.INACTIVE);
    subscriptionRepository.save(subscription);

    // Analyze website in background
    websiteAnalysisService.analyzeWebsite(chatbot);

    return ResponseEntity.ok(chatbot);
}
```

**Step 2: User Clicks "Activate Chatbot"**
```java
// PaymentController.java
@PostMapping("/create-checkout-session")
public ResponseEntity<Map<String, String>> createCheckoutSession(@RequestBody Map<String, Long> request) {
    Long chatbotId = request.get("chatbotId");
    Chatbot chatbot = chatbotRepository.findById(chatbotId).orElseThrow();

    // Create or get Stripe customer
    String customerId = stripeService.getOrCreateCustomer(currentUser);

    // Create checkout session
    String sessionUrl = stripeService.createCheckoutSession(
        customerId,
        chatbotId,
        env.getProperty("stripe.price-id")
    );

    return ResponseEntity.ok(Map.of("url", sessionUrl));
}
```

**Step 3: Stripe Webhook Activates Subscription**
```java
// StripeWebhookController.java
@PostMapping
public ResponseEntity<String> handleWebhook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String signature) {

    Event event = stripeService.verifyWebhook(payload, signature);

    switch (event.getType()) {
        case "checkout.session.completed":
            Session session = (Session) event.getDataObjectDeserializer().getObject().get();
            Long chatbotId = Long.parseLong(session.getMetadata().get("chatbot_id"));

            // Activate subscription
            subscriptionService.activateSubscription(
                chatbotId,
                session.getCustomer(),
                session.getSubscription()
            );
            break;

        case "customer.subscription.deleted":
            String subscriptionId = event.getData().getObject().get("id").toString();
            subscriptionService.deactivateByStripeId(subscriptionId);
            break;
    }

    return ResponseEntity.ok("Success");
}
```

---

## 🔒 Phase 4: Access Control & Feature Gating

### 4.1 Message Limit Enforcement

**Update `ChatController.java`:**
```java
@PostMapping("/{chatbotId}")
public ResponseEntity<Map<String, Object>> sendMessage(@PathVariable Long chatbotId, @RequestBody ChatRequest request) {

    Chatbot chatbot = chatbotRepository.findById(chatbotId).orElseThrow();
    Subscription subscription = chatbot.getSubscription();

    // Check subscription status
    if (subscription.getStatus() == SubscriptionStatus.INACTIVE) {
        // Preview mode
        if (chatbot.getPreviewMessageCount() >= chatbot.getPreviewMessageLimit()) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "Preview limit reached. Please activate this chatbot to continue.",
                "limitReached", true,
                "isPreview", true
            ));
        }

        // Increment preview counter
        chatbot.setPreviewMessageCount(chatbot.getPreviewMessageCount() + 1);
        chatbotRepository.save(chatbot);

    } else if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
        // Paid mode
        if (chatbot.getMessageCount() >= chatbot.getMessageLimit()) {
            return ResponseEntity.status(429).body(Map.of(
                "error", "Monthly message limit reached. Resets on: " + chatbot.getLastResetAt().plusMonths(1),
                "limitReached", true
            ));
        }

        // Increment message counter
        chatbot.setMessageCount(chatbot.getMessageCount() + 1);
        chatbotRepository.save(chatbot);

    } else {
        // Subscription expired/canceled
        return ResponseEntity.status(403).body(Map.of(
            "error", "Subscription is not active. Please renew.",
            "subscriptionInactive", true
        ));
    }

    // Process message normally
    ChatResponse response = aiChatbotService.processMessage(...);

    return ResponseEntity.ok(Map.of(
        "message", response.getContent(),
        "sessionId", sessionId,
        "messagesRemaining", chatbot.getMessageLimit() - chatbot.getMessageCount(),
        "isPreview", subscription.getStatus() == SubscriptionStatus.INACTIVE
    ));
}
```

### 4.2 Monthly Usage Reset

**File: `service/UsageResetService.java`**
```java
@Service
public class UsageResetService {

    // Run daily at midnight
    @Scheduled(cron = "0 0 0 * * ?")
    public void resetMonthlyUsage() {
        LocalDateTime now = LocalDateTime.now();

        // Find chatbots that need reset (last reset > 30 days ago)
        List<Chatbot> chatbotsToReset = chatbotRepository
            .findByLastResetAtBefore(now.minusDays(30));

        for (Chatbot chatbot : chatbotsToReset) {
            if (chatbot.getSubscription().getStatus() == SubscriptionStatus.ACTIVE) {
                chatbot.setMessageCount(0);
                chatbot.setLastResetAt(now);
                chatbotRepository.save(chatbot);
            }
        }

        logger.info("Reset usage for {} chatbots", chatbotsToReset.size());
    }
}
```

### 4.3 Embed Code Protection

**Update `ChatbotController.java`:**
```java
@GetMapping("/{id}/embed-code")
public ResponseEntity<Map<String, String>> getEmbedCode(@PathVariable Long id) {
    Chatbot chatbot = chatbotRepository.findById(id).orElseThrow();

    // Check ownership
    if (!chatbot.getOwner().getId().equals(currentUser.getId())) {
        return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
    }

    // Check payment status
    if (chatbot.getSubscription().getStatus() != SubscriptionStatus.ACTIVE) {
        return ResponseEntity.status(403).body(Map.of(
            "error", "Please activate your subscription to get the embed code",
            "requiresPayment", "true"
        ));
    }

    // Return embed code
    String embedCode = generateEmbedCode(chatbot);
    return ResponseEntity.ok(Map.of("embedCode", embedCode));
}
```

---

## 🎨 Phase 5: Frontend Changes

### 5.1 Remove Internal Chat Interface

**Files to Remove/Modify:**
- ❌ `/templates/chatbot-test.html` → Delete or restrict to preview only
- ❌ Internal chat components → Remove
- ✅ Keep: Chatbot creation, website analysis, dashboard

### 5.2 New UI Components

**Landing Page (`/templates/index.html`):**
```html
<!-- Hero Section -->
<h1>Create Your AI Chatbot in Minutes</h1>
<p>$4.98/month per chatbot • 500 messages/month</p>
<button onclick="loginWithGoogle()">Sign in with Google</button>

<!-- Features -->
- Analyze any website automatically
- 100+ languages supported
- 5 free preview messages
- Easy embed code
```

**Dashboard (`/templates/dashboard.html`):**
```html
<!-- Chatbot List -->
<div class="chatbot-card">
  <h3>{{ chatbot.name }}</h3>
  <span class="badge">
    {{ chatbot.subscription.status == 'ACTIVE' ? 'Active' : 'Preview Mode' }}
  </span>

  <div class="actions">
    <button>Edit</button>
    <button onclick="previewChatbot()">Preview</button>

    <!-- Show activate button if not paid -->
    <button v-if="!isPaid" onclick="activateChatbot()">
      Activate ($4.98/mo)
    </button>

    <!-- Show embed code button if paid -->
    <button v-if="isPaid" onclick="getEmbedCode()">
      Get Embed Code
    </button>
  </div>

  <div class="usage">
    Messages: {{ messageCount }} / {{ messageLimit }}
  </div>
</div>

<button onclick="createNewChatbot()">+ Create New Chatbot</button>
```

**Preview Modal (`/templates/components/preview-modal.html`):**
```html
<div class="preview-modal">
  <div class="preview-header">
    <span class="preview-badge">PREVIEW MODE</span>
    <span>{{ previewMessagesRemaining }} / 5 messages remaining</span>
  </div>

  <div class="chat-container">
    <!-- Chat interface here -->
  </div>

  <div v-if="limitReached" class="upgrade-prompt">
    <p>Preview limit reached!</p>
    <button onclick="activateChatbot()">Activate for $4.98/month</button>
  </div>
</div>
```

**Payment Success Page (`/templates/payment-success.html`):**
```html
<div class="success-page">
  <h1>🎉 Chatbot Activated!</h1>
  <p>Your chatbot is now live with 500 messages/month.</p>

  <div class="embed-code-section">
    <h2>Embed Code</h2>
    <p>Copy this code and paste it before the closing &lt;/body&gt; tag on your website:</p>
    <pre><code>{{ embedCode }}</code></pre>
    <button onclick="copyCode()">Copy Code</button>
  </div>

  <a href="/dashboard">Go to Dashboard</a>
</div>
```

### 5.3 Embed Widget Updates

**File: `/static/js/chatbot-widget.js`**

Add preview mode indicators:
```javascript
// Check if chatbot is in preview mode
if (response.isPreview) {
    showPreviewBanner();
    updateRemainingMessages(response.messagesRemaining);
}

// Handle limit reached
if (response.limitReached) {
    if (response.isPreview) {
        showUpgradePrompt("Preview limit reached. Ask the site owner to activate this chatbot.");
    } else {
        disableInput("Monthly limit reached. Resets soon!");
    }
}
```

---

## 🧪 Phase 6: Testing Strategy

### 6.1 Unit Tests

**Test Files to Create:**
- `StripeServiceTest.java` - Mock Stripe API calls
- `SubscriptionServiceTest.java` - Test subscription logic
- `OAuth2UserServiceTest.java` - Test Google OAuth flow
- `ChatControllerTest.java` - Test message limits

### 6.2 Integration Tests

**Test Scenarios:**
1. Google OAuth login flow
2. Chatbot creation → Preview → Payment → Activation
3. Message limit enforcement (preview & paid)
4. Stripe webhook handling
5. Monthly usage reset
6. Subscription cancellation

### 6.3 Stripe Testing

**Use Stripe Test Mode:**
- Test card: `4242 4242 4242 4242`
- Expiry: Any future date
- CVC: Any 3 digits

**Test webhook events locally:**
```bash
stripe listen --forward-to localhost:8081/api/webhooks/stripe
```

---

## 📦 Deployment Checklist

### 6.1 Google Cloud Console Setup

1. Create OAuth 2.0 Client ID
2. Add authorized redirect URIs:
   - `https://yourdomain.com/login/oauth2/code/google`
3. Copy Client ID and Secret

### 6.2 Stripe Setup

1. Create product and price
2. Set up webhook endpoint
3. Copy API keys (Secret, Publishable, Webhook Secret)

### 6.3 Environment Variables

**Production `.env`:**
```bash
# Database
DATABASE_URL=postgresql://...
DB_USERNAME=...
DB_PASSWORD=...

# AI APIs
ANTHROPIC_API_KEY=sk-ant-...
COHERE_API_KEY=...

# OAuth
GOOGLE_CLIENT_ID=...apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=...

# Stripe
STRIPE_SECRET_KEY=sk_live_...
STRIPE_PUBLISHABLE_KEY=pk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_PRICE_ID=price_...

# App
APP_URL=https://yourdomain.com
JWT_SECRET=...
```

### 6.4 Database Migration

```bash
# Run migrations
./mvnw flyway:migrate

# Or manually execute V2__add_oauth_and_payments.sql
```

---

## 📈 Monitoring & Analytics

### Track These Metrics:

**User Metrics:**
- Total signups (Google OAuth)
- Active users (last 30 days)
- Chatbots created per user

**Revenue Metrics:**
- Total active subscriptions
- Monthly recurring revenue (MRR)
- Churn rate
- Average revenue per user (ARPU)

**Conversion Metrics:**
- Signup → Chatbot creation rate
- Chatbot creation → Preview usage rate
- Preview → Paid conversion rate
- Time to first payment

**Usage Metrics:**
- Average messages per chatbot
- % chatbots hitting message limits
- Preview vs paid message volume

### Tools:

1. **Stripe Dashboard**: Revenue, subscriptions, failed payments
2. **Google Analytics**: User journeys, conversion funnels
3. **Custom Dashboard**: Create admin panel showing:
   - Total revenue
   - Active subscriptions by status
   - Top users by chatbots created
   - Message volume trends

---

## ⚠️ Important Considerations

### Security

1. **NEVER** expose Stripe Secret Key in frontend
2. Always verify webhook signatures
3. Validate JWT tokens on all protected endpoints
4. Sanitize user inputs (website URLs, chatbot names)
5. Use HTTPS everywhere in production

### Edge Cases to Handle

1. **User cancels during Stripe checkout**: Subscription stays INACTIVE
2. **Payment fails after activation**: Webhook updates to PAST_DUE
3. **User creates chatbot, never pays**: Preview mode forever (OK)
4. **User deletes chatbot mid-subscription**: Cancel Stripe subscription
5. **Multiple tabs using same chatbot**: Message count race conditions (use transactions)

### Cost Management

**Monthly Costs per Chatbot:**
- Anthropic: ~$2-5 for 500 conversations
- Cohere: ~$0.10 (one-time indexing)
- Stripe fee: $0.15 + 2.9% = $0.29

**Break-even:** $2.39 profit per chatbot/month

**If user exceeds 500 messages:** Consider soft cap (slower responses) vs hard cap (stop working)

---

## 🚀 Launch Strategy

### MVP Launch (Week 1-4)

**Features:**
- ✅ Google OAuth
- ✅ Basic chatbot creation
- ✅ Preview mode (5 messages)
- ✅ Stripe checkout
- ✅ Embed code generation
- ✅ Simple dashboard

**Nice-to-Have (Later):**
- ⏳ Advanced analytics
- ⏳ Team collaboration
- ⏳ Custom domains
- ⏳ White-label option
- ⏳ API access

### Beta Testing Phase

1. Launch to 10-20 beta users
2. Monitor conversion rates
3. Gather feedback on:
   - Pricing ($4.98 too high/low?)
   - Preview limit (5 messages enough?)
   - Message limit (500 sufficient?)
4. Adjust before public launch

### Marketing Hooks

**Value Propositions:**
- "Add an AI chatbot to your website in 5 minutes"
- "No coding required"
- "$4.98/month - cheaper than hiring support"
- "Try free with 5 preview messages"
- "500 conversations/month included"

---

## 📞 Support & Troubleshooting

### Common Issues

**"Google login not working"**
- Check redirect URI matches exactly
- Verify Google Client ID/Secret
- Ensure OAuth consent screen approved

**"Payment not activating chatbot"**
- Check webhook endpoint is public
- Verify webhook secret
- Check webhook event delivery in Stripe dashboard

**"Message limit not resetting"**
- Check cron job is running
- Verify `lastResetAt` field is set
- Check subscription is ACTIVE

**"Embed code not working on user's site"**
- CORS issues - check allowed origins
- Script blocked by CSP - advise user to whitelist
- Check chatbot is ACTIVE

---

## 🎯 Success Criteria

**Launch Goals (Month 1):**
- 50 signups via Google OAuth
- 20 chatbots created
- 10 paid activations (20% conversion)
- $50 MRR

**Growth Goals (Month 3):**
- 500 signups
- 200 chatbots created
- 100 paid subscriptions (50% conversion)
- $500 MRR

**Profitability:**
- Break-even: ~50 paid subscriptions
- Target: 200+ paid subscriptions for sustainable business

---

## 📚 Next Steps

1. **Review this plan** - Confirm all decisions
2. **Set up external accounts:**
   - Google Cloud Console (OAuth)
   - Stripe account
3. **Start Phase 1**: Database schema changes
4. **Implement sequentially**: OAuth → Stripe → Access Control → Frontend
5. **Test thoroughly** before production
6. **Beta launch** with monitoring
7. **Iterate** based on data

---

**Document Version:** 1.0
**Last Updated:** 2025-01-23
**Status:** Ready for Implementation

---

Questions? Need clarification on any phase? Let's discuss!
