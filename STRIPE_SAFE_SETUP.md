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
| `STRIPE_PRICE_ID` | (Optional) Default price when no plan is sent (e.g. Pro) | Stripe Dashboard → Products → your product → Price ID |
| `STRIPE_PRICE_ID_BASIC` | (Production) Price for Basic plan | See [§2.1 What you need for production](#21-what-you-need-for-production) |
| `STRIPE_PRICE_ID_PRO` | (Production) Price for Pro plan | See [§2.1 What you need for production](#21-what-you-need-for-production) |
| `STRIPE_PRICE_ID_ENTERPRISE` | (Production) Price for Enterprise plan | See [§2.1 What you need for production](#21-what-you-need-for-production) |
| `STRIPE_SUCCESS_URL` | Where to redirect after payment | e.g. `https://yourdomain.com/dashboard?session_id={CHECKOUT_SESSION_ID}` |
| `STRIPE_CANCEL_URL` | Where to redirect if user cancels | e.g. `https://yourdomain.com/pricing` |

If `STRIPE_SECRET_KEY` is missing, the API returns **503 Payment provider not configured** instead of calling Stripe.

### 2.1 What you need for production (multi-plan: Basic / Pro / Enterprise)

For production with three tiers (Basic $12/mo, Pro $29/mo, Enterprise $79/mo), do the following.

**Step 1: Create three Products and Prices in Stripe Dashboard**

1. In [Stripe Dashboard](https://dashboard.stripe.com/) switch to **Live** mode (or Test for staging).
2. Go to **Product catalog** → **Products** → **Add product**.
3. Create each product and its recurring price:

   | Product name (example) | Price | Billing | After saving, copy |
   |------------------------|-------|---------|--------------------|
   | Prayer-Chat Basic | $12.00 USD | Monthly (recurring) | **Price ID** (starts with `price_`) |
   | Prayer-Chat Pro | $29.00 USD | Monthly (recurring) | **Price ID** |
   | Prayer-Chat Enterprise | $79.00 USD | Monthly (recurring) | **Price ID** |

   For each: set **Pricing model** to *Recurring*, **Billing period** to *Monthly*, **Price** and **Currency** as above, then **Save product**. Copy the **Price ID** from the product’s pricing section (e.g. `price_1ABC...`).

**Step 2: Set environment variables**

In your production environment (e.g. Render, or `.env` for production), set:

```bash
# Per-plan Price IDs (from Step 1)
STRIPE_PRICE_ID_BASIC=price_xxx    # replace with your Basic price ID
STRIPE_PRICE_ID_PRO=price_xxx      # replace with your Pro price ID
STRIPE_PRICE_ID_ENTERPRISE=price_xxx  # replace with your Enterprise price ID
```

**Step 3 (optional): Default when no plan is sent**

If the frontend or API sometimes calls create-checkout without a plan (e.g. “Subscribe” with no tier), the backend uses a single default price. Set that to your most common tier (e.g. Pro):

```bash
STRIPE_PRICE_ID=price_xxx   # e.g. same as STRIPE_PRICE_ID_PRO
```

If you don’t set `STRIPE_PRICE_ID`, and no plan/price is sent, the backend may fall back to an inline price (see `StripeService`); for a clear production setup, set `STRIPE_PRICE_ID` to one of your Price IDs (typically Pro).

**Step 4: How the backend uses these**

- **Checkout with plan:** Frontend sends `{ "plan": "BASIC" }`, `{ "plan": "PRO" }`, or `{ "plan": "ENTERPRISE" }` to `POST /api/subscription/create-checkout-session`. The backend maps those to `STRIPE_PRICE_ID_BASIC`, `STRIPE_PRICE_ID_PRO`, `STRIPE_PRICE_ID_ENTERPRISE` respectively.
- **Checkout with no plan:** If the body is `{}` or no plan, the backend uses `STRIPE_PRICE_ID` (your default).
- **Webhook:** When Stripe sends `customer.subscription.created` (etc.), the app maps the subscription’s price ID back to a plan (BASIC/PRO/ENTERPRISE) and stores it so limits and features match the tier.

## 3. Webhook security

- The app **always** verifies the `Stripe-Signature` header using `STRIPE_WEBHOOK_SECRET` before processing any event.
- Endpoint: `POST /stripe/webhook` (no auth; Stripe is not logged in – verification is by signature).
- In Stripe Dashboard, add a webhook endpoint with URL: `https://your-backend.com/stripe/webhook`.
- Subscribe to: `customer.subscription.created`, `customer.subscription.updated`, `customer.subscription.deleted`, `invoice.paid`, `invoice.payment_failed`.
- **Optional (defense-in-depth):** Set `STRIPE_WEBHOOK_IP_ALLOWLIST` to a comma-separated list of Stripe webhook IPs (see [Stripe’s list](https://stripe.com/files/ips/ips_webhooks.txt)). When set, only requests from those IPs are accepted.

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
