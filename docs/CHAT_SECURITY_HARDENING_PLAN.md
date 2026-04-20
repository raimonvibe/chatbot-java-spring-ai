# Chat & embed abuse mitigation plan (without Turnstile)

This document turns the informal hardening ideas into a **phased plan**: what to do, why it matters, whether you need external accounts, and how **Redis** fits in.

---

## Do you need new accounts? Is Redis “not free”?

| Piece | New account / vendor? | Cost picture |
|--------|------------------------|--------------|
| **Lock down `POST /api/chat/{chatbotId}`** | No — application and deployment only. | $0 (engineering time). |
| **Per–embed-code rate limits** | No — can live in memory first; Redis optional. | $0 if in-memory; see Redis row. |
| **Persistent / shared rate limits (Redis)** | **Only if** you use a **hosted** Redis (Upstash, Redis Cloud, AWS ElastiCache, Render Redis, etc.). | **Redis the software is open source and free.** Self-host on your own VM/Docker: no Redis license fee (you already pay for the server). Many hosts offer a **small free tier** for managed Redis; production scale usually paid. |
| **Stronger per-IP limits when billing is on** | No — config/code only. | $0. |
| **Domain allowlist for embed** | No — store domains in DB with chatbot. | $0. |
| **Orphan / no-owner chatbots** | No — policy + code. | $0. |
| **WAF / CDN rate rules** | **Only if** you use that provider (e.g. Cloudflare, AWS). | Cloudflare has a **free** tier suitable for basic rules; AWS/other clouds are usage-based. |

**Summary:** You do **not** need to sign up anywhere to implement the **pure application** changes. You need an account **only** if you **choose** a specific hosted Redis or a specific CDN/WAF vendor. Redis itself is not “paid software”; **managed Redis** or **extra servers** may cost money.

---

## Goals

1. Stop **anonymous chat by guessed numeric `chatbotId`** where possible.
2. Make **rate limits** honest under **restarts** and **multiple backend instances**.
3. Spread limits so one actor cannot trivially burn **one owner’s** AI quota.
4. Add **optional** embed restrictions (domains) for browser-like traffic.
5. Avoid depending on **Turnstile** for the first line of defense.

---

## Current limits: is “30 per hour” logical with “30 per day”?

The backend combines **several** independent caps (see `ChatController`, `BillingModeService`, `RateLimitingService`). On the **free product** path (billing disabled), the important ones include:

| Limit | Scope | Typical value (code today) |
|--------|--------|----------------------------|
| **Owner daily message quota** | All chat traffic counted against the **chatbot owner’s** account for today | **30 / day** (`FREE_PRODUCT_MESSAGES_PER_DAY`) |
| **Per end-user IP, per day** | Extra guardrail when billing is off | **60 / day / IP** (`FREE_PRODUCT_MESSAGES_PER_IP_PER_DAY`) — *not* 30 |
| **Per end-user IP, per hour (burst)** | In-memory bucket on chat endpoints (shared key `embed:` + client IP for the paths that use it) | **30 / hour / IP** (`EMBED_CHAT_PER_IP_PER_CHATBOT_LIMIT`) |

**Do 30/hour and 30/day “work together”?**

- **They do not contradict each other.** The **owner** stops accepting new AI replies after **30 messages total today** (for that owner’s plan mode). The **hourly** cap only says: from **one IP**, you cannot send **more than 30 chat requests in a rolling hour** on the routes that use that bucket.
- **With a 30/day owner cap**, one very active visitor can still burn the **entire day’s allowance in a few minutes** (up to 30 sends), as long as they stay under **30/hour** — so for that scenario, **30/hour does not spread abuse over the day**; the **owner daily quota** is what actually protects your AI bill.
- **Where 30/hour still helps:** If the owner’s plan allowed **more** messages per day (e.g. paid tier), the hourly cap **limits burst** from a **single IP** so one client cannot fire hundreds of messages in a few minutes; the **daily** cap then bounds total cost.

**If you want stricter “spread” for a 30/day owner tier:** use a **lower** hourly cap (e.g. **10/hour**) so one IP needs **several hours** to consume 30 messages — at the cost of frustrating a legitimate power user on the same network.

**Takeaway:** **30/hour + 30/day is logically consistent** and easy to explain (“up to 30 messages an hour from one connection, and your plan’s daily total still applies”). It is **not** redundant in all plan modes, but for **strictly 30/day owners**, the **daily owner limit** is the binding cost control; the hourly limit is mainly **burst control** and aligns with “30 in one sitting” for one IP.

---

## What users see visually (today vs after hardening)

This section describes **UX**, not exact copy — wording can be improved in product work.

### A. Visitor on a website (embedded `chatbot-widget.js`)

**Today**

- **Normal chat:** User messages appear on the right (styled bubble); bot replies on the left; typing indicator while waiting.
- **When the API returns an error JSON** (including **429 Too Many Messages**, **403** inactive bot, **503** AI misconfiguration): the widget still parses JSON and, if the body contains an `error` field, shows a **generic bot bubble**: *“Sorry, I encountered an error. Please try again.”* — it does **not** show the server’s specific text (e.g. “Too many messages. Please try again later.”) unless you change the widget.
- **Network failure** (no response / not JSON): *“Sorry, I'm having trouble connecting. Please try again later.”*
- **Quota / rate-limit nuance:** The **visitor** does not see “you have used 27/30 messages”; they only see the generic error once the server refuses the request.

**After typical hardening (no UI polish)**

- **Numeric-ID path removed for anonymous callers:** No change for **embed** visitors. Anyone who was abusing `POST /api/chat/{id}` from a script would get **401/403** instead of replies — **no widget involved**.
- **Stricter or Redis-backed limits:** Same **visual** behavior as today unless you **update the widget** to map `429` (or `error` codes) to clearer copy (“Too many messages this hour — try again later.”).
- **Domain allowlist:** Browser requests from a **non-allowed site** might get **403**; the widget would still show the **same generic error** unless you add a dedicated message for that case.
- **Turnstile (if ever enabled):** Invisible or low-friction challenge; on failure, same pattern — generic error unless the UI is extended.

### B. Logged-in owner (dashboard / preview)

**Today**

- **In-app preview** may use the **numeric chatbot ID** path or embed flow depending on implementation; if that path is **restricted to authenticated users**, preview keeps working **with a session**, while **anonymous** abuse of the same URL stops.
- **When the owner hits daily message quota:** API returns **429** with plan-oriented messaging; the **dashboard UI** should surface that (exact styling depends on `frontend` chat components — often a toast, inline error, or disabled send).

**After hardening**

- **Preview** might require **login** or a **signed preview token** if you remove public numeric-ID chat — visually: **logged-out** users simply cannot open preview until they sign in (or you show an explicit “Sign in to preview” state).
- **Redis / new limits:** Same as visitors — until the **frontend** maps errors, users may still see **generic** failures; plan for **copy** that distinguishes “hourly limit”, “daily plan limit”, and “domain not allowed”.

### C. Summary table (visibility)

| Who | Sees limits coming? | What they actually see today (embed) |
|-----|---------------------|--------------------------------------|
| Site visitor | No meter/counter | Generic “Sorry, I encountered an error…” when blocked |
| Owner in app | Partially (if UI shows plan/upgrade) | Depends on dashboard implementation for preview |
| Script / attacker | N/A | HTTP status + JSON `error` (and optional `limit` / `current` on 429) |

**Product follow-up (optional):** Improve `chatbot-widget.js` to use `response.status` and `data.error` / `data.limit` so **429** shows friendly, specific text — documented here as a **visual/UX improvement**, not yet required for backend hardening.

---

## Implementation phases — how each phase looks in the UI

Below: **what changes on screen** for each audience when you ship the technical phases (1 → 4). Same behavior in **local dev** as in production unless you use different env limits — the *screens* are identical.

**Audiences**

| Audience | Where they are |
|----------|----------------|
| **Site visitor** | Customer’s website; floating **Prayer-Chat widget** (`chatbot-widget.js`). |
| **Owner** | **Dashboard** (`/dashboard`), **Preview** (`/chatbot/[id]`), **Account** / embed snippet. |
| **Anonymous / attacker** | Browser devtools, scripts, Postman — **no branded UI**, only HTTP errors. |

---

### Phase 1.1 — Restrict public `POST /api/chat/{chatbotId}`

| Audience | What they see |
|----------|----------------|
| **Site visitor** | **No change.** They use **embed** + `embedCode`; widget layout, colors, and chat bubbles stay the same. |
| **Owner** | **Preview still works** while **logged in**, because the app can keep calling chat with **JWT** (see `frontend/app/chatbot/[id]/page.tsx` → `sendMessage(chatbotId, …)`). Preview chat shows the same **assistant bubbles** as today; on errors, today’s pattern is an assistant bubble like *“Sorry, I encountered an error: …”* with text from `getUserFacingFetchError`. |
| **Anonymous** | Direct numeric-ID chat **stops working**: **401/403** from API — no Prayer-Chat page, only whatever their client shows. |

**Net UI delta:** Nothing new to design for the widget; **ensure preview never relies on unauthenticated numeric chat** (already typical if JWT is sent).

---

### Phase 1.2 — Per–embed-code rate limits (in addition to IP)

| Audience | What they see |
|----------|----------------|
| **Site visitor** | **Same widget chrome** (button, panel, bubbles). If limited: **same as today** unless you improve copy — generic *“Sorry, I encountered an error…”* in the bot bubble, or with a **UI polish** task: clearer text, e.g. *“This chat is busy right now. Try again in a little while.”* |
| **Owner** | No new **settings screen** required for v1. If **they** test embed on their own site from one IP, they could hit limits like anyone else. |
| **Anonymous** | Stricter rejection counts in logs; **no UI**. |

---

### Phase 1.3 — Per-IP daily cap when billing is on

| Audience | What they see |
|----------|----------------|
| **Site visitor** | Same widget; **429** → same generic bot bubble (or improved message if you ship widget copy changes). Visitors still **do not** see a numeric “X / Y messages left” meter unless you add one. |
| **Owner** | **Dashboard / preview** unchanged visually; **paid** tenants may see **rate errors more often** for heavy traffic from one IP — still surfaced as **chat error bubbles** or toasts depending on screen. |
| **Anonymous** | Same as today: JSON + status. |

---

### Phase 1.4 — Orphan / no-owner chatbots policy

| Audience | What they see |
|----------|----------------|
| **Site visitor** | If embed is **disabled** for orphan bots: widget may **fail to load** config or show **inactive / not found** style errors (depending on implementation). Visually: **empty panel**, **error strip**, or generic apology — align with how you treat **inactive** bots today. |
| **Owner** | N/A for orphans without an account; **real customers** always have an owner — **no change** for normal flows. |
| **Admin / support** | May need internal docs: “bot must have owner for embed.” |

---

### Phase 2 — Redis (or shared store) for rate limits

| Audience | What they see |
|----------|----------------|
| **Everyone** | **Almost no visual change.** Limits behave more **predictably** across **multiple backend instances** and **fewer limit resets** on deploy. The **UI components are the same**; only **when** a user hits a cap may shift slightly (stricter in multi-instance setups). |

**Net:** This phase is **infrastructure**; design system and layouts **unchanged**.

---

### Phase 3 — Embed domain allowlist

| Audience | What they see |
|----------|----------------|
| **Site visitor** | On **allowed** domains: **unchanged** happy path. On **non-allowed** domain: **403** → widget shows **generic error** (today) or, with product work, a **specific line**: *“This chat is not enabled for this website.”* |
| **Owner** | **New or extended settings** in dashboard (recommended): inputs for **allowed site URLs / domains**, helper text (“Add the site where you pasted the embed”), **Save**. Optional: **test embed** link that opens allowed origin. This is the **largest visible product change** in this plan. |
| **Anonymous** | 403 + JSON; no UI. |

---

### Phase 4 — WAF / CDN edge rules

| Audience | What they see |
|----------|----------------|
| **Site visitor** | If the edge **blocks** before the API: browser may show **failed fetch** — widget falls through to *“trouble connecting”* or a **blank** assistant response path depending on CORS/body. Often **indistinguishable** from a network blip unless you add **edge custom error pages** (optional, outside this app). |
| **Owner** | Usually **no in-app change**; operations tune Cloudflare / WAF. |
| **Anonymous** | Blocked at edge — may get **non-JSON** response; scripts break earlier. |

---

### Phase roll-out — quick UI checklist

| Phase | New screens / components? | Visitor widget | Owner dashboard |
|-------|---------------------------|----------------|-----------------|
| 1.1 | No | Same | Preview OK if authenticated |
| 1.2 | No (optional copy tweak) | Same + maybe clearer errors | Same |
| 1.3 | No (optional copy tweak) | Same | Same |
| 1.4 | No | Possible stricter error for bad bots | Same for normal owners |
| 2 | No | Same | Same |
| 3 | **Yes** — domain list UI | Same or clearer “wrong site” error | **Settings form** |
| 4 | No (optional edge pages) | Rare “can’t connect” | Same |

---

## Phase 1 — Application-only (no Redis, no new vendors)

### 1.1 Restrict public chat to the embed path

**Today:** `POST /api/chat/{chatbotId}` is allowed without authentication for anyone who can reach the API.

**Change:** Require **authentication** for the numeric-ID chat endpoint **or** remove anonymous use and route **in-app preview** through an authenticated or dedicated internal route.

**Why:** Embed codes are long and random; numeric IDs are often **sequential and enumerable**. Closing this hole is one of the **highest-impact, smallest conceptual** changes.

**Accounts:** None.

#### How authentication works for Phase 1.1 (this codebase)

Phase 1.1 splits **two** public chat entry points:

| Endpoint | Who uses it | Authentication model |
|----------|-------------|-------------------------|
| `POST /api/chat/embed/{embedCode}` | Website visitors, `chatbot-widget.js` | **Stays public** (no JWT). Identification is the **opaque `embedCode`** in the URL + existing rate limits. |
| `POST /api/chat/{chatbotId}` | Dashboard **Preview** (`sendMessage` in `frontend/lib/api.ts`) | **Must require a logged-in user** — in practice the same **JWT** the app already uses after Google OAuth. |

**End-user journey (preview)**

1. User signs in with **Google** on the Next.js app; the backend issues a **JWT** stored in the browser (e.g. `localStorage` / `authToken`).
2. `sendMessage()` already calls `getAuthHeaders()`, which adds **`Authorization: Bearer <jwt>`** to the request to `/api/chat/{chatbotId}`.
3. After Phase 1.1, **Spring Security** must **reject** requests to that URL **without** a valid JWT (**401 Unauthorized**), while **`/api/chat/embed/**`** remains **`permitAll`**.

**Backend changes (conceptual)**

1. **`SecurityConfig`** — Today `.requestMatchers("/api/chat/**").permitAll()` opens everything. Replace with something equivalent to: **`/api/chat/embed/**` → `permitAll`**, then **`/api/chat/**` → `authenticated()`** (order: more specific `/embed/**` rule **first**).
2. **`JwtAuthenticationFilter`** — Today any URI starting with `/api/chat/` is treated as “permit all without JWT”. Narrow that to **`/api/chat/embed/`** only (plus health/auth as today), so missing JWT on **`/api/chat/123`** is not forced to anonymous; Security can return **401**.
3. **Defense in depth (recommended next step)** — In `ChatController.sendMessage`, optionally verify the JWT user **owns** the `chatbotId` (or is admin). Otherwise any logged-in user could chat with **another** user’s bot ID if they guess the ID.

**What does *not* use JWT**

- The **embed widget** never sends the owner’s JWT (and must not — it would leak if pasted on third-party sites). It only calls **`/api/chat/embed/{embedCode}`**, which stays anonymous + embed-code-scoped.

**Tests / tools**

- Integration tests that `POST /api/chat/{id}` **without** a Bearer token must expect **401** after this change, or use **`@WithMockUser`** / a real JWT.

---

### 1.2 Per–embed-code rate limit (in-memory first)

**Today:** Part of the limiter uses a key derived from **client IP** in a way that does not isolate abuse **per bot** as clearly as a per–embed-code cap.

**Change:** Add something like **N messages per hour per `embedCode`** (and optionally keep per-IP limits). Can still use the existing in-process bucket approach **initially**.

**Why:** Stops a single IP from focusing fire on **one** bot without affecting your global IP bucket story; clearer fairness per customer widget.

**Accounts:** None.  
**Caveat:** In-memory limits still **reset on deploy** and do **not** sync across instances (see Phase 2).

---

### 1.3 Per-IP daily cap when billing is enabled

**Today:** `BillingModeService.effectiveMessagesPerIpDay()` effectively disables the extra IP-day cap when Stripe/billing is on (`Integer.MAX_VALUE`).

**Change:** Introduce a **finite** per-IP-per-day ceiling even when billing is on (tunable env var, e.g. high default).

**Why:** Reduces **distributed** abuse (many IPs still hit **owner** quota, but IP-day cap adds another layer and protects shared infrastructure).

**Accounts:** None.

---

### 1.4 Policy for chatbots without an owner

**Today:** Owner-based daily quotas may not apply when `owner` is null.

**Change:** Decide product rule: e.g. **no embed chat** for bots without an owner, or a **very low** global limit for those bots.

**Why:** Closes a gap where automation could target **orphan** bots.

**Accounts:** None.

---

## Phase 2 — Persistent / distributed rate limiting (Redis optional)

### 2.1 Move hot rate limits to a shared store

**Change:** Back the **embed** and **IP** (and per–embed-code) counters with **Redis** (or another shared store your team already runs).

**Why:** In-memory `ConcurrentHashMap` buckets:

- reset on **process restart**;
- do **not** aggregate across **horizontal scaling** (each JVM has its own counters).

**Redis options:**

| Approach | Account? | Notes |
|----------|----------|--------|
| **Docker Redis next to backend** (compose / single VM) | No extra vendor if it’s your server. | Simplest for self-hosted. |
| **Upstash / Redis Cloud / Render Redis / ElastiCache** | Yes, for that provider. | Often a **free tier** for small usage; pay as you grow. |

**Accounts:** Only if you pick **hosted** Redis. Self-hosted = no new SaaS account.

**Self-hosted Redis in Docker — what “no vendor” really means**

- **Yes:** Redis is **ordinary software**; running `redis` in **Docker** or **docker-compose** on a server you control is common and **does not require** a Redis Cloud / Upstash **account**.
- **Not “zero setup”:** You still do **operational** work: add the service to compose (image, port, volume if you want persistence), put the **host/port (and optional password)** in **Spring** or your rate-limiter config, and ensure the **backend can reach** the container on the network. For **rate-limit counters only**, losing Redis on restart is often acceptable (limits reset), so persistence can stay minimal.
- **Production:** Consider a password, firewall (only app subnet talks to Redis), and monitoring — same as any internal dependency.

---

### 2.2 Align Spring / Bucket4j with Redis (if you use Bucket4j)

**Change:** Use a **distributed** bucket configuration (e.g. Bucket4j Redis proxy) or a small custom counter service.

**Why:** Same rules as today, but **consistent** across replicas.

---

## Phase 3 — Embed domain allowlist (defense in depth)

### 3.1 Store allowed origins / hostnames per chatbot

**Change:** Optional field (e.g. list of hosts or full origins). On `POST /api/chat/embed/{embedCode}`, validate `Origin` or `Referer` when present.

**Why:** Reduces casual **hotlinking** and drive-by use of the snippet on random sites. **Not** a cryptographic guarantee: non-browser clients can omit or forge headers, so pair with **rate limits**.

**Accounts:** None (data in your DB).

---

## Phase 4 — Edge / operations (optional)

### 4.1 WAF / CDN rules

**Change:** Rate-limit or challenge **`/api/chat/**`** at the edge; block obvious bad ASNs; ensure TLS.

**Why:** Absorbs junk before it hits the JVM.

**Accounts:** Only for the provider you use (e.g. **Cloudflare** free tier is enough for many teams).

---

## Suggested order of work

1. **Phase 1.1** — Restrict numeric-ID public chat (biggest abuse surface).  
2. **Phase 1.3** + **1.4** — IP cap when billing on + orphan policy.  
3. **Phase 1.2** — Per–embed-code limits (in-memory).  
4. **Phase 2** — Redis (or existing shared cache) when you run **more than one** backend instance or care about **restart** consistency.  
5. **Phase 3** — Domain allowlist when product/UI is ready.  
6. **Phase 4** — Edge rules if you already use a CDN/WAF.

---

## Out of scope for this plan (but related)

- **Turnstile** — documented elsewhere; remains optional bot friction.  
- **Prompt injection / content safety** — separate from infrastructure abuse; handled by prompts, moderation, or model policies.  
- **Secrets rotation** — general ops; not specific to this plan.

---

## References in this repo

- Public chat and limits: `ChatController.java`  
- Billing vs free-product caps: `BillingModeService.java`, `RateLimitingService.java`  
- Security matcher for `/api/chat/**`: `SecurityConfig.java`  
- Turnstile (currently disabled in service): `TurnstileService.java`, `docs/SECURITY_HARDENING_RENDER_PROXY_TURNSTILE.md`
