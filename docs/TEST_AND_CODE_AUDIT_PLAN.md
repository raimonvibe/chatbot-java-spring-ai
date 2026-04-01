# Test-and-Code Audit Plan

## Why this approach is necessary

**Tests that only “match the code” are not enough for best results.** If we only add or adjust tests so they pass against the current implementation, we can end up with:

- Tests that green-light unsafe or inconsistent behaviour
- Edge cases and security gaps that the code never handles
- Logic bugs (e.g. priceId vs plan mismatch, NPEs on empty lists) that tests never challenge

**The right approach is two-way:**

1. **Analyse the code** – Read the real behaviour (validation, null-safety, security, consistency).
2. **Improve the code** – Fix gaps, add guards, enforce invariants.
3. **Align tests** – Add or update tests so they assert the intended behaviour and catch regressions.

This “analyse tests and code, then make necessary code changes” style is necessary for a reliable and secure app. This document is a **plan to double-check tests and code across the entire application** and to apply that style everywhere. Implement the audit in phases when you have time.

---

## Principles

- **Tests should encode desired behaviour**, not just mirror current code.
- **Code should be reviewed** for: validation, null/empty handling, security (allowlists, auth), and consistency (e.g. DB vs external system).
- **When you find a gap:** fix the code first, then add or adjust tests, then document if needed.

---

## Audit plan (by area)

Use this as a checklist. For each area: (1) read code and tests, (2) list gaps, (3) fix code, (4) update/add tests, (5) tick off when done.

### 1. Subscription & Stripe (done as reference)

- [x] **Checkout session** – Controller rejects disallowed `priceId`; service uses allowlist and idempotency key; `client_reference_id` set.
- [x] **Plan vs priceId** – `changeSubscriptionPlan` rejects FREE and enforces `planFromPriceId(priceId) == plan`; controller rejects FREE and validates `priceId` for change/upgrade/downgrade.
- [x] **Stripe payloads** – `handleSubscriptionCreated` and upgrade/downgrade guard against empty `items.getData()`; no NPE.
- [x] **Portal returnUrl** – Validated (allowlist, no `javascript:`); tests for disallowed origin and `javascript:`.
- [x] **Tests** – Disallowed priceId, plan FREE, GET 405, auth 401, invalid plan/priceId, portal returnUrl.

*Use this as the template for depth and two-way (code + test) changes.*

---

### 2. Authentication & auth-dependent endpoints

- [ ] **JWT / OAuth** – Review all endpoints that use `@AuthenticationPrincipal` or JWT: ensure 401 when unauthenticated and no leaking of other users’ data.
- [ ] **User identity** – Every mutation (create/update/delete) must use the authenticated user’s ID (or equivalent), never a user ID from the request body/path without ownership check.
- [ ] **Tests** – For each protected endpoint: unauthenticated request → 401; wrong user / invalid ID → 403 or 400 as appropriate; no success with another user’s ID.

---

### 3. Chatbot CRUD & usage

- [ ] **Create/update chatbot** – Validate URL, name, description; max lengths; sanitisation if rendered; ownership (user can only change their own).
- [ ] **List/get chatbots** – Only return chatbots for the current user (or shared with them if you have sharing).
- [ ] **Delete** – Only allow delete for owner; confirm no orphaned data or broken references.
- [ ] **Edge cases** – Null/empty required fields; oversized input; invalid URLs; XSS in name/description.
- [ ] **Tests** – Validation failures (400); auth (401/403); IDOR (access to other user’s bot); null/empty and oversized input.

---

### 4. Chat / messages / conversations

- [ ] **Send message** – Validate chatbot belongs to user (or is shared); rate limiting or abuse protection if present; max message length.
- [ ] **List messages/conversations** – Scope by user/chatbot; no cross-user leakage.
- [ ] **Streaming / long responses** – Timeouts, error handling, no sensitive data in logs.
- [ ] **Tests** – Unauthorised access to another user’s chat; invalid chatbot ID; oversized message; error paths.

---

### 5. Website analysis / crawling

- [ ] **URL validation** – Allowlist of schemes (e.g. https); block internal/private IPs and `file://`, `javascript:`; max URL length.
- [ ] **Content handling** – Safe parsing; size limits; no RCE via injected content; timeouts for fetches.
- [ ] **Storage** – No storing raw HTML/scripts unsanitised if ever replayed or displayed.
- [ ] **Tests** – Invalid or dangerous URLs rejected; timeouts and size limits; no NPE on empty or malformed responses.

---

### 6. Bible / embeddings / data loading

- [ ] **Initialisers** – `BibleDataInitializer`, embedding import: transactions, correct table names, graceful handling of “table does not exist” on first deploy.
- [ ] **Queries** – No unbounded result sets; pagination or limits where relevant.
- [ ] **File/URL import** – Validate paths/URLs; size and retry limits; no path traversal.
- [ ] **Tests** – Empty/missing data; duplicate run; invalid file/URL; transaction behaviour.

---

### 7. Webhooks (Stripe, others)

- [ ] **Signature verification** – Every webhook handler must verify signature before processing; reject invalid/missing signature.
- [ ] **Idempotency** – Duplicate events must not double-apply (e.g. double grant subscription).
- [ ] **Payload** – Validate structure; guard against null/empty nested fields (e.g. `items.getData().get(0)`).
- [ ] **Tests** – Invalid signature → 400/401 and handler not called; duplicate event → no double effect; malformed payload → safe handling, no NPE.

---

### 8. API surface & errors

- [ ] **Error responses** – No stack traces or internal details in production; consistent structure (e.g. `{ "error": "..." }`); appropriate status codes.
- [ ] **Logging** – No secrets or PII in logs; use sanitisation (e.g. `LogSanitizer`) where needed.
- [ ] **CORS** – Correct origins; no wildcards in production unless intentional and documented.
- [ ] **Tests** – Unauthenticated and unauthorised calls; invalid input; verify error shape and status.

---

### 9. Frontend (if in scope)

- [ ] **API calls** – All authenticated calls send token (e.g. JWT); correct base URL for env; no secrets in client code.
- [ ] **User input** – Validation and length limits before submit; no raw HTML injection.
- [ ] **Redirects / links** – Success/cancel URLs for Stripe (or similar) come from env or allowlist, not user input.
- [ ] **Tests** – E2E or integration: login → protected action → success; no token → 401; invalid payload → error handling.

---

## How to run each audit step

1. **List all relevant files** – Controllers, services, repos, and their tests.
2. **Read the code** – Don’t assume; trace validation, null checks, and security (auth, allowlists).
3. **List gaps** – Missing validation, NPE risks, auth/IDOR, inconsistent state, missing tests.
4. **Fix code first** – Add validation, guards, and consistent behaviour.
5. **Update tests** – New cases for new behaviour; fix or remove tests that assumed wrong behaviour.
6. **Document** – Short note in this file or in a dedicated security/design doc (e.g. `STRIPE_CHECKOUT_SECURITY.md`).

---

## Outcome

After applying this plan across the app:

- **Code** – Validates input, handles null/empty safely, enforces auth and allowlists, keeps state consistent.
- **Tests** – Assert that behaviour; they fail when someone reintroduces a bug or weak validation.
- **Docs** – Record what is enforced and where (so future changes don’t undo it).

The Stripe/subscription work is the reference implementation of this style: analyse tests and code, then make necessary code changes for best results. Use this plan to repeat that process for the rest of the application over time.
