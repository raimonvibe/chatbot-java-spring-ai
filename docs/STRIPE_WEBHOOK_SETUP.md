# Stripe webhook setup for the chatbot app

This app needs its **own** Stripe webhook. If you already have webhooks for other apps (e.g. imageconverter), add a **new** destination for the chatbot backend.

## 1. Add a new webhook in Stripe

1. Open **Stripe Dashboard** → **Developers** → **Webhooks** (or **Gebeurtenisbestemmingen** in Dutch).
2. Click **"+ Add destination"** / **"+ Bestemming toevoegen"**.
3. Do **not** reuse the URL of another app (e.g. `imageconverter-backend.onrender.com`). Use your **chatbot** backend URL.

## 2. Endpoint URL

Use your chatbot backend base URL + path **`/stripe/webhook`**:

| Environment | Example URL |
|-------------|-------------|
| Production (Render) | `https://your-chatbot-backend.onrender.com/stripe/webhook` |
| Local | `https://your-ngrok-or-tunnel-url/stripe/webhook` |

Replace `your-chatbot-backend.onrender.com` with the real hostname of your deployed backend service.

## 3. Events to send

Subscribe to at least these events (the backend handles them):

| Event | Purpose |
|-------|---------|
| `customer.subscription.created` | New subscription → create/update local subscription |
| `customer.subscription.updated` | Plan change, renewal → sync status |
| `customer.subscription.deleted` | Cancellation → mark subscription inactive |
| `invoice.payment_succeeded` | Logged (subscription events cover most logic) |
| `invoice.payment_failed` | Triggers security alert |

In Stripe, under “Select events” or “Events to send”, add these. You can add more later if needed; unhandled types are logged and ignored.

## 4. Signing secret → environment variable

1. After saving the webhook, Stripe shows a **Signing secret** (starts with `whsec_`).
2. Copy it and set it in your **Render** (or other host) environment:
   - **Key:** `STRIPE_WEBHOOK_SECRET` (or the name your app uses, e.g. `stripe.webhook-secret`).
   - **Value:** the `whsec_...` string.
3. Redeploy the backend so it picks up the new variable.

Without the correct signing secret, the backend will reject webhook requests (invalid signature).

## 5. Optional: IP allowlist

If you use `stripe.webhook-ip-allowlist`, add Stripe’s webhook IPs (see Stripe docs). Leaving it unset disables IP checks; signature verification is still required.

## 6. Verify

- In Stripe → Webhooks → your chatbot endpoint, use “Send test webhook” or trigger a test subscription.
- Check backend logs for “Handled subscription.created event” (or similar) and no signature errors.

---

**Summary:** One webhook **destination** per app. This chatbot’s URL is `https://<your-chatbot-backend>/stripe/webhook`. Set `STRIPE_WEBHOOK_SECRET` (or equivalent) from the new destination’s signing secret.
