# Stripe Payment Flow – Latest Security Research (2024–2025)

This document summarizes **current** Stripe and industry guidance on coding a secure payment flow, and how it maps to this codebase.

## 1. Official Stripe security guide

**Source:** [Stripe Integration security guide](https://stripe.com/docs/security/guide)

| Recommendation | What it means | Our implementation |
|----------------|---------------|--------------------|
| **Use low-risk integrations** | Don’t handle raw card data. Use Stripe Checkout or Elements so card data goes straight to Stripe. | ✅ We use **Stripe Checkout**; card data never touches our server. |
| **TLS/HTTPS** | Payment and webhook endpoints must use TLS 1.2+. All Stripe API calls use HTTPS. | ✅ Production uses HTTPS; Stripe Java SDK uses HTTPS. |
| **Verify webhook signatures** | Use `Stripe-Signature` and `Webhook.constructEvent(payload, sigHeader, endpointSecret)` so only Stripe can trigger logic. | ✅ `StripeWebhookController` verifies before processing. Rejects missing/empty secret or signature. |
| **Allowlist Stripe webhook IPs** | Restrict webhook endpoint so only Stripe’s IPs can POST. | ✅ **Optional:** Set `STRIPE_WEBHOOK_IP_ALLOWLIST` (comma-separated IPs from [Stripe's list](https://stripe.com/files/ips/ips_webhooks.txt)) to enable. When set, requests from other IPs get 403. |
| **CSP for Stripe** | Allow Stripe domains in Content-Security-Policy so Checkout/Elements work. | ✅ `SecurityConfig` allows `js.stripe.com`, `api.stripe.com`, `checkout.stripe.com`, `frame-src` etc. per [Stripe CSP docs](https://stripe.com/docs/security/guide#content-security-policy). |

## 2. Webhook implementation

**Sources:** [Stripe webhooks](https://docs.stripe.com/webhooks), [Webhook signature verification](https://docs.stripe.com/webhooks/signature), [Receive events](https://docs.stripe.com/webhooks/configure)

- **Raw body:** Signature verification **must** use the **exact** request body (UTF-8, unmodified). If the framework parses or reorders JSON, verification can fail.
  - **Our setup:** We use `@RequestBody String payload`. In Spring Boot this is the raw body as a string; no JSON parsing is applied to it, so verification is valid. If you ever add a global JSON mapper that parses all request bodies, exclude the webhook path.
- **Return 2xx quickly:** Stripe recommends returning a successful status (e.g. `200`) **before** long-running work (e.g. DB updates, external calls) to avoid timeouts and retries. Optionally process the event asynchronously after returning.
  - **Our setup:** We verify the signature, handle the event (DB updates), then return `200`. For very heavy work, consider returning `200` immediately and processing in a queue/async.
- **Idempotency:** The same event can be delivered more than once. Handlers should be idempotent (e.g. upsert by `event.id` or subscription id).
  - **Our setup:** Subscription create/update/delete handlers update by Stripe subscription/customer id; duplicate events overwrite with same state, which is idempotent.

## 3. API keys and secrets

**Source:** [Best practices for managing secret API keys](https://docs.stripe.com/keys-best-practices)

- **Server-side only:** Secret keys must never be in frontend, mobile apps, or public repos. Use env vars or a secret manager.
  - ✅ We use `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET` from env; backend only.
- **Rotation:** Rotate keys periodically and have a plan for immediate rotation if exposed.
  - Document in runbooks; no code change.
- **Restricted keys:** Prefer [restricted API keys](https://docs.stripe.com/keys#create-restricted-api-secret-key) with minimal permissions (e.g. only Customers, Subscriptions, Checkout) instead of full secret key where possible.
  - Optional for this app; if you introduce a separate service that only needs a subset of APIs, use a restricted key there.
- **Limit key by IP:** In Stripe Dashboard you can restrict which IPs can use a key. Useful if your server has a fixed outbound IP.
  - Optional; configure in Dashboard.

## 4. Checkout and Customer Portal

- **Success/cancel URLs:** Must be under your control. Never take redirect URLs from the client for checkout.
  - ✅ We use server-side config only (`stripe.success-url`, `stripe.cancel-url`).
- **Customer Portal `return_url`:** Stripe allows a client-supplied `return_url`. Validate it to prevent open redirects (e.g. allow only your origins or paths).
  - ✅ We validate in `SubscriptionController.isAllowedReturnUrl()` against `cors.allowed-origins` (and localhost when unset). Frontend also checks portal URL is `billing.stripe.com` or `billing.stripe.dev` before redirecting.
- **Price IDs:** Only allow Stripe Price IDs that you configure (e.g. BASIC/PRO/ENTERPRISE). Reject arbitrary `price_xxx` from the client.
  - ✅ We restrict to configured price IDs via `StripeService.isAllowedPriceId()` for checkout and plan changes.

## 5. Optional: Webhook IP allowlist

Stripe’s security guide says: *“verify webhook signatures and allowlist Stripe’s IP addresses to ensure that every Stripe webhook you receive is sent exclusively by Stripe.”*

- **Signature verification** is the main guarantee; Stripe and many practitioners consider it sufficient on its own.
- **IP allowlisting** is implemented as an optional extra: set `STRIPE_WEBHOOK_IP_ALLOWLIST` to a comma-separated list of IPs (from [Stripe’s list](https://stripe.com/files/ips/ips_webhooks.txt)). When set, only requests from those IPs are accepted; others get 403. If unset, no IP check is performed.
- **Trade-offs:** IP list can change; update the env when Stripe publishes changes. When behind a proxy/load balancer, `request.getRemoteAddr()` may be the proxy IP—ensure the proxy forwards the real client IP or add the proxy’s egress IP to the list.

## 6. Idempotency keys (Stripe API)

**Source:** [Idempotent requests](https://docs.stripe.com/api/idempotent_requests)

- For **POST** requests that create or change state (e.g. creating a PaymentIntent, Subscription, or one-off charge), Stripe supports **idempotency keys** so retries don’t create duplicates.
- **Checkout Session creation:** Stripe’s Create Checkout Session API accepts an idempotency key. If the client retries (e.g. double-click), the same key returns the same session.
- **Our setup:** We send an idempotency key when creating checkout and billing portal sessions. The key is derived from user id + plan (for checkout) or user id (for portal) plus a 5-minute time bucket, so duplicate requests within the window return the same Stripe session without creating a second one.

## 7. PCI and card data

- We do **not** handle card numbers, CVC, or raw PAN. Stripe Checkout collects and tokenizes card data on Stripe’s side.
- You may store **non-sensitive** data Stripe returns (e.g. last4, brand, exp month/year) per [Stripe’s PCI guidance](https://stripe.com/docs/security/guide#out-of-scope-card-data).
- Annual PCI attestation and any SAQ still apply to your business; using Checkout reduces scope.

## 8. Summary checklist (secure coding)

| Item | Status |
|------|--------|
| Use Stripe Checkout (no card on server) | ✅ |
| TLS/HTTPS in production | ✅ |
| Webhook: verify signature, reject if secret/signature missing | ✅ |
| Webhook: use raw request body for verification | ✅ (String payload) |
| Checkout: success/cancel URLs from server config only | ✅ |
| Portal: validate return_url (allowlist origins) | ✅ |
| Restrict price IDs to configured list | ✅ |
| Secret keys only on server, from env | ✅ |
| CSP allows required Stripe domains | ✅ |
| Webhook IP allowlist | Optional: set `STRIPE_WEBHOOK_IP_ALLOWLIST` (see §5) |
| Idempotency key on Checkout/Portal session creation | ✅ Implemented (5-min bucket per user/plan) |
| Return 200 quickly, process webhook async | Optional for heavy work |

## References

- [Stripe Integration security guide](https://stripe.com/docs/security/guide)
- [Stripe Webhooks (configure & verify)](https://docs.stripe.com/webhooks)
- [Stripe Webhook signature verification](https://docs.stripe.com/webhooks/signature)
- [Stripe API keys best practices](https://docs.stripe.com/keys-best-practices)
- [Stripe Idempotent requests](https://docs.stripe.com/api/idempotent_requests)
- [Stripe Webhook IP list](https://stripe.com/files/ips/ips_webhooks.txt)
- [Stripe Checkout fulfillment (webhook + redirect)](https://docs.stripe.com/payments/checkout/fulfill-orders)
