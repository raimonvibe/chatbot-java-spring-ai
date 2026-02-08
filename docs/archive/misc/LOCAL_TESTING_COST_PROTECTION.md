# 🧪 Local Testing Guide: Cost Protection Features

## ✅ What to Test Locally

### 1. **Website Size Estimation** (Pre-Scan Check)
Test that large websites are blocked before scanning:

```bash
# Test with a large website (should be blocked for preview mode)
curl -X POST http://localhost:8081/api/chatbots/1/analyze \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json"
```

**Expected Behavior:**
- Small website (< 50 pages): ✅ Analysis starts
- Large website (> 50 pages): ❌ Returns 403 with error message

### 2. **Scan Frequency Limit** (1 scan/day for preview mode)
Test that users can only scan once per day:

```bash
# First scan - should work
curl -X POST http://localhost:8081/api/chatbots/1/analyze \
  -H "Authorization: Bearer YOUR_TOKEN"

# Second scan (same day) - should be blocked
curl -X POST http://localhost:8081/api/chatbots/1/analyze \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**Expected Behavior:**
- First scan: ✅ Analysis starts
- Second scan (same day): ❌ Returns 403 "Daily scan limit reached"

### 3. **Cost Limit Check** ($5/month for preview mode)
Test that cost limits are enforced:

**To test this:**
1. Create a user in preview mode (no subscription)
2. Set `currentMonthCost` close to limit (e.g., $4.50)
3. Try to scan a website

**Expected Behavior:**
- If estimated cost would exceed limit: ❌ Returns 403 "Monthly cost limit reached"
- If within limit: ✅ Analysis starts

### 4. **Preview Mode Detection**
Test that the system correctly identifies preview mode users:

**Check in H2 Console:**
```sql
-- Check user subscription status
SELECT u.id, u.email, s.plan, s.status 
FROM users u 
LEFT JOIN subscriptions s ON u.id = s.user_id;

-- Check user cost tracking
SELECT id, email, current_month_cost, monthly_cost_limit 
FROM users;
```

## 🚀 Quick Local Test Commands

### Start Backend Locally
```bash
cd backend
mvn spring-boot:run
```

### Start Frontend Locally
```bash
cd frontend
npm run dev
```

### Access H2 Console
- URL: http://localhost:8081/h2-console
- JDBC URL: `jdbc:h2:mem:chatbotdb`
- Username: `sa`
- Password: (empty)

## 📋 Test Checklist

- [ ] **Website size estimation works** (blocks >50 pages for preview)
- [ ] **Scan frequency limit enforced** (1 scan/day for preview)
- [ ] **Cost limit check works** (blocks if would exceed $5/month)
- [ ] **Preview mode detection correct** (identifies users without paid subscription)
- [ ] **Error messages are user-friendly** (clear, Christian-style messaging)
- [ ] **Paid users have no limits** (subscription bypasses all checks)

## 🔍 Debugging Tips

### Check Logs
```bash
# Backend logs
tail -f backend/logs/prayer-chat.log

# Or if running with Maven
# Logs appear in console
```

### Check Database State
```sql
-- View user cost tracking
SELECT * FROM users WHERE email = 'test@example.com';

-- View website scans
SELECT wc.*, c.name as chatbot_name 
FROM website_content wc 
JOIN chatbots c ON wc.chatbot_id = c.id 
ORDER BY wc.created_at DESC 
LIMIT 10;
```

### Test with Different User Types

1. **Preview Mode User** (no subscription):
   - Should have all limits enforced
   - `monthlyCostLimit = 5.00`
   - Max 1 scan/day
   - Max 50 pages/website

2. **Paid User** (active subscription):
   - Should bypass all limits
   - No scan frequency limit
   - No website size limit
   - No cost limit

## 🐛 Common Issues

### Issue: "No subscription found" errors
**Solution:** Ensure test users have proper subscription setup in database

### Issue: Cost tracking not working
**Solution:** Check that `User` entity has cost fields initialized:
- `currentMonthCost` should default to `0.00`
- `monthlyCostLimit` should default to `5.00` for preview mode

### Issue: Website size estimation always returns 10
**Solution:** This is the fallback value. Check:
- Is sitemap.xml accessible?
- Is robots.txt accessible?
- Are there network/firewall issues?

## ✅ Success Criteria

After local testing, you should verify:
1. ✅ Large websites are blocked before scanning (no costs incurred)
2. ✅ Users can't scan more than once per day (preview mode)
3. ✅ Cost limits are enforced and tracked correctly
4. ✅ Error messages are clear and helpful
5. ✅ Paid users have no restrictions

---

**Next Steps:** Once local testing passes, you can safely deploy to Render and test in production environment.

