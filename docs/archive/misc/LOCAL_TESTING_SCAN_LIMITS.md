# 🧪 Local Testing: Resetting Scan Limits

## Problem
When testing locally, you may hit the daily scan limit (1 scan/day for preview mode). This prevents you from testing the scan functionality multiple times.

## Solutions

### Option 1: Use Development Endpoint (Recommended)

The application includes a development-only endpoint to reset scan limits:

1. **Enable Development Mode**
   - Set environment variable: `DEV_MODE_ENABLED=true`
   - Or add to `application.yml`:
     ```yaml
     app:
       dev:
         enabled: true
     ```

2. **Reset Scan Limits via API**
   ```bash
   # Reset scan limits for your user
   curl -X DELETE http://localhost:8081/api/dev/scan-limits \
     -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     -H "Cookie: JSESSIONID=YOUR_SESSION_ID"
   ```

3. **Check Scan Status**
   ```bash
   # Check current scan limit status
   curl http://localhost:8081/api/dev/scan-status \
     -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     -H "Cookie: JSESSIONID=YOUR_SESSION_ID"
   ```

4. **Reset Costs (if needed)**
   ```bash
   # Reset monthly cost tracking
   curl -X POST http://localhost:8081/api/dev/reset-costs \
     -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     -H "Cookie: JSESSIONID=YOUR_SESSION_ID"
   ```

### Option 2: Use H2 Console (Direct Database Access)

1. **Access H2 Console**
   - URL: http://localhost:8081/h2-console
   - JDBC URL: `jdbc:h2:mem:chatbotdb`
   - Username: `sa`
   - Password: (empty)

2. **Delete Scan Audits**
   ```sql
   -- Find your user ID
   SELECT id, email FROM users WHERE email = 'your-email@gmail.com';
   
   -- Delete scan audits for your user (replace USER_ID with your ID)
   DELETE FROM website_scan_audits WHERE user_id = USER_ID;
   ```

3. **Reset Cost Tracking (Optional)**
   ```sql
   -- Reset monthly cost for your user
   UPDATE users 
   SET current_month_cost = 0.00 
   WHERE id = USER_ID;
   ```

### Option 3: Use Frontend (If Implemented)

If the frontend has a "Reset Limits" button in development mode, use that.

## Security Notes

⚠️ **IMPORTANT**: 
- Development endpoints are **ONLY available** when:
  - `spring.profiles.active=dev` is set
  - `app.dev.enabled=true` is set
- These endpoints are **NOT available in production**
- Never enable dev mode in production environments

## About the "Retry error"

The "Retry error. Retry count:1" message is likely from:
- Spring AI's retry mechanism when API calls fail
- This is separate from the scan limit issue
- Check your `ANTHROPIC_API_KEY` and `COHERE_API_KEY` environment variables
- Verify the AI services are properly configured

## Quick Test Checklist

- [ ] Enable development mode (`DEV_MODE_ENABLED=true`)
- [ ] Restart the backend application
- [ ] Reset scan limits via `/api/dev/scan-limits`
- [ ] Try scanning a website again
- [ ] Verify scan works without limit errors

## Troubleshooting

### "Development mode is disabled"
- Set `DEV_MODE_ENABLED=true` environment variable
- Or add to `application.yml`: `app.dev.enabled: true`
- Restart the application

### "Authentication required"
- Make sure you're logged in
- Include your session cookie or JWT token in the request

### Endpoint not found (404)
- Check that `@Profile("dev")` is active
- Verify `spring.profiles.active=dev` is set
- Restart the application

