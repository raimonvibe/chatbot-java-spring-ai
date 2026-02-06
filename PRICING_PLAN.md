# 💰 Subscription Pricing Plan

## Usage-based plans (backend implementation)

Plans are enforced by **usage** so that high-cost actions (website scans = embeddings) stay within sustainable limits.

| Plan       | Use case              | Scans/month | Max pages/scan | Cost cap/month | Chatbots | Messages/day |
|-----------|------------------------|-------------|----------------|----------------|----------|--------------|
| **FREE**  | Try before buy         | 1           | 50             | $5             | 1        | 10           |
| **BASIC** | Small sites, light use | 5           | 500            | $15            | 3        | 100          |
| **PRO**   | Medium sites, agencies | 20          | 2,000          | $50            | 10       | 500          |
| **ENTERPRISE** | Large sites, high volume | 100    | 10,000         | $200           | 50       | 2,000        |

- **Scans/month**: Number of website analyses per calendar month.
- **Max pages/scan**: Prevents scanning very large sites (e.g. AWS-sized) on lower tiers; embedding cost scales with pages/tokens.
- **Cost cap**: Hard limit on tracked usage (embedding + scan cost) per month; all plans are capped.
- Limits are defined in `backend/.../config/PlanLimits.java` and used by `RateLimitingService`, `CostTrackingService`, `AccessControlService`, and `ChatbotController`.

---

## 📊 Cost Basis Analysis

### Cost Per Chatbot (Monthly)

**Cost Components:**
1. **Website Scanning (One-Time):**
   - Small site (50 pages): ~$0.015
   - Medium site (500 pages): ~$0.15
   - Large site (5,000 pages): ~$1.50
   - AWS (15,000 pages): ~$4.50

2. **Chat Operations (Monthly - Main Cost Driver):**
   - Per question: ~$0.0037 (input + output tokens)
   - 1,000 questions/month: ~$3.70
   - 10,000 questions/month: ~$37.00
   - 45,000 questions/month: ~$166.64 (worst case)

---

## 💳 Recommended Pricing Tiers

### Tier 1: Preview Mode (No Subscription - Free to Try)

**Price:** $0/month

**Included:**
- ✅ Create 3 chatbots (temporary for testing, will be 1 in production)
- ✅ Website size limit: 50 pages max (pre-scan check)
- ✅ Rate-limited preview: 10 chat messages/day
- ✅ Theme customization
- ✅ View visitor chats (read-only)
- ❌ NO integration script
- ❌ NO deployment

**Cost to Platform:**
- Small website scan: ~$0.015 (one-time)
- Chat messages: 10/day × 30 days = 300/month × $0.0037 = ~$1.11/month
- **Total cost: ~$1.13/month per preview user**

**Purpose:** Let users try the service with minimal cost exposure

---

### Tier 2: Starter Plan

**Price:** $29/month

**Included:**
- ✅ All preview features
- ✅ Website size: Up to 500 pages
- ✅ Chat messages: Up to 8,000/month (~266/day)
- ✅ Integration script access
- ✅ Full deployment
- ✅ Analytics and reporting

**Cost Coverage:**
- Website scan (500 pages): ~$0.15 (one-time)
- Chat operations: 8,000 × $0.0037 = ~$29.60/month
- **Total cost: ~$29.75/month**
- **Margin: ~$0.25/month (break-even)**

**Target:** Small businesses, personal websites

---

### Tier 3: Professional Plan

**Price:** $49/month

**Included:**
- ✅ All Starter features
- ✅ Website size: Up to 2,000 pages
- ✅ Chat messages: Up to 13,000/month (~433/day)
- ✅ Priority support
- ✅ Advanced customization

**Cost Coverage:**
- Website scan (2,000 pages): ~$0.60 (one-time)
- Chat operations: 13,000 × $0.0037 = ~$48.10/month
- **Total cost: ~$48.70/month**
- **Margin: ~$0.30/month (break-even)**

**Target:** Medium businesses, growing companies

---

### Tier 4: Enterprise Plan

**Price:** $99/month

**Included:**
- ✅ All Professional features
- ✅ Website size: Unlimited
- ✅ Chat messages: Up to 25,000/month (~833/day)
- ✅ Dedicated support
- ✅ Custom integrations
- ✅ SLA guarantee

**Cost Coverage:**
- Website scan (unlimited): Up to ~$5.00 (one-time, for very large sites)
- Chat operations: 25,000 × $0.0037 = ~$92.50/month
- **Total cost: ~$97.50/month**
- **Margin: ~$1.50/month (small margin)**

**Target:** Large businesses, high-traffic websites

---

### Tier 5: Enterprise Plus (Custom)

**Price:** $199/month or Custom

**Included:**
- ✅ All Enterprise features
- ✅ Unlimited everything
- ✅ White-label options
- ✅ Custom AI model configuration
- ✅ Dedicated account manager

**Cost Coverage:**
- Handles worst-case scenarios (45,000+ questions/month)
- **Cost: Up to ~$170/month**
- **Margin: ~$29+/month**

**Target:** Enterprise customers with high usage

---

## 📈 Pricing Comparison Table

| Tier | Price/Month | Website Size | Chat/Month | Cost/Month | Margin |
|------|-------------|--------------|------------|------------|--------|
| **Preview** | $0 | 50 pages | 300 | ~$1.13 | -$1.13 (loss leader) |
| **Starter** | $29 | 500 pages | 8,000 | ~$29.75 | ~$0 (break-even) |
| **Professional** | $49 | 2,000 pages | 13,000 | ~$48.70 | ~$0.30 |
| **Enterprise** | $99 | Unlimited | 25,000 | ~$97.50 | ~$1.50 |
| **Enterprise Plus** | $199 | Unlimited | Unlimited | ~$170 | ~$29+ |

---

## 💡 Pricing Strategy Rationale

### 1. Preview Mode (Free)
- **Purpose:** User acquisition, low barrier to entry
- **Cost:** Acceptable loss (~$1/user/month)
- **Conversion Goal:** Get users to try, then upgrade

### 2. Starter ($29)
- **Positioning:** Entry-level paid plan
- **Margin:** Break-even (covers costs)
- **Goal:** Cover operational costs, build user base

### 3. Professional ($49)
- **Positioning:** Most popular plan
- **Margin:** Small positive margin
- **Goal:** Main revenue driver

### 4. Enterprise ($99)
- **Positioning:** High-usage customers
- **Margin:** Small but positive
- **Goal:** Handle worst-case scenarios profitably

### 5. Enterprise Plus ($199)
- **Positioning:** Premium tier
- **Margin:** Healthy margin
- **Goal:** Premium revenue, custom needs

---

## 🎯 Cost Protection Measures

### For Preview Users:
1. **Pre-scan size check** - Blocks large websites before costs
2. **50 page limit** - Prevents expensive scans
3. **10 messages/day** - Limits chat costs
4. **Total cost cap:** ~$1.13/month per user

### For Paid Users:
1. **Usage-based limits** - Each tier has clear limits
2. **Cost tracking** - Monitor actual costs vs. pricing
3. **Overage protection** - Can add usage alerts
4. **Auto-scaling** - Suggest upgrade if limits approached

---

## 📊 Revenue Projections (Example)

### Scenario: 1,000 Users

**Distribution (estimated):**
- Preview: 700 users (70%) - Cost: $791/month
- Starter: 200 users (20%) - Revenue: $5,800/month
- Professional: 80 users (8%) - Revenue: $3,920/month
- Enterprise: 18 users (1.8%) - Revenue: $1,782/month
- Enterprise Plus: 2 users (0.2%) - Revenue: $398/month

**Totals:**
- **Revenue:** $11,900/month
- **Costs:** ~$7,500/month (preview costs + paid tier costs)
- **Profit:** ~$4,400/month (37% margin)

---

## 🔄 Pricing Adjustments

### If Costs Increase:
- Monitor AI API pricing changes
- Adjust limits or pricing accordingly
- Consider usage-based pricing for high-volume users

### If Competition Changes:
- Monitor competitor pricing
- Adjust positioning if needed
- Focus on value differentiation

### If Conversion Rates Low:
- Consider lowering Starter tier to $24/month
- Add more value to Professional tier
- Improve preview → paid conversion

---

## ✅ Final Recommended Pricing

| Tier | Monthly Price | Key Features |
|------|---------------|--------------|
| **Preview** | **$0** | 50 pages, 10 msgs/day, no script |
| **Starter** | **$29** | 500 pages, 8K msgs/month, script access |
| **Professional** | **$49** | 2K pages, 13K msgs/month, priority support |
| **Enterprise** | **$99** | Unlimited, 25K msgs/month, SLA |
| **Enterprise Plus** | **$199** | Unlimited everything, white-label |

---

## 💬 User Experience & Christian Messaging

### Beautiful, Compassionate Feedback

**Philosophy:** We provide a warm, Christian-centered user experience with clear, compassionate communication about website findings and limits.

### Website Analysis Feedback

**When Website is Analyzed:**
```json
{
  "status": "analyzing",
  "message": "We're carefully reviewing your website to understand its content and purpose. This may take a few moments.",
  "progress": 45,
  "findings": {
    "pagesFound": 127,
    "estimatedTime": "2 minutes",
    "christianContent": {
      "similarityScore": 78,
      "themes": ["service", "community", "faith"],
      "relevantVerses": [...]
    }
  }
}
```

**When Limits Are Reached:**
```json
{
  "status": "limit_reached",
  "message": "We've discovered that your website contains {pages} pages, which exceeds our preview limit of 50 pages. We'd love to help you share your message with the world!",
  "upgradeMessage": "Consider upgrading to our {tier} plan to unlock the full potential of your website chatbot.",
  "bibleVerse": "Matthew 5:16 - 'Let your light shine before others, that they may see your good deeds and glorify your Father in heaven.'",
  "action": "upgrade"
}
```

### Christian-Style Limit Messages

**Website Size Limit Reached:**
```
"Your website is a beautiful testament to your mission! We've found {pages} pages of content, 
which exceeds our preview limit. We'd be honored to help you share your message more widely 
through our paid plans.

'For I know the plans I have for you,' declares the Lord, 'plans to prosper you and not to harm you, 
plans to give you hope and a future.' - Jeremiah 29:11

Upgrade now to unlock unlimited website scanning and share your message with the world."
```

**Chat Message Limit Reached:**
```
"You've reached your daily preview limit of 10 messages. We're grateful you're exploring how 
Prayer-Chat can serve your community!

'Give, and it will be given to you. A good measure, pressed down, shaken together and running over, 
will be poured into your lap.' - Luke 6:38

Upgrade to continue your conversation and serve your visitors without limits."
```

**Cost Limit Reached:**
```
"We've reached the monthly cost limit for your preview account. Your dedication to serving others 
through your website is inspiring!

'Each of you should use whatever gift you have received to serve others, as faithful stewards 
of God's grace in its various forms.' - 1 Peter 4:10

Upgrade to continue growing your ministry and reaching more people."
```

### Website Analysis Findings Presentation

**Beautiful Dashboard Widget:**
```jsx
<ChristianContentAnalysis>
  <h3>Your Website's Christian Content Analysis</h3>
  <SimilarityScore score={78} />
  <Message>
    "We've analyzed your website and found strong themes of {themes}. 
    Your content beautifully reflects Christian values of {values}."
  </Message>
  <RelevantVerses verses={topVerses} />
  <Themes themes={extractedThemes} />
  <UpgradePrompt>
    "Discover more insights about your website's Christian content with our paid plans."
  </UpgradePrompt>
</ChristianContentAnalysis>
```

### Error Messages with Grace

**Instead of:**
- ❌ "Error: Limit exceeded"
- ❌ "Payment required"
- ❌ "Access denied"

**We Show:**
- ✅ "We'd love to help you share your message more widely!"
- ✅ "Your website is a beautiful testament to your mission"
- ✅ "We're grateful you're exploring Prayer-Chat"
- ✅ "Your dedication to serving others is inspiring"

### Implementation Guidelines

**1. Always Include:**
- Warm, welcoming tone
- Relevant Bible verse (when appropriate)
- Clear explanation of what happened
- Helpful next steps
- Upgrade option (when applicable)

**2. Visual Design:**
- Beautiful, modern UI
- Christian-themed colors (warm browns, golds)
- Gentle animations
- Clear call-to-action buttons

**3. Message Examples:**

**Website Too Large:**
```
"Your website is a beautiful testament to your mission! We've found {pages} pages of content, 
which exceeds our preview limit of 50 pages. 

'For I know the plans I have for you,' declares the Lord, 'plans to prosper you and not to harm you, 
plans to give you hope and a future.' - Jeremiah 29:11

We'd be honored to help you share your message more widely. Upgrade to scan websites of any size."
```

**Analysis Complete:**
```
"We've completed analyzing your website! We found {pages} pages of inspiring content that reflects 
Christian values of {themes}.

'Let your light shine before others, that they may see your good deeds and glorify your Father 
in heaven.' - Matthew 5:16

Your chatbot is ready to serve your visitors with grace and wisdom."
```

**Upgrade Prompt:**
```
"Thank you for exploring Prayer-Chat! We're here to help you serve your community better.

'Each of you should use whatever gift you have received to serve others, as faithful stewards 
of God's grace in its various forms.' - 1 Peter 4:10

Upgrade now to unlock the full potential of your ministry chatbot."
```

---

**Last Updated:** 2025-12-20  
**Status:** Final Pricing Plan

