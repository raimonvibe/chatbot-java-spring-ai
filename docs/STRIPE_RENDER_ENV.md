# Stripe env vars on Render (for “Subscribe” to work)

Set these in **Render → your Backend Service → Environment** so “Subscribe on a plan” works and the 500/405 errors go away.

## Required

| Variable | Description | Example |
|----------|-------------|--------|
| **STRIPE_SECRET_KEY** | Stripe secret key (Dashboard → Developers → API keys) | `sk_live_...` or `sk_test_...` |
| **STRIPE_PRICE_ID** | Default Stripe Price ID (one plan) | `price_xxxxxxxxxxxxx` |
| **STRIPE_SUCCESS_URL** | Where to send user after payment (use `/account` so they see “Share your chatbot” / embed script) | `https://www.prayer-chat.com/account?payment=success&session_id={CHECKOUT_SESSION_ID}` |
| **STRIPE_CANCEL_URL** | Where to send user if they cancel | `https://prayer-chat.com/pricing` |

Copy-paste one line (Render → Backend → Environment, Key / Value):

- **Key:** `STRIPE_SUCCESS_URL`  
- **Value:** `https://www.prayer-chat.com/account?payment=success&session_id={CHECKOUT_SESSION_ID}`

### How to set STRIPE_PRICE_ID

1. **Stripe Dashboard** → [Products](https://dashboard.stripe.com/products). Use **Test** or **Live** (top-right toggle) to match your `STRIPE_SECRET_KEY` (`sk_test_` = Test, `sk_live_` = Live).
2. Create a product (e.g. “Prayer-Chat Basic”) or open an existing one.
3. Add a **recurring** price (e.g. Monthly, amount in your currency) and save.
4. On the product page, under **Pricing**, open the price and copy the **Price ID** (starts with `price_`).
5. **Render** → your Backend service → **Environment** → Add variable: **Key** `STRIPE_PRICE_ID`, **Value** paste the ID (e.g. `price_1ABC123...`). Save and redeploy.

If you use per-plan prices, repeat for Basic/Pro/Enterprise and set `STRIPE_PRICE_ID_BASIC`, `STRIPE_PRICE_ID_PRO`, `STRIPE_PRICE_ID_ENTERPRISE`. Set `STRIPE_PRICE_ID` to your default (e.g. same as Basic).

### Is it safe for production?

- **STRIPE_PRICE_ID** (and other Price IDs) are **not secret**. They only identify which product/price to charge; they cannot be used to take payments by themselves. Storing them in env vars and in Render is standard and safe.
- **Never expose STRIPE_SECRET_KEY.** That key must stay only on the server (Render env). The frontend must never see or send it.
- For **production**: use **Live** mode in Stripe, a **Live** secret key (`sk_live_...`), and **Live** Price IDs (created with the Live toggle on). Use HTTPS for success/cancel URLs.

## Optional (per-plan prices)

If you have separate products/prices for Basic, Pro, Enterprise (e.g. in **Stripe Dashboard → Productcatalogus** / Product Catalog):

| Stripe product (catalog) | Env variable | Example price |
|--------------------------|--------------|---------------|
| Prayer-Chat Basic        | STRIPE_PRICE_ID_BASIC  | US$ 12/mo → copy its Price ID |
| Prayer-Chat Pro          | STRIPE_PRICE_ID_PRO    | US$ 29/mo → copy its Price ID |
| Prayer-Chat Enterprise   | STRIPE_PRICE_ID_ENTERPRISE | US$ 79/mo → copy its Price ID |

For each product: open it in Stripe → **Pricing** section → open the recurring price → copy the **Price ID** (`price_...`) into the matching env var on Render.

## Webhook (for subscription status updates)

| Variable | Description |
|----------|-------------|
| STRIPE_WEBHOOK_SECRET | From Stripe Dashboard → Webhooks → your endpoint → Signing secret |

## Stripe redirect security (Render)

Setting **STRIPE_SUCCESS_URL** to  
`https://www.prayer-chat.com/account?payment=success&session_id={CHECKOUT_SESSION_ID}`  
on Render is **secure**:

- **Server-side only** – Success and cancel URLs are read from environment variables only; the client cannot supply redirect URLs, so there is no open-redirect from checkout.
- **Startup validation** – When Stripe is configured, the backend checks that the host of `STRIPE_SUCCESS_URL` and `STRIPE_CANCEL_URL` is in the allowed list. If not, the app fails to start with an explicit error. The default allowed list includes `https://www.prayer-chat.com` and `https://prayer-chat.com`.
- **HTTPS** – Use `https://` in production so the redirect is encrypted.
- **Your domain only** – Never set `STRIPE_SUCCESS_URL` or `STRIPE_CANCEL_URL` to another site's domain. If you use a custom domain, add it to **STRIPE_ALLOWED_REDIRECT_ORIGINS** (comma-separated, e.g. `https://www.prayer-chat.com,https://prayer-chat.com`); otherwise leave it unset to use the default list.

## Notes

- **500** when clicking Subscribe: usually missing `STRIPE_SECRET_KEY` or wrong/missing Price ID.
- **405 Method Not Allowed**: the checkout endpoint is **POST only**. The frontend now sends the request with the auth token (JWT); make sure you’re logged in when you click Subscribe.
- Use your **production frontend URL** in `STRIPE_SUCCESS_URL` and `STRIPE_CANCEL_URL` (e.g. `https://prayer-chat.com/...`), not `http://localhost:3000`.

---

## 500 “Failed to create checkout session” – what to check

When the server returns **500** and the frontend shows “Failed to create checkout session”, the backend is calling Stripe but Stripe (or our code) is throwing. The app returns a generic message; the **real error is in the backend logs** (e.g. Render → your Backend Service → Logs). Check those first.

### 1. Environment variables (Render → Backend → Environment)

| Variable | What goes wrong |
|----------|------------------|
| **STRIPE_SECRET_KEY** | Missing, or typo (e.g. `STRIPE_API_KEY` instead of `STRIPE_SECRET_KEY`). Must be the **secret** key (`sk_live_...` or `sk_test_...`), not the publishable key. Key must belong to the same Stripe account as the Price IDs. |
| **STRIPE_PRICE_ID** | Missing or wrong. If you only use per-plan IDs (e.g. `STRIPE_PRICE_ID_BASIC`), the code still needs at least one price: set `STRIPE_PRICE_ID` to your default price (e.g. same as `STRIPE_PRICE_ID_BASIC`). |
| **STRIPE_SUCCESS_URL** | Must be a valid HTTPS URL (or HTTP only for localhost). Recommended: `/account?payment=success&session_id={CHECKOUT_SESSION_ID}` so users land on the page with the embed script. |
| **STRIPE_CANCEL_URL** | Same: valid URL back to your site (e.g. `https://prayer-chat.com/pricing`). |

If the secret key is missing or empty, the app usually returns **503** “Payment provider not configured”, not 500. So a **500** typically means the key is set but something else fails (e.g. Stripe API error).

### 2. Stripe Dashboard

- **API key**: In [Developers → API keys](https://dashboard.stripe.com/apikeys), use the **Secret key**. For production use **Live**; for testing use **Test** (and test Price IDs).
- **Price ID**: In [Products](https://dashboard.stripe.com/products), open the product → copy the **Price ID** (starts with `price_`). The price must be **recurring** (e.g. monthly). It must belong to the same Stripe account as the secret key.
- **Test vs Live**: Don’t mix: `sk_test_...` only works with test Price IDs; `sk_live_...` only with live Price IDs.

### 3. Typical Stripe errors (see backend logs)

- **Invalid API Key** / **No such API key**: Wrong key or wrong account.
- **No such price** (`resource_missing`): Almost always **Test vs Live mismatch**. Your `STRIPE_SECRET_KEY` and `STRIPE_PRICE_ID` must be from the same mode:
  - **Test**: Dashboard → toggle **Test** (top right) → [API keys](https://dashboard.stripe.com/test/apikeys) use `sk_test_...` → [Products](https://dashboard.stripe.com/test/products) → open product → copy the **Price ID** (that’s your test price). Set both on Render.
  - **Live**: Toggle **Live** → use `sk_live_...` and the **live** Price ID from Products. If you copied the price when in Test mode, Stripe won’t find it when using a Live key. Re-copy the Price ID with the correct mode selected.
- **No such customer** (`resource_missing`): The stored Stripe customer ID was created in a different mode (Test vs Live) or account. The app **auto-recovers**: it clears the invalid ID and creates a new customer. The `subscriptions.stripe_customer_id` column must allow NULL (so we can clear it). If you see **"null value in column stripe_customer_id violates not-null constraint"**, run this once on your Postgres DB (e.g. Render Shell or psql): `ALTER TABLE subscriptions ALTER COLUMN stripe_customer_id DROP NOT NULL;` then redeploy.
- **Invalid URL** (success_url/cancel_url): Stripe rejects the URL format or scheme.
- **Customer** / **subscription** errors: Less common on first “Create checkout”; if you see them, check DB and that the user exists.

### 4. After changing env vars on Render

Redeploy or restart the backend so it picks up the new values. Then try “Subscribe” again and, if it still returns 500, read the **latest backend log line** for the Stripe error.
