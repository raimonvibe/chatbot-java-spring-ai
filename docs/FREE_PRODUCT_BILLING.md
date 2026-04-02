# Free product mode and billing (Stripe)

This app can run **without charging users** while keeping all Stripe-related code paths in the repository for a future paid launch.

## Backend

- **Property:** `app.billing.enabled` (environment: `APP_BILLING_ENABLED`).
- **Default in `application.yml`:** `false` for a free-first deployment.
- **When `false`:**
  - Mutating subscription APIs (checkout, portal, plan changes) return **403** with a stable error code (`BILLING_DISABLED` where applicable).
  - Stripe webhooks verify the signature but **do not apply** business logic if billing is disabled (avoids accidental state changes).
  - `BillingModeService` relaxes quotas and adjusts user-facing messages so they do not promise “upgrade to Stripe” when billing is off.
- **Tests:** `application-test.yml` may set `enabled: true` so existing Stripe integration tests remain valid.

## Frontend

- **Environment:** `NEXT_PUBLIC_BILLING_ENABLED` should mirror the backend intent for static/marketing behavior before the user is logged in.
- **Runtime source of truth (logged-in):** `GET /api/subscription/status` exposes `billingEnabled` and `paymentActionsAvailable`. The UI hides checkout, the billing portal, and upgrade modals when those indicate billing is off.
- **Public limits copy:** `GET /api/plans/limits` includes `billingEnabled`, `maxPagesPerScanOffered`, and `websiteScanPolicySummary` so forms and the pricing page stay aligned with the server (e.g. **500 pages per scan** cap).

## Re-enabling paid plans

1. Set `APP_BILLING_ENABLED=true` (and configure Stripe keys, webhook secret, success/cancel URLs).
2. Set `NEXT_PUBLIC_BILLING_ENABLED=true` on the frontend host.
3. Deploy and run through a test checkout in Stripe test mode.
4. Confirm `GET /api/subscription/status` returns `paymentActionsAvailable: true` for test subscribers.

No code removal is required; toggles only.

## Large websites (UX)

- Crawling is capped (see `app.website-analysis.max-pages`, aligned with `PlanLimits.FREE_MAX_PAGES`).
- When a URL exceeds the limit, the API returns a clear message (with estimated vs max pages). The onboarding/dashboard URL form shows that message after submit instead of an upfront banner.
