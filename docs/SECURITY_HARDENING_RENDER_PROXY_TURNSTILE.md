# Security hardening (Render proxy + Turnstile + abuse limits)

This document explains the **environment variables you set on Render** and the **security tightening** they enable:

- **Trusted-proxy forwarded-header hardening** (prevents spoofed `X-Forwarded-For` from bypassing IP limits)
- **Cloudflare Turnstile gate** on the **public embed widget** (cheap bot protection)
- **Extra abuse limits** that reduce multi-account abuse

---

## Why this exists (simple)

Your app uses **IP-based limits** (rate limits, per-IP caps). On the public internet, attackers can fake the `X-Forwarded-For` header unless you only trust it when it comes from your **actual proxy/load balancer**.

Because Render runs your service behind a proxy, we want:

- **Real users**: identified by their real IP from `X-Forwarded-For`
- **Attackers hitting you directly**: **cannot** spoof headers to bypass limits

---

## Render environment variables you set

### 1) Trusted proxy header hardening

Set on the **backend** service in Render:

- `APP_TRUST_FORWARDED_HEADERS=true`
- `APP_TRUSTED_PROXIES=10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,100.64.0.0/10`

**What they do**

- When `APP_TRUST_FORWARDED_HEADERS=true`, the backend will *consider* `X-Forwarded-For` / `X-Real-IP` **only if** the immediate sender (`remoteAddr`) matches one of the CIDR ranges in `APP_TRUSTED_PROXIES`.
- If the request does **not** come from a trusted proxy IP, the backend uses `remoteAddr` and **ignores** forwarded headers.

**Why it matters**

- Prevents “**rate limit bypass** by spoofing `X-Forwarded-For`”.
- Keeps your IP-based abuse protections meaningful in production.

**Where it applies**

This safe client-IP resolution is used in:

- `ChatController` (widget chat + preview chat endpoints)
- `RateLimitingFilter` (global request rate limiting)
- `AuditService` (audit log IP fields)

---

### 2) Turnstile (bot protection) variables

Only set these if/when you want Turnstile enabled:

- `APP_TURNSTILE_ENABLED=true`
- `APP_TURNSTILE_SITE_KEY=...` (safe to expose to browsers)
- `APP_TURNSTILE_SECRET_KEY=...` (**secret**, server-only)

**Current status**

- Turnstile code is present, but the feature is **disabled in code for now** (kept for future rollout).
- Keeping the env vars on Render is safe; they simply have **no effect** until the feature is re-enabled.

**What they do**

- When enabled, `POST /api/chat/embed/{embedCode}` requires a valid Turnstile token.
- The widget script (`/js/chatbot-widget.js`) auto-loads Turnstile and sends a token with each message.

**How verification works (server-side)**

- Server calls Cloudflare siteverify endpoint and checks `"success": true`.
- Tokens are short-lived and single-use; replay fails.

---

## What security tightening was implemented

### A) Spoof-proof IP detection (trusted proxies only)

Implemented in `ClientIpResolver`:

- **If not from trusted proxy** → ignore `X-Forwarded-For`, use `remoteAddr`
- **If from trusted proxy** → use first IP in `X-Forwarded-For`, else `X-Real-IP`, else `remoteAddr`

This aligns with Spring’s guidance: forwarded headers must only be trusted at the **trust boundary** and should otherwise be removed/ignored.

### B) Turnstile gate on public embed widget chat

When Turnstile is enabled:

- `GET /api/chat/embed/{embedCode}` returns:
  - `turnstileEnabled`
  - `turnstileSiteKey`
- The widget includes `turnstileToken` in `POST /api/chat/embed/{embedCode}`.
- The backend rejects missing/invalid tokens with **403** and an error code.

### C) IP-based abuse controls (multi-account hardening)

These are specifically to reduce “many accounts from one network” abuse:

- **Bucket4j throttling key changed to per-IP (global)** for widget traffic
  - Prevents “multiple chatbot IDs” from multiplying the per-IP allowance
- **Extra daily cap per IP** (free-product mode) using DB-backed counting by `conversation.userIp`

### D) Production deletion lock (cost/abuse protection)

Deletion endpoints are disabled when billing is enabled (`app.billing.enabled=true`):

- `DELETE /api/chatbots/{id}` → 403
- `DELETE /api/chatbots` → 403

UI also hides delete controls in production.
See `docs/DELETE_CHATBOT_PRODUCTION.md`.

---

## What to expect in production

### If you configured proxies correctly

- IP limits will apply to the **real visitor IP**
- Spoofed forwarded headers won’t bypass limits

### If you configured proxies incorrectly

- If `APP_TRUSTED_PROXIES` is wrong/empty, forwarded headers won’t be trusted:
  - Your app may treat many users as coming from the same IP (the proxy),
  - causing too-aggressive throttling.

---

## Quick rollout checklist (Render)

- **Required**
  - Set `APP_TRUST_FORWARDED_HEADERS=true`
  - Set `APP_TRUSTED_PROXIES=...` (private ranges listed above)

- **Optional**
  - Turnstile: set `APP_TURNSTILE_ENABLED=true` and keys

- **Validation**
  - Send a few widget messages and confirm you do *not* get unexpected 429/403.
  - If Turnstile is enabled and you see 403 “Bot verification required”, verify the widget is loading Turnstile and your keys match.

---

## References (why this approach is standard)

- Spring forwarded headers security note (trust boundary): `https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/filter/ForwardedHeaderFilter.html`
- OWASP “IP spoofing via HTTP headers”: `https://owasp.org/www-community/pages/attacks/ip_spoofing_via_http_headers`
- Cloudflare Turnstile server-side validation: `https://developers.cloudflare.com/turnstile/get-started/server-side-validation`

