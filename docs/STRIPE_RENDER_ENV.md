# Stripe env vars on Render (for “Subscribe” to work)

Set these in **Render → your Backend Service → Environment** so “Subscribe on a plan” works and the 500/405 errors go away.

## Required

| Variable | Description | Example |
|----------|-------------|--------|
| **STRIPE_SECRET_KEY** | Stripe secret key (Dashboard → Developers → API keys) | `sk_live_...` or `sk_test_...` |
| **STRIPE_PRICE_ID** | Default Stripe Price ID (one plan) | `price_xxxxxxxxxxxxx` |
| **STRIPE_SUCCESS_URL** | Where to send user after payment | `https://prayer-chat.com/dashboard?session_id={CHECKOUT_SESSION_ID}` |
| **STRIPE_CANCEL_URL** | Where to send user if they cancel | `https://prayer-chat.com/pricing` |

## Optional (per-plan prices)

If you have separate products/prices for Basic, Pro, Enterprise:

| Variable | Example |
|----------|--------|
| STRIPE_PRICE_ID_BASIC | `price_xxx` |
| STRIPE_PRICE_ID_PRO | `price_yyy` |
| STRIPE_PRICE_ID_ENTERPRISE | `price_zzz` |

## Webhook (for subscription status updates)

| Variable | Description |
|----------|-------------|
| STRIPE_WEBHOOK_SECRET | From Stripe Dashboard → Webhooks → your endpoint → Signing secret |

## Notes

- **500** when clicking Subscribe: usually missing `STRIPE_SECRET_KEY` or wrong/missing Price ID.
- **405 Method Not Allowed**: the checkout endpoint is **POST only**. The frontend now sends the request with the auth token (JWT); make sure you’re logged in when you click Subscribe.
- Use your **production frontend URL** in `STRIPE_SUCCESS_URL` and `STRIPE_CANCEL_URL` (e.g. `https://prayer-chat.com/...`), not `http://localhost:3000`.
