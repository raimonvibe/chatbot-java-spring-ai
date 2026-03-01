# Stripe Checkout Security

Summary of security measures for the subscription checkout flow and where they are tested. The code is reviewed so that validation and consistency are enforced in both controller and service.

## Server-side session creation

- Checkout sessions are **created only on the backend** with the secret API key. The client never receives or sends Stripe secret keys.
- **Idempotency key** per user+plan (5‑minute window) prevents duplicate sessions on double-clicks or retries.
- **client_reference_id** is set to `user_<id>` for reconciliation with Stripe Dashboard and webhooks.

## Price and plan validation

- **Plan** from the client is restricted to `BASIC`, `PRO`, or `ENTERPRISE`. Any other value returns 400. **FREE** is rejected for checkout and for change-plan/upgrade/downgrade (use cancel instead).
- **Price ID** from the client is accepted only if it is in the configured allowlist. The **controller** rejects disallowed price IDs with 400 "Price ID not allowed" for create-checkout-session, change-plan, upgrade, and downgrade. The service also validates and enforces that **priceId matches the requested plan** in `changeSubscriptionPlan` (prevents inconsistent DB vs Stripe state).
- `StripeService.isAllowedPriceId()` enforces exact match; substring or whitespace-only inputs are rejected.
- **Stripe payloads**: `handleSubscriptionCreated` guards against empty `items.getData()` and null/blank customer ID; `handleSubscriptionUpdated`/`handleSubscriptionDeleted` guard against null/blank subscription ID; upgrade/downgrade guard against empty subscription items and throw instead of NPE.
- **Defensive null checks**: `createCheckoutSession`, `createBillingPortalSession`, `getOrCreateCustomer`, and `clearStripeCustomerIdForUser` validate that `user` and `user.getId()` are non-null before proceeding (avoid NPE and ensure we never clear or create for an invalid user).

## Authentication and method

- **Authentication required**: `create-checkout-session` and `create-portal-session` require an authenticated user (`@AuthenticationPrincipal`). Unauthenticated requests receive 401.
- **POST only**: `create-checkout-session` is mapped with `@PostMapping`. GET requests receive 405 Method Not Allowed (avoids misuse or crawlers hitting the URL).
- **No IDOR**: All subscription operations use only the authenticated user from `@AuthenticationPrincipal`; no user ID is read from the request body, so a client cannot act on another user’s subscription.

## Redirect URLs

- **Checkout** `success_url` and `cancel_url` come from **environment variables** only (server-controlled). The client cannot change them.
- **Portal** `returnUrl` may be sent in the request body but is validated with **isAllowedReturnUrl()**: only HTTPS (or HTTP for localhost), host must match CORS allowed origins, no `javascript:`, `data:`, etc., and length is capped.

## Tests

| Area | Test | File |
|------|------|------|
| Price allowlist | Only configured price IDs return true; null, blank, whitespace, and unknown `price_xxx` rejected | `StripeServiceTest` |
| Price allowlist | Substring / suffix attempts (e.g. `price_ok_evil`) rejected | `StripeServiceTest.security_isAllowedPriceId_rejectsWhitespaceAndSubstringAttempts` |
| Checkout priceId | Disallowed priceId returns 400 "Price ID not allowed" | `SubscriptionControllerIT.shouldReturn400ForDisallowedPriceId_createCheckoutSession` |
| Plan FREE | change-plan with plan=FREE returns 400 (use cancel) | `SubscriptionControllerIT.shouldReturn400ForPlanFree_changePlan` |
| Method | GET `create-checkout-session` returns 405 when authenticated (POST only) | `SubscriptionControllerIT.security_getCreateCheckoutSession_returns405` |
| Auth | Unauthenticated POST returns 401 | `SubscriptionControllerIT.shouldReturn401_whenUnauthenticated_createCheckoutSession` |
| Plan | Invalid plan returns 400 | `SubscriptionControllerIT.shouldReturn400ForInvalidPlan_createCheckoutSession` |
| Price ID | `invalid_price_id` or empty priceId returns 400 | `SubscriptionControllerIT.shouldReturn400ForInvalidPriceId_createCheckoutSession` |
| Portal returnUrl | Disallowed origin returns 400 | `SubscriptionControllerIT` (evil.com) |
| Portal returnUrl | `javascript:` URL returns 400 | `SubscriptionControllerIT.security_portalSession_rejectsJavascriptReturnUrl` |
| No-such-customer retry | Only `resource_missing` + "No such customer" triggers clear-and-retry; other errors are not retried | `StripeServiceTest.security_isNoSuchCustomer_*` (4 tests) |
| IDOR | Checkout is invoked with authenticated user only (no user ID from body) | `SubscriptionControllerIT.security_createCheckoutSession_usesAuthenticatedUserOnly` |
| IDOR | Portal session is invoked with authenticated user only | `SubscriptionControllerIT.security_createPortalSession_usesAuthenticatedUserOnly` |
| IDOR | Cancel, change-plan, upgrade, downgrade use authenticated user ID only | `SubscriptionControllerIT.security_cancelSubscription_calledWithAuthenticatedUserId`, `security_changePlan_calledWithAuthenticatedUserId`, `security_upgrade_calledWithAuthenticatedUserId`, `security_downgrade_calledWithAuthenticatedUserId` |
| IDOR | Status and details use authenticated user ID only (no path/query user ID) | `SubscriptionControllerIT.security_getSubscriptionStatus_usesAuthenticatedUserId`, `security_getSubscriptionDetails_usesAuthenticatedUserId` |
| Null user | createCheckoutSession / createBillingPortalSession throw when user or user.getId() is null | `StripeServiceTest.security_createCheckoutSession_throwsWhenUserNull`, `_throwsWhenUserIdNull`, `security_createBillingPortalSession_throwsWhenUserNull` |
| Webhook unknown customer | handleSubscriptionCreated does not save when customer ID is null/blank or when no subscription found | `StripeServiceTest.security_handleSubscriptionCreated_skipsWhenCustomerIdNull`, `_skipsWhenCustomerIdBlank`, `_noSaveWhenUnknownCustomer` |
| Webhook null subscription ID | handleSubscriptionUpdated / handleSubscriptionDeleted skip and do not save when subscription ID is null | `StripeServiceTest.security_handleSubscriptionUpdated_skipsWhenSubscriptionIdNull`, `security_handleSubscriptionDeleted_skipsWhenSubscriptionIdNull` |

## Webhook security

- **Signature verification**: All webhook requests are verified with `Webhook.constructEvent(payload, Stripe-Signature, webhookSecret)` before any processing. Invalid or missing signature returns 400; no event is processed.
- **Raw body**: The endpoint uses `@RequestBody String payload` so the body is not parsed before verification (required for correct HMAC verification).
- **Event idempotency**: Processed event IDs are stored in memory; the same event ID is skipped and we return 200 so Stripe stops retrying. For high concurrency, a DB table with a unique constraint on `event_id` is recommended.
- **No such customer recovery**: If checkout or portal session creation fails with Stripe "No such customer" (e.g. after Test/Live switch), the app clears the stored customer ID and retries once by creating a new customer; no manual DB fix needed.

## References

- Stripe: [Create a Checkout Session](https://stripe.com/docs/api/checkout/sessions/create) (server-side only).
- Stripe: [Client reference ID](https://docs.stripe.com/api/checkout/sessions/create#create_checkout_session-client_reference_id) for reconciliation.
- Stripe: [Webhook signature verification](https://docs.stripe.com/webhooks/signature).
- Project: `StripeService`, `SubscriptionController`, `StripeWebhookController`, `STRIPE_RENDER_ENV.md`.
