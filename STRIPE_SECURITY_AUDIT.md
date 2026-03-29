# Stripe Payment Security Audit – Attack Vectors Addressed

This document records the security review and fixes applied so the Stripe flow is resistant to abuse and common attack patterns.

---

## 1. Open redirect (Customer Portal return URL)

**Risk:** Attacker tricks a user into using a portal link that redirects to `javascript:`, `data:`, or a phishing domain after “Manage subscription”.

**Mitigations:**
- **Scheme allowlist:** Only `http` and `https`. Reject `javascript:`, `data:`, `vbscript:`, `file:`.
- **Length cap:** Return URL limited to 500 characters (Stripe’s limit; avoids overflow/abuse).
- **Host match:** URL is parsed; host must **exactly** match one of the allowed origins (no `https://yoursite.com.evil.com` or `https://yoursite.com@evil.com`). Comparison is case-insensitive for host.
- **Path scope:** Path must be empty, equals the origin path, or starts with `originPath + "/"` so only your app’s paths are allowed.
- **No auth in URL:** Validation is purely structural; no secrets in the return URL.

**Code:** `SubscriptionController.isAllowedReturnUrl()`.

---

## 2. Webhook replay / duplicate processing

**Risk:** Replay of a valid webhook (or Stripe retries) could double-apply subscription creation/update/cancel.

**Mitigations:**
- **Signature + timestamp:** Stripe’s `Webhook.constructEvent()` verifies HMAC and **timestamp tolerance** (default 300s). Old events fail verification.
- **Event-id deduplication:** Processed event IDs are stored in a bounded map (TTL 24h, max size). If the same event ID is seen again, return `200` without running handlers again.

**Code:** `StripeWebhookController`: `PROCESSED_EVENT_IDS`, `evictOldProcessedEvents()`.

---

## 3. Arbitrary price ID (checkout / plan change)

**Risk:** Attacker sends a custom `price_xxx` from another Stripe account or a different product to get a different price or to abuse your Stripe account.

**Mitigations:**
- **Server-side allowlist:** Only price IDs configured in env (`STRIPE_PRICE_ID`, `STRIPE_PRICE_ID_BASIC`, etc.) are accepted. `StripeService.isAllowedPriceId()` is used for checkout and for change-plan/upgrade/downgrade.
- **Plan enum:** Plan names are restricted to `BASIC`, `PRO`, `ENTERPRISE` (and `FREE` where applicable).

**Code:** `StripeService.resolvePriceId()`, `isAllowedPriceId()`, `upgradeSubscription()`, `downgradeSubscription()`.

---

## 4. Information disclosure (Stripe IDs)

**Risk:** Exposing `stripeCustomerId`, `stripeSubscriptionId`, or `stripePriceId` to the client helps attackers target Stripe or your users.

**Mitigations:**
- **Subscription details API:** `GET /api/subscription/details` no longer returns the full `Subscription` entity. It returns a map of **safe fields only** (id, status, plan, dates, flags). Stripe IDs are never sent to the client.

**Code:** `SubscriptionController.getSubscriptionDetails()` builds a safe `Map` and omits Stripe IDs.

---

## 5. Null / missing request body

**Risk:** `change-plan`, `upgrade`, `downgrade` with `@RequestBody Map<String, String> request` could receive `null` (e.g. wrong Content-Type or empty body), leading to NPE or unclear behavior.

**Mitigations:**
- **Required body:** `@RequestBody(required = false)` and explicit `if (request == null)` with `400` and “Missing request body” so no NPE and clear error.

**Code:** `SubscriptionController.changePlan()`, `upgradePlan()`, `downgradePlan()`.

---

## 6. Webhook IP allowlist (defense in depth)

**Risk:** If the webhook secret were ever compromised, an attacker could send forged events from any IP.

**Mitigations:**
- **Optional IP allowlist:** When `STRIPE_WEBHOOK_IP_ALLOWLIST` is set (comma-separated IPs from [Stripe’s list](https://stripe.com/files/ips/ips_webhooks.txt)), only those IPs are allowed to POST to `/stripe/webhook`. Others get `403`. Signature verification remains the primary check.

**Code:** `StripeWebhookController`: `allowedWebhookIps()`, check before `constructEvent()`.

---

## 7. Authentication and authorization

- **Subscription endpoints:** All `/api/subscription/*` require an authenticated user (Spring Security `authenticated()`). Actions are scoped to the current user (e.g. `currentUser.getUser().getId()`).
- **Webhook:** No user auth; authenticity is by **signature + optional IP allowlist**. No cookies/session used for webhook.

---

## 8. Checkout and portal URLs

- **Success/cancel URLs:** Taken only from server config (`stripe.success-url`, `stripe.cancel-url`). Not client-supplied, so no open redirect from checkout.
- **Frontend:** Before redirecting, the app checks that checkout URL host is `checkout.stripe.com` or `checkout.stripe.dev`, and portal URL host is `billing.stripe.com` or `billing.stripe.dev`.

---

## 9. Idempotency

- **Checkout/portal session creation:** Idempotency keys (per user + plan or user, 5-min bucket) are sent to Stripe so duplicate requests (double-click, retries) return the same session instead of creating multiple.

---

## 10. Other checks

- **Secrets:** Stripe secret key and webhook secret are server-side only (env/config). Not logged or returned to the client.
- **Errors:** Client-facing errors are generic; `LogSanitizer` used in logs to avoid leaking sensitive data.
- **CSRF:** Webhook is excluded from CSRF; Stripe does not use cookies for this endpoint. Subscription APIs use session/JWT; CORS and credentials are configured per your SecurityConfig.

---

## Summary

| Threat | Mitigation |
|--------|------------|
| Open redirect (portal return URL) | Scheme allowlist, length cap, host + path validation |
| Webhook replay / duplicate processing | Signature + timestamp (SDK) + event-id deduplication |
| Arbitrary price ID | Server-side allowlist of configured price IDs |
| Stripe ID leakage | Details endpoint returns safe fields only |
| NPE on missing body | Null check and 400 for change-plan/upgrade/downgrade |
| Forged webhook (defense in depth) | Optional IP allowlist |
| Double session creation | Idempotency keys for checkout and portal |

These measures make the Stripe flow robust against the listed attack vectors while keeping normal behavior and Stripe best practices intact.
