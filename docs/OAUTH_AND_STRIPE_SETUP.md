# Google OAuth 2.0 & Stripe Setup Guide

Single reference for setting up Google OAuth 2.0 and Stripe subscriptions, plus troubleshooting and deployment notes.

## Table of Contents

1. [Google OAuth 2.0 Setup](#google-oauth-20-setup)
2. [OAuth on Render](#oauth-on-render)
3. [Why the backend URL appears in Google login](#why-the-backend-url-appears-in-google-login)
4. [Stripe Setup](#stripe-setup)
5. [Environment configuration](#environment-configuration)
6. [Testing](#testing)
7. [Production deployment](#production-deployment)
8. [Troubleshooting](#troubleshooting)
9. [Logout and consent screen](#logout-and-consent-screen)
10. [Security best practices](#security-best-practices)

---

## Google OAuth 2.0 Setup

### Step 1: Create a Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click "Select a project" → "New Project"
3. Enter project name (e.g. `Prayer-Chat Chatbot`) and click "Create"

### Step 2: Enable Google+ API

1. APIs & Services → Library
2. Search "Google+ API" and enable it

### Step 3: Configure OAuth Consent Screen

1. APIs & Services → OAuth consent screen
2. Select "External" (unless you use Google Workspace)
3. Fill in:
   - **App name**: `Prayer-Chat` (this is what users see)
   - **User support email** and **Developer contact**: your email
4. Scopes: add `userinfo.email`, `userinfo.profile`
5. Test users: add your Google email for development
6. Save and continue through the steps

### Step 4: Create OAuth 2.0 Credentials

1. APIs & Services → Credentials → Create Credentials → OAuth 2.0 Client ID
2. Application type: Web application
3. **Authorized JavaScript origins**:
   - Development: `http://localhost:8081`
   - Production: `https://your-backend.onrender.com` and/or your frontend domain
4. **Authorized redirect URIs**:
   - Development: `http://localhost:8081/login/oauth2/code/google`
   - Production: `https://your-backend.onrender.com/login/oauth2/code/google`
5. Create and copy **Client ID** and **Client Secret** for `.env`

---

## OAuth on Render

When deploying to Render, add your backend URL in Google Cloud Console:

1. Render Dashboard → your backend service → copy URL (e.g. `https://chatbot-backend-xxxx.onrender.com`)
2. Google Cloud Console → APIs & Services → Credentials → your OAuth 2.0 Client ID → Edit
3. **Authorized JavaScript origins**: add `https://your-backend-url.onrender.com`
4. **Authorized redirect URIs**: add `https://your-backend-url.onrender.com/login/oauth2/code/google`
5. Keep localhost entries for local development
6. Save; wait 1–2 minutes before testing

**Common errors:**

- `redirect_uri_mismatch`: URI in Google must match exactly (no trailing slash).
- `invalid_client`: set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in Render environment variables.
- `access_denied`: add your email as a test user if the app is in testing mode.

---

## Why the backend URL appears in Google login

This app uses **server-side OAuth**: the OAuth callback goes to the backend, so Google shows the backend URL in the consent screen. To show your frontend domain (e.g. prayer-chat.com) instead, you can:

1. **Hybrid OAuth flow (recommended):** Frontend starts OAuth with a redirect_uri pointing to the frontend (e.g. `https://prayer-chat.com/auth/callback`). After Google redirects back with a code, the frontend sends the code to the backend; the backend exchanges it for tokens and returns a JWT. Then in Google Cloud Console you only need the frontend origin and redirect URI (e.g. `https://prayer-chat.com/auth/callback`). See `docs/archive/oauth-stripe/HYBRID_OAUTH_SETUP.md` if that flow is implemented.
2. **Custom domain for backend:** Use a reverse proxy or Render custom domain so the backend is reached at e.g. `https://api.prayer-chat.com`, then use that in authorized origins and redirect URIs.
3. **Keep current setup:** Ensure the OAuth consent screen **App name** is "Prayer-Chat"; the URL will still be the backend domain.

---

## Stripe Setup

### Create account and get keys

1. [Stripe](https://stripe.com/) → sign up and verify
2. Dashboard → Developers → API keys: use **Publishable key** (frontend) and **Secret key** (backend); copy secret for `.env`

### Product and price

1. Products → Add product (e.g. "Prayer-Chat Pro Subscription")
2. Pricing: Recurring, set price and billing period, save
3. Copy the **Price ID** (`price_...`) for `.env`

### Webhook

1. Developers → Webhooks → Add endpoint
2. URL: production `https://yourdomain.com/stripe/webhook` (for local use Stripe CLI, see below)
3. Events: `customer.subscription.created`, `customer.subscription.updated`, `customer.subscription.deleted`, `invoice.payment_succeeded`, `invoice.payment_failed`
4. Copy the **Signing secret** (`whsec_...`) for `.env`

### Local webhook testing

```bash
stripe login
stripe listen --forward-to localhost:8081/stripe/webhook
```

Use the CLI’s webhook secret for `STRIPE_WEBHOOK_SECRET` in development.

---

## Environment configuration

1. `cp .env.example .env`
2. Set at least:

```bash
# Google OAuth
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...

# Stripe
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_PRICE_ID=price_...

STRIPE_SUCCESS_URL=http://localhost:3000/dashboard?session_id={CHECKOUT_SESSION_ID}
STRIPE_CANCEL_URL=http://localhost:3000/pricing
```

See also **STRIPE_SAFE_SETUP.md** and **RENDER_ENV_VARIABLES.md** for production.

---

## Testing

- **OAuth:** Start backend, open `http://localhost:8081/oauth2/authorization/google`, complete flow.
- **Stripe:** Use test cards (e.g. `4242 4242 4242 4242`). Create checkout session and check subscription status via API; monitor webhooks in Dashboard or via `stripe listen`.

---

## Production deployment

- Use Stripe **live** keys and live Price IDs; add production webhook endpoint.
- **Multi-plan setup:** For Basic / Pro / Enterprise ($12 / $29 / $79 per month), create three Products/Prices in Stripe and set `STRIPE_PRICE_ID_BASIC`, `STRIPE_PRICE_ID_PRO`, `STRIPE_PRICE_ID_ENTERPRISE`. Optionally set `STRIPE_PRICE_ID` as the default (e.g. Pro) when no plan is sent. Full steps: **[STRIPE_SAFE_SETUP.md](../STRIPE_SAFE_SETUP.md) §2.1 What you need for production**.
- Add production OAuth redirect URIs and origins in Google Cloud Console.
- Set `CORS_ALLOWED_ORIGINS` and ensure no test keys in production env.

---

## Troubleshooting

### OAuth

| Error | Check |
|-------|--------|
| `redirect_uri_mismatch` | Redirect URI in Google exactly matches app (e.g. `http://localhost:8081/login/oauth2/code/google`). No trailing slash. |
| `invalid_client` | `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in `.env`/Render; no quotes or extra spaces. |
| `access_denied` | OAuth consent screen: add test users (dev) or publish app. |
| `authorization_request_not_found` (local) | Session storage: use file-based H2 or ensure Spring Session JDBC tables exist; check `JSESSIONID` cookie. |

### Stripe

| Issue | Check |
|-------|--------|
| Webhook signature failed | `STRIPE_WEBHOOK_SECRET` matches endpoint signing secret (or CLI secret when using `stripe listen`). |
| "No such price" | `STRIPE_PRICE_ID` correct and from same mode (test/live). |
| Subscription not updating | Webhook endpoint reachable; events listed in Dashboard. |

---

## Logout and consent screen

- **Logout:** Use the app’s Logout button; it invalidates the session and can redirect to Google logout so the next login shows the consent screen again.
- **Full reset:** Clear site data for the app and backend domains, or use an incognito window.
- **App name in consent screen:** Google Cloud Console → APIs & Services → OAuth consent screen → Edit app → set **App name** to e.g. `Prayer-Chat`. Changes can take a few minutes to appear.

---

## Security best practices

- Never commit `.env`; keep Stripe secret and Google client secret server-side only.
- Use a strong JWT secret (e.g. 32+ random characters).
- Use HTTPS in production.
- Rotate keys if compromised; see **API_KEY_ROTATION.md** and **INCIDENT_RESPONSE.md**.

---

*Consolidated from OAUTH_STRIPE_SETUP, OAUTH_RENDER_SETUP, OAUTH_DOMAIN_EXPLANATION, OAUTH2_TROUBLESHOOTING, OAUTH2_TROUBLESHOOTING_LOCAL, GOOGLE_OAUTH_LOGOUT_GUIDE, and related docs.*
