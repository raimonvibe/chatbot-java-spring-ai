# API Key & Secret Rotation Procedure

**Purpose:** Rotate credentials (JWT, Stripe, OAuth, AI API keys) securely with minimal downtime.  
**When:** After a suspected leak, periodically (e.g. annually), or when an team member with access leaves.

---

## 1. JWT secret (`JWT_SECRET`)

**Used for:** Signing and validating access/refresh tokens. Rotating invalidates all existing tokens.

**Steps:**

1. Generate a new secret (e.g. 256-bit):  
   `openssl rand -base64 32`
2. Set the new value in your environment (Render dashboard, `.env`, etc.) as `JWT_SECRET`.
3. Deploy or restart the backend so it picks up the new secret.
4. **Effect:** All existing JWTs become invalid; users must sign in again. No need to revoke old secret elsewhere.

**Rollback:** Revert `JWT_SECRET` to the previous value and redeploy (not recommended if rotation was due to a leak).

---

## 2. Stripe keys

### Secret key (`STRIPE_SECRET_KEY`)

1. In [Stripe Dashboard → Developers → API keys](https://dashboard.stripe.com/apikeys), create a new secret key (or use an existing unused one).
2. Update `STRIPE_SECRET_KEY` in your environment and redeploy.
3. Optionally restrict or roll the old key in Stripe so it can’t be used.

**Note:** Webhook signing secret is separate (see below).

### Webhook signing secret (`STRIPE_WEBHOOK_SECRET`)

- Each webhook endpoint has its own signing secret. If you create a new endpoint, you get a new secret.
1. In Stripe: Developers → Webhooks → your endpoint → “Reveal” or “Roll” signing secret (if available), or add a new endpoint and get a new secret.
2. Update `STRIPE_WEBHOOK_SECRET` in your environment and redeploy.
3. Ensure the webhook URL (e.g. `https://your-api.com/stripe/webhook`) is correct so events still deliver.

---

## 3. Google OAuth (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`)

**Used for:** Login and user identity. Rotating client secret does not invalidate existing sessions until tokens are refreshed.

**Steps:**

1. In [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → Credentials:
   - **Option A:** Add a new “Client secret” for the same OAuth 2.0 Client ID. Update `GOOGLE_CLIENT_SECRET` to the new value; keep `GOOGLE_CLIENT_ID`. Redeploy. You can later remove the old secret.
   - **Option B:** Create a new OAuth 2.0 Client ID (e.g. “Web client 2”), set the same authorized redirect URIs, then switch env to the new Client ID and Client Secret and redeploy.
2. Update frontend `NEXT_PUBLIC_GOOGLE_CLIENT_ID` if you changed the Client ID.
3. Redeploy backend and frontend. Existing sessions may continue until refresh; new logins use the new credentials.

---

## 4. AI / third‑party API keys

**Examples:** `ANTHROPIC_API_KEY`, `COHERE_API_KEY`.

**Steps:**

1. In each provider’s dashboard, create a new API key (or a new project with a new key).
2. Update the corresponding env var(s) in your environment and redeploy.
3. Revoke or delete the old key in the provider’s UI to prevent reuse.

**Rollback:** Revert the env var to the old key only if the old key is still active and you accept the risk.

---

## 5. Security alert webhook (optional)

**`SECURITY_ALERT_WEBHOOK_URL`:** If you use a Slack or other webhook for security alerts:

1. Create a new incoming webhook (or equivalent) in the target system.
2. Update `SECURITY_ALERT_WEBHOOK_URL` and redeploy.
3. Disable or delete the old webhook.

---

## Checklist (all rotations)

- [ ] New credential generated or created in provider dashboard.
- [ ] Environment updated (Render / Vercel / `.env` as appropriate).
- [ ] Backend and/or frontend redeployed and verified.
- [ ] Old credential revoked or restricted where applicable.
- [ ] Team notified if users must re-login (e.g. after JWT rotation).
- [ ] Audit log or runbook updated with rotation date and reason.

---

## Future improvements (Phase 3)

- **Key versioning:** Support multiple JWT secrets (e.g. current + previous) during a short overlap window to allow graceful rotation.
- **Expiration alerts:** Calendar or monitoring reminders for rotating OAuth client secrets and API keys on a schedule.

---

*See also: SECURITY_AUDIT_PLAN.md (Phase 3.1), RENDER_ENV_VARIABLES.md.*
