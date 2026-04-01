# What to do next

Actionable next steps, in order of priority. Use this as a checklist.

---

## 1. One-off: Stripe and production DB

### Allow null `stripe_customer_id` (if you haven’t already)

After the “No such customer” auto-recovery change, the app can clear the stored Stripe customer ID. The DB column must allow NULL.

- **If you see** “null value in column stripe_customer_id violates not-null constraint” **in logs:**
  1. Connect to your **Render PostgreSQL** (Dashboard → PostgreSQL → Connect, or `psql` with the internal URL).
  2. Run once:  
     `ALTER TABLE subscriptions ALTER COLUMN stripe_customer_id DROP NOT NULL;`
  3. Redeploy the backend (or leave as-is; the next deploy will use the updated code).

- **If you don’t see that error:** Either the column was already updated by Hibernate (`ddl-auto=update`) or you’re on a fresh DB. No action needed unless the error appears.

See: `STRIPE_RENDER_ENV.md` (section on “No such customer”).

---

## 2. Stripe: Webhook and env (production)

- [ ] **Add a webhook for this app** in Stripe (separate from imageconverter or other apps):
  - **Stripe Dashboard** → Developers → Webhooks → **Add destination**.
  - **Endpoint URL:** `https://chatbot-java-spring-ai.onrender.com/stripe/webhook` (or your real backend URL).
  - **Events:** at least `customer.subscription.created`, `customer.subscription.updated`, `customer.subscription.deleted`, `invoice.payment_succeeded`, `invoice.payment_failed`.
  - Copy the **Signing secret** (`whsec_...`) and set it on Render as **`STRIPE_WEBHOOK_SECRET`**.
- [ ] **Confirm env on Render:** `STRIPE_SECRET_KEY`, `STRIPE_PRICE_ID` (and optional `STRIPE_PRICE_ID_BASIC` etc.), `STRIPE_SUCCESS_URL`, `STRIPE_CANCEL_URL`, `STRIPE_WEBHOOK_SECRET`. All must match the same Stripe mode (Test or Live).

See: `STRIPE_WEBHOOK_SETUP.md`, `STRIPE_RENDER_ENV.md`.

---

## 3. Verify production

- [ ] **Smoke test:** Open your live app → log in → open subscription/pricing → click “Subscribe” (or equivalent). You should get redirected to Stripe Checkout (no 500). If you see 500, check **Render → Backend → Logs** for the exact Stripe error (e.g. “No such price” → fix Price ID / Test vs Live).
- [ ] **After first successful payment (or test payment):** Confirm in Stripe Dashboard that the subscription and customer exist, and that your app’s webhook received events (Stripe → Webhooks → your endpoint → “Recent deliveries”).

---

## 4. App-wide security and quality: audit plan

The **test-and-code audit** (analyse code, fix gaps, then align tests) is documented in **`TEST_AND_CODE_AUDIT_PLAN.md`**. Subscription & Stripe is already done as the reference; the rest of the app is to be audited in phases.

**Suggested order:**

| Order | Area | Doc section | Main focus |
|-------|------|-------------|------------|
| 1 | **Authentication & auth-dependent endpoints** | §2 | 401 when unauthenticated; no user ID from body (IDOR). |
| 2 | **Chatbot CRUD & usage** | §3 | Ownership, validation, IDOR, null/empty. |
| 3 | **Chat / messages** | §4 | Scope by user/chatbot; rate limits; max length. |
| 4 | **Website analysis / crawling** | §5 | URL validation; timeouts; no RCE. |
| 5 | **Bible / embeddings / data loading** | §6 | Transactions; table names; limits. |
| 6 | **Webhooks (Stripe, others)** | §7 | Signature; idempotency; null-safe payload. |
| 7 | **API surface & errors** | §8 | No stack traces in prod; logging; CORS. |
| 8 | **Frontend** (if in scope) | §9 | Token in calls; validation; no secrets in client. |

For each area: list files → read code → list gaps → fix code → update/add tests → tick in the doc.

See: **`docs/TEST_AND_CODE_AUDIT_PLAN.md`**.

---

## 5. Optional / when you have time

- **Run full test suite** before each deploy:  
  `cd backend && mvn test` and frontend tests (e.g. `npm test`).
- **Review logs** for PII: we use `LogSanitizer` in many places; if you add new log lines with user email or IDs, use `LogSanitizer.sanitize(...)`.
- **Stripe webhook idempotency at scale:** The app uses an in-memory map for processed event IDs. For very high traffic, consider a DB table with `UNIQUE(event_id)` and insert-before-process. See comment in `StripeWebhookController` and `STRIPE_CHECKOUT_SECURITY.md`.

---

## Quick reference: key docs

| Topic | Document |
|-------|----------|
| Stripe env and troubleshooting | `STRIPE_RENDER_ENV.md` |
| Stripe webhook setup (this app) | `STRIPE_WEBHOOK_SETUP.md` |
| Stripe security and tests | `STRIPE_CHECKOUT_SECURITY.md` |
| App-wide test-and-code audit | `TEST_AND_CODE_AUDIT_PLAN.md` |
| Upload embeddings to Render | `UPLOAD_EMBEDDINGS_TO_RENDER.md` (if applicable) |

---

**Summary:** Do the one-off DB change if needed, finish Stripe webhook and env on Render, smoke-test production, then work through the audit plan area by area when you have time.
