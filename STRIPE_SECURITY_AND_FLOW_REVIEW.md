# Stripe Payment – Security & Flow Review

This document summarizes the security and flow review of the Stripe payment feature and the changes made to keep it secure and smooth.

## Security

### 1. **Webhook**

- **Signature verification**: All webhook requests are verified with `Webhook.constructEvent(payload, sigHeader, webhookSecret)`. Invalid or missing signature → `400 Bad Request`; no Stripe logic runs.
- **Missing/empty secret**: If `STRIPE_WEBHOOK_SECRET` is not set, the endpoint returns `503 Service Unavailable` and does not process the body.
- **Missing header**: If `Stripe-Signature` is missing or blank, the endpoint returns `400` with "Missing signature" and does not call Stripe or internal services.
- **CSRF**: Webhook URL is excluded from CSRF (`SecurityConfig`); Stripe does not send cookies, so cookie-based CSRF is not a concern. Verification is done via the signature only.

### 2. **Checkout & portal URLs**

- **Checkout**: Success and cancel URLs are taken only from server config (`stripe.success-url`, `stripe.cancel-url`), not from the client. No open redirect from checkout.
- **Billing portal**: `returnUrl` from the client is validated in `SubscriptionController.isAllowedReturnUrl()` against `cors.allowed-origins` (and localhost when no origins are set). Only allowed origins or paths under them are accepted.
- **Frontend**: Before redirecting, the frontend checks that:
  - Checkout URL host is `checkout.stripe.com` or `checkout.stripe.dev`.
  - Portal URL host is `billing.stripe.com` or `billing.stripe.dev`.

### 3. **Price ID validation**

- **Issue**: The API previously accepted any `price_xxx` from the client for checkout and plan changes, which could have allowed subscribing to arbitrary Stripe prices.
- **Change**:  
  - `StripeService.isAllowedPriceId(priceId)` returns true only for configured price IDs (`stripe.price-id`, `stripe.price-id-basic`, `stripe.price-id-pro`, `stripe.price-id-enterprise`).  
  - Checkout: when the client sends a raw `price_xxx`, it is used only if `isAllowedPriceId(priceId)` is true; otherwise the default configured price is used.  
  - Plan change/upgrade/downgrade: `upgradeSubscription` and `downgradeSubscription` throw if `!isAllowedPriceId(newPriceId)`.

### 4. **Authentication & authorization**

- Subscription endpoints (`/api/subscription/*`) require an authenticated user (`SubscriptionController` uses `@AuthenticationPrincipal`). Only the current user’s subscription is used (e.g. by `user.getId()`).
- Webhook is unauthenticated by design; authenticity is ensured by the Stripe signature.

### 5. **Sensitive data**

- Stripe secret key and webhook secret are server-side only (config/env).
- Errors returned to the client are generic (e.g. "Failed to create checkout session"); `LogSanitizer` is used in logs to avoid leaking sensitive data.
- Account page hides technical/stack details in user-facing error messages.

### 6. **CSP**

- `Content-Security-Policy` allows Stripe for scripts and frames: `https://js.stripe.com`, and `connect-src` includes `https://api.stripe.com`, so Stripe.js and API calls are allowed as intended.

---

## Flow and UX

### 1. **Pricing page – subscribe**

- **Double submit**: A `subscribingPlan` state blocks concurrent subscribe clicks and disables all plan buttons while a request is in progress.
- **Loading**: The clicked plan’s button shows a spinner and "Redirecting…" until redirect or error.
- **Errors**: On non-OK or invalid URL, `subscribingPlan` is cleared so the user can try again. User sees an alert with a clear message (e.g. 503 → "Payments are not configured yet...").

### 2. **Paywall modal**

- **Double submit**: `loading` prevents multiple simultaneous checkout requests.
- **Checkout URL**: Same host checks as pricing page (Stripe checkout only) before redirect.
- **Errors**: On failure, `loading` is reset and user sees an alert.

### 3. **Account page – billing portal**

- **Loading**: `portalLoading` disables the "Manage subscription" action and avoids double clicks.
- **Redirect**: Portal URL is validated (Stripe billing hosts only) before `window.location.href` is set.
- **Errors**: Sensitive messages are not shown; user sees a generic message and can retry.

### 4. **Backend**

- Checkout: If Stripe is not configured, returns `503` with a clear message; no redirect.
- Portal: Invalid `returnUrl` → `400` with "Invalid return URL".
- Plan/upgrade/downgrade: Invalid or disallowed `priceId` → `400` with a safe error message; no Stripe call with arbitrary price IDs.

---

## Configuration checklist

- **Production**
  - Set `STRIPE_SECRET_KEY` (and optionally per-plan `STRIPE_PRICE_ID_*`).
  - Set `STRIPE_WEBHOOK_SECRET` and configure the webhook in Stripe Dashboard to point to `https://your-api-host/stripe/webhook`.
  - Set `stripe.success-url` and `stripe.cancel-url` (and `cors.allowed-origins`) to your real frontend origins.
- **Testing**
  - Use Stripe test keys and test webhook secret; webhook can be tested with Stripe CLI (`stripe listen --forward-to localhost:8081/stripe/webhook`).

---

## Summary

- **Security**: Webhook is verified and guarded; checkout/portal URLs are server- or allowlist-controlled; price IDs are restricted to configured ones; subscription APIs are authenticated and scoped to the current user; secrets stay server-side and errors are sanitized.
- **Flow**: Subscribe and portal flows have loading states and guards against double submit; errors are handled and state is reset so the user can retry with a smooth experience.
