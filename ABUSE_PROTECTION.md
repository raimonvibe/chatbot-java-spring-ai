# 🛡️ Abuse Protection & Cost Controls

## Problem Statement

**Threat:** Hackers creating multiple accounts to scan large websites (like AWS) without paying, causing high costs for the platform.

**Example Attack:**
1. Attacker creates 100 accounts
2. Each account scans AWS website (15,000 pages, ~$5 cost)
3. Total cost: 100 × $5 = **$500** (one-time)
4. If they also use chat: 100 accounts × $166/month = **$16,600/month**

---

## 🛡️ Protection Layers

### Layer 1: Account Creation Protection

#### 1.1 Email Verification (Automatic via Google OAuth)
- ✅ **Google OAuth users: Email automatically verified** (Google verifies emails before account creation)
- ✅ **No additional verification step needed** for Google login users
- ✅ **Email comes directly from Google** - already verified by Google's system
- ✅ Prevents automated account creation (requires real Google account)

**Implementation:**
```java
// Google OAuth users: Email is automatically verified
// No need to check emailVerified for Google OAuth users
if (user.getAuthProvider() == AuthProvider.GOOGLE) {
    // Email is already verified by Google
    // Proceed with website scanning
} else {
    // For other auth methods (if added in future), require email verification
    if (!user.isEmailVerified()) {
        throw new BusinessException("Email verification required");
    }
}
```

**Why This Works:**
- Google requires email verification before allowing OAuth login
- Users must have access to their Google email to complete OAuth flow
- Google's OAuth system ensures email is valid and verified
- No additional verification step needed = better UX for legitimate users

#### 1.2 CAPTCHA Limitations & Alternatives

**⚠️ CAPTCHA Security Issues (2024):**
- ❌ **AI can bypass CAPTCHAs:** ChatGPT and advanced AI models can solve CAPTCHAs
- ❌ **Cheap solving services:** CAPTCHA-solving services available for $1-2 per 1000 solves
- ❌ **Implementation vulnerabilities:** Many CAPTCHA plugins have security flaws
- ❌ **Accessibility issues:** Difficult for users with disabilities
- ❌ **User frustration:** Poor UX, users abandon registration

**Better Alternatives:**

**Option 1: Google OAuth Only (Recommended)**
- ✅ **No CAPTCHA needed** - Google already verifies users
- ✅ **Better security** - Requires real Google account
- ✅ **Better UX** - One-click login, no puzzles
- ✅ **Harder to automate** - Can't create fake Google accounts at scale

**Option 2: Invisible reCAPTCHA v3 (If needed)**
- ✅ **Background scoring** - No user interaction required
- ✅ **Better UX** - Users don't see CAPTCHA
- ✅ **Risk-based** - Only challenge suspicious users
- ⚠️ **Still bypassable** - But better than traditional CAPTCHA

**Option 3: Rate Limiting + Behavioral Analysis**
- ✅ **IP-based rate limiting** - Already implemented
- ✅ **Account creation patterns** - Detect suspicious behavior
- ✅ **Time-based delays** - Add friction for automated scripts
- ✅ **No CAPTCHA needed** - More user-friendly

**Recommendation:** 
Since we use **Google OAuth exclusively**, CAPTCHA is **NOT NEEDED**. Google OAuth provides better security than CAPTCHA:
- Requires real Google account (harder to fake)
- Email already verified by Google
- Can't be automated at scale
- Better user experience

#### 1.3 IP-Based Rate Limiting
- ✅ **Max 3 accounts per IP per day**
- ✅ Prevents mass account creation from single IP
- ✅ Tracks IP addresses for abuse detection

**Limits:**
- Free tier: 3 accounts/IP/day
- Paid tier: 10 accounts/IP/day (for legitimate use cases)

---

### Layer 2: Free Tier Restrictions

#### 2.1 Website Size Limits
- ✅ **Free tier: Max 50 pages per website**
- ✅ **Free tier: Max 100,000 tokens** (~$0.01 cost)
- ✅ Prevents scanning of large websites without payment

**Example:**
- AWS website: 15,000 pages → **BLOCKED** for free tier
- Small business site: 30 pages → **ALLOWED** for free tier

#### 2.2 Scanning Rate Limits
- ✅ **Free tier: 1 scan per day**
- ✅ **Paid tier: 10 scans per day**
- ✅ Prevents rapid repeated scanning

#### 2.3 Cost Limits Per Account
- ✅ **Free tier: $5/month maximum cost**
- ✅ Automatic account suspension when limit reached
- ✅ Requires upgrade to continue

**Cost Tracking:**
- Website scanning costs tracked per account
- Chat usage costs tracked per account
- Monthly reset on subscription date

---

### Layer 3: Usage Monitoring & Abuse Detection

#### 3.1 Abuse Pattern Detection

**Suspicious Patterns:**
1. **Multiple accounts from same IP**
   - Flag if > 5 accounts from same IP
   - Manual review required

2. **Only scanning, never chatting**
   - Flag if scanCount > 3 AND chatCount = 0
   - Indicates abuse (not real usage)

3. **Rapid account creation**
   - Flag if > 3 accounts created in 1 hour
   - Temporary IP block

4. **Large website scanning without subscription**
   - Already blocked by size limits
   - But track attempts for monitoring

#### 3.2 Automated Monitoring

**Hourly Checks:**
- Count suspicious accounts
- Check cost anomalies
- Detect abuse patterns

**Alerts:**
- Email admin if > 10 suspicious accounts detected
- Auto-disable accounts exceeding cost limits
- Flag accounts for manual review

---

### Layer 4: Cost Controls

#### 4.1 Per-Account Cost Tracking

**Track:**
- Website scanning costs (embedding generation)
- Chat operation costs (Claude API calls)
- Total monthly cost per account

**Enforce:**
- Free tier: $5/month limit
- Paid tier: Based on subscription plan
- Enterprise: Custom limits

#### 4.2 Real-Time Cost Checks

**Before Each Operation:**
```java
// Check if operation would exceed cost limit
BigDecimal estimatedCost = calculateCost(operation);
if (user.getCurrentMonthCost().add(estimatedCost).compareTo(limit) > 0) {
    throw new BusinessException("Cost limit exceeded. Upgrade to continue.");
}
```

**Operations Tracked:**
- Website scanning (one-time cost)
- Chat messages (per-message cost)
- Embedding generation (if needed)

---

## 📊 Free Tier Limits Summary

| Feature | Free Tier Limit | Paid Tier |
|---------|----------------|-----------|
| **Website Pages** | 50 pages max | Unlimited |
| **Website Tokens** | 100,000 tokens | Unlimited |
| **Scans per Day** | 1 scan | 10 scans |
| **Chat Messages** | 10/day | Unlimited |
| **Monthly Cost Limit** | $5.00 | Based on plan |
| **Accounts per IP** | 3/day | 10/day |
| **Email Verification** | Required | Required |

---

## 🚨 Response to Abuse Detection

### Automatic Actions

1. **Cost Limit Reached:**
   - Account suspended
   - User notified: "Upgrade to continue"
   - Can upgrade immediately to reactivate

2. **Suspicious Pattern Detected:**
   - Account flagged for review
   - Operations continue (to avoid false positives)
   - Admin notified for manual review

3. **Multiple Accounts from Same IP:**
   - All accounts flagged
   - Rate limiting increased
   - Manual review required

4. **Abuse Confirmed:**
   - All related accounts banned
   - IP address blocked
   - Cost recovery (if possible)

---

## 💰 Cost Protection Example

### Scenario: Attacker tries to scan AWS website

**Attempt 1: Create account via Google OAuth**
- ✅ Must have real Google account → Requires real email
- ✅ Google OAuth flow → Email automatically verified by Google
- ✅ No way to create fake Google accounts at scale
- ✅ **No CAPTCHA needed** - Google OAuth is more secure than CAPTCHA

**Attempt 2: Try to scan website**
- ✅ Website size check: 15,000 pages > 50 page limit
- ❌ **BLOCKED:** "Free tier limited to 50 pages. Upgrade to scan larger websites."

**Attempt 3: Create multiple accounts**
- ✅ IP rate limit: 3 accounts/IP/day
- ✅ After 3 accounts, blocked: "Too many account creations from this IP"

**Attempt 4: Use different IPs**
- ✅ Still need real Google accounts (can't create fake ones at scale)
- ✅ Website size limit still applies
- ✅ Cost limit: $5/month (can't scan large sites)
- ✅ Abuse detection flags suspicious pattern

**Result:** Attacker can't cause significant costs without paying.

---

## 🔧 Implementation Priority

### Phase 1: Critical (Must Have)
1. ✅ **Google OAuth email verification** (automatic - no extra step needed)
2. ✅ Website size limits (50 pages free tier)
3. ✅ Cost limits per account ($5/month free tier)
4. ✅ IP-based rate limiting (3 accounts/IP/day)

### Phase 2: Important (Should Have)
5. ❌ **CAPTCHA NOT NEEDED** - Google OAuth provides better security
6. ✅ Scanning rate limits (1 scan/day free tier)
7. ✅ Cost tracking per operation

### Phase 3: Monitoring (Nice to Have)
8. ✅ Abuse pattern detection
9. ✅ Automated monitoring & alerts
10. ✅ Manual review workflow

---

## 📝 Success Metrics

**Protection Goals:**
- ✅ Prevent > 95% of abuse attempts
- ✅ Limit free tier abuse cost to < $10/month per attacker
- ✅ Detect abuse patterns within 24 hours
- ✅ Zero false positives for legitimate users

**Monitoring:**
- Track abuse attempts
- Measure cost savings
- Monitor false positive rate
- Adjust limits based on data

---

**Last Updated:** 2025-12-20  
**Status:** Planning Phase - To be implemented in Task 4

