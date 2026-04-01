# Account page – research and recommendations

Research for adding a dedicated **Account** (or **Settings**) page to the Prayer-Chat frontend, aligned with existing backend APIs and UX patterns.

---

## 1. What the project already has

### Backend APIs

| Endpoint | Purpose |
|----------|---------|
| `GET /api/auth/me` | Current user: `id`, `username`, `email`, `roles`, `authProvider` |
| `POST /api/auth/logout` | Logout + optional Google logout URL |
| `GET /api/subscription/status` | `hasSubscription`, `status`, `plan`, `isActive`, `canUseChatbot`, `currentPeriodEnd`, `canceledAt` |
| `GET /api/subscription/details` | Full subscription entity (when present) |
| `POST /api/subscription/create-portal-session` | Stripe Customer Portal (manage payment, invoices) |
| `POST /api/subscription/create-checkout-session` | Start subscription (BASIC/PRO/ENTERPRISE) |
| `GET /api/plans/limits` | Plan limits (max pages, etc.) for tiers |

### Frontend

- **Auth:** `checkAuth()` → calls `/api/auth/me`; JWT in `localStorage`; cookie/session for backend.
- **Dashboard:** Shows “Manage subscription” (portal) and “Logout”; no dedicated account/settings route.
- **SecurityConfig** (backend): `/settings` is listed as authenticated; no `/account` yet. Either is fine for a single account/settings page.
- **User model (backend):** `id`, `username`, `email`, `googleId`, `authProvider`, `roles`, `createdAt`, `lastLogin`, `subscription`, cost-tracking fields.

There is **no** `/account` or `/settings` page in the app yet; the dashboard holds subscription and logout only.

---

## 2. Best practices for account pages

- **Single place for “me”:** Profile, subscription, and security in one Account (or Settings) page, with clear sections or tabs.
- **Profile section:** Show email, name (if any), auth provider (e.g. “Signed in with Google”). No password when using only OAuth.
- **Subscription section:** Current plan, status, next billing date, “Manage subscription” (Stripe portal), link to Pricing for upgrades.
- **Security / session:** Explain “Signed in with Google”; optional “Log out everywhere” (if you add it later). Logout is essential.
- **Privacy / data:** Link to Privacy Notice; optional “Download my data” or “Delete account” if you add those later.
- **Accessibility:** Headings, landmarks, focus order, and same auth redirect behaviour as dashboard (redirect to login if not authenticated).
- **Mobile:** Stack sections vertically; primary actions (Manage subscription, Logout) easy to tap.

---

## 3. Recommended structure for Prayer-Chat

One **Account** page at `/account` (or `/settings`) with these sections:

### 3.1 Profile

- **Email** (from `GET /api/auth/me` → `user.email`).
- **Display name** (e.g. `user.username` or “Signed in as {email}”).
- **Sign-in method:** “Google” (from `user.authProvider`). Optional: “Connected with Google” + icon.

No edit form needed if you don’t support changing name/email in-app (Google is source of truth).

### 3.2 Subscription

- **Current plan:** From `GET /api/subscription/status` → `plan` (FREE, BASIC, PRO, ENTERPRISE).
- **Status:** Active / Canceled / Trial, using `isActive`, `status`, `canceledAt`.
- **Next billing date:** `currentPeriodEnd` (if present).
- **Actions:**
  - “Manage subscription” → `POST /api/subscription/create-portal-session` → redirect to Stripe portal (same as dashboard).
  - “Upgrade” or “View plans” → link to `/pricing`.

Reuse the same API calls and types you use on the dashboard and pricing page.

### 3.3 Security & session

- Short line: “You are signed in with Google. Sign out below to end this session.”
- **Logout** button → call existing `logout()` (same as dashboard), then redirect to `/` or `/login`.

### 3.4 Links

- “Privacy Notice” → `/privacy`.
- “Legal Notice” → `/legal`.
- “Contact” → `/contact`.

Optional later: “Download my data”, “Delete account” (only if you add backend support).

---

## 4. Implementation outline

### 4.1 Route and layout

- **Path:** e.g. `frontend/app/account/page.tsx` (or `app/settings/page.tsx`).
- **Auth:** On load, call `checkAuth()`; if not authenticated, `router.replace('/login')` (same pattern as dashboard/onboarding).
- **Layout:** Reuse existing root layout (Header + Footer). Optional: add “Account” in the header when logged in (or only from dashboard).

### 4.2 Data loading

- **User:** `checkAuth()` or `GET /api/auth/me` (you already have `checkAuth()`).
- **Subscription:** Add and use a dedicated call to `GET /api/subscription/status` from the frontend (e.g. `getSubscriptionStatus()` that calls this endpoint instead of the current heuristic). Use the same backend response shape: `hasSubscription`, `plan`, `isActive`, `currentPeriodEnd`, etc.

### 4.3 UI

- **Styling:** Reuse dashboard/pricing patterns: brown/gold theme, `motion` for entrance, Tailwind, same button styles.
- **Sections:** Card-style blocks (e.g. “Profile”, “Subscription”, “Security”) with clear headings.
- **Subscription:** If plan is FREE and no Stripe customer, show “Free trial” and “Upgrade” (link to `/pricing`). If subscribed, show plan name, status, next billing, “Manage subscription”.
- **Errors:** If `/api/subscription/status` fails, show a neutral message (“Unable to load subscription”) and still show Profile and Logout.

### 4.4 Navigation

- **From dashboard:** Add an “Account” (or “Settings”) link/button next to “Manage subscription” and “Logout”.
- **Header (optional):** Add “Account” when authenticated; or keep header minimal and only link from dashboard.

### 4.5 Backend

- No new endpoints required. `/api/auth/me` and `/api/subscription/status` (and `/details` if needed) are enough.
- Ensure `/account` (or `/settings`) is treated as authenticated in your backend if you ever add per-route checks; SecurityConfig already has `.requestMatchers("/settings").authenticated()` — if you use `/account`, add `/account` there too, or keep the page as a client-only route (no backend route for “account page” itself).

---

## 5. Summary

| Item | Recommendation |
|------|----------------|
| **Route** | `/account` or `/settings` (one page). |
| **Sections** | Profile (email, provider), Subscription (plan, status, manage/upgrade), Security (logout), Links (privacy, legal, contact). |
| **APIs** | `GET /api/auth/me`, `GET /api/subscription/status`, existing `logout()` and Stripe portal session. |
| **Auth** | Same as dashboard: redirect to login if not authenticated. |
| **Navigation** | Link from dashboard (e.g. “Account” next to “Manage subscription” / “Logout”). |
| **Styling** | Match dashboard/pricing (brown/gold, Framer Motion, Tailwind). |

Implementing the page with these sections and reusing existing APIs will give you a clear, consistent account experience without new backend work.
