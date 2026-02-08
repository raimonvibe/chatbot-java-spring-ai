# Stripe Payments – Safe Setup (2026)

This app uses **Stripe Checkout** so card data never touches your server ([Stripe security guide](https://docs.stripe.com/security/guide)). Follow this checklist to enable payments safely.

## 1. Use environment variables only

- **Never** put `STRIPE_SECRET_KEY` or `STRIPE_WEBHOOK_SECRET` in code or in the frontend.
- Set them as **environment variables** (e.g. Render dashboard, or `.env` locally).
- Backend reads them via `application.yml`: `stripe.api-key`, `stripe.webhook-secret`.

## 2. Required environment variables

| Variable | Purpose | Where to get it |
|----------|---------|------------------|
| `STRIPE_SECRET_KEY` | Backend API calls (create session, etc.) | Stripe Dashboard → Developers → API keys → Secret key |
| `STRIPE_WEBHOOK_SECRET` | Verify webhook signatures | Stripe Dashboard → Developers → Webhooks → your endpoint → Signing secret |
| `STRIPE_PRICE_ID` | (Optional) Use an existing Price for the subscription | Stripe Dashboard → Products → your product → Price ID |
| `STRIPE_SUCCESS_URL` | Where to redirect after payment | e.g. `https://yourdomain.com/dashboard?session_id={CHECKOUT_SESSION_ID}` |
| `STRIPE_CANCEL_URL` | Where to redirect if user cancels | e.g. `https://yourdomain.com/pricing` |

If `STRIPE_SECRET_KEY` is missing, the API returns **503 Payment provider not configured** instead of calling Stripe.

## 3. Webhook security

- The app **always** verifies the `Stripe-Signature` header using `STRIPE_WEBHOOK_SECRET` before processing any event.
- Endpoint: `POST /stripe/webhook` (no auth; Stripe is not logged in – verification is by signature).
- In Stripe Dashboard, add a webhook endpoint with URL: `https://your-backend.com/stripe/webhook`.
- Subscribe to: `customer.subscription.created`, `customer.subscription.updated`, `customer.subscription.deleted`, `invoice.paid`, `invoice.payment_failed`.

## 4. Frontend

- The frontend **never** sees the secret key or webhook secret.
- It only calls your backend: `POST /api/subscription/create-checkout-session` (authenticated).
- Backend returns `checkoutUrl`; frontend redirects the user to Stripe Checkout.
- CSP already allows `https://js.stripe.com` and `https://api.stripe.com` where needed.

## 5. Production checklist

- [ ] `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET` set in production env only.
- [ ] Webhook endpoint uses **HTTPS** and the correct URL.
- [ ] `STRIPE_SUCCESS_URL` and `STRIPE_CANCEL_URL` point to **your** domain (no open redirects).
- [ ] Stripe Dashboard is in **Live** mode for real payments; use **Test** mode and test keys for development.
- [ ] If you expose subscription status, do not leak Stripe IDs to untrusted clients unless needed.

## 6. Customer Billing Portal (manage subscription)

- **Endpoint:** `POST /api/subscription/create-portal-session` (authenticated). Optional body: `{ "returnUrl": "https://yourdomain.com/dashboard" }`.
- **Response:** `{ "portalUrl": "https://billing.stripe.com/..." }`. Redirect the user to this URL so they can update payment method, cancel, or view invoices.
- **Dashboard:** A "Manage subscription" button calls this and redirects to the portal; when done, Stripe sends the user back to your `returnUrl`.

## 7. When Stripe is not configured

- If `STRIPE_SECRET_KEY` is empty, `POST /api/subscription/create-checkout-session` and `POST /api/subscription/create-portal-session` return **503** with `{ "error": "Payment provider not configured" }`.
- Frontend can show a “Payments coming soon” or “Contact support” message instead of redirecting to checkout.

## References

- [Stripe Integration security guide](https://docs.stripe.com/security/guide)
- [Stripe API keys best practices](https://docs.stripe.com/keys-best-practices)
- Project: `StripeWebhookController` (signature verification), `StripeService` (server-side only), `SubscriptionController` (checkout session).
