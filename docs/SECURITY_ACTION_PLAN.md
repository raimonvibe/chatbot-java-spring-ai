# Security Action Plan

**Date:** 2026-04-07
**Scope:** Backend, frontend, CI/CD, dependency posture, and runtime security verification.

## 1) Immediate (This Week) - Close Critical/High Gaps

- [x] Deploy completed fixes to main and production:
  - Require authentication for all `/api/chatbots/**` routes.
  - Enforce ownership checks for conversation export by `conversationId`.
  - Enforce OAuth `state` generation and callback validation in frontend login flow.
- [x] Add regression tests for:
  - Unauthorized export attempts (`401`/`404` behavior).
  - OAuth callback with missing or invalid `state`.
- [x] Run full verification before release:
  - Backend: `mvn test`
  - Frontend: `npm run test:ci`
  - Security checks: Trivy + dependency scan

## 2) Short Term (Next 2 Weeks) - Make Security Enforceable

- [x] Make security scans blocking quality gates in CI:
  - Remove `continue-on-error` from critical security steps.
  - Fail PRs on High/Critical vulnerabilities.
- [x] Re-enable CodeQL (if repository settings/licensing permit).
- [x] Tighten suppression hygiene:
  - Review each `.trivyignore` suppression.
  - Keep only justified suppressions with expiry and owner.

## 3) Medium Term (30 Days) - Reduce Token Exposure Risk

- [x] Migrate browser auth flows toward cookie-only auth:
  - Minimize/remove `localStorage` token usage.
  - Prefer `HttpOnly`, `Secure` cookies with appropriate `SameSite` policy.
- [x] Add auth hardening tests for:
  - Session expiry and stale cookie handling.
  - Clear `401` vs `403` semantics.
  - CSRF checks for credentialed endpoints.

## 4) Operational Hardening (60 Days)

- [ ] Formalize OWASP ASVS + API Top 10 traceability:
  - Map each control to code location and tests.
  - Keep a living checklist in repository docs.
- [ ] Strengthen deployment posture checks:
  - Script validation for production headers, TLS, CORS, and cookie flags.
  - Confirm webhook secret validation and IP allowlist posture.

## 5) Continuous Security Program (90 Days)

- [ ] Schedule recurring security validation:
  - Monthly dependency and suppression review.
  - Quarterly black-box API checks (authz, rate limiting, CORS, export endpoints).
- [ ] Maintain a residual risk register:
  - Risk, owner, target date, mitigation status.
- [ ] Enforce release security gate:
  - Block deploys for critical auth/access-control regressions.

## Current Status Snapshot

- Completed: Sections 1, 2, and 3 implementation tracks.
- In progress: Section 4 runtime evidence packaging and operational checklist formalization.
- Remaining: Section 5 governance cadence, residual-risk register maintenance, and long-term release gate operations.

## Residual Risks (Current)

- Medium: Runtime proof bundle is partially manual; needs scripted repeatable checks for headers/cookies/CORS.
- Medium: Some Trivy suppressions remain accepted with expiry and owner metadata; requires scheduled review.
- Low/Medium: E2E coverage is aligned to cookie-first behavior but should be expanded for additional cross-origin edge cases.

## 30/60/90 Refresh

- 30 days:
  - Finish Section 4 evidence pack and add a repeatable runtime verification checklist.
  - Validate all auth/session checks on production after each deploy.
- 60 days:
  - Publish ASVS/API Top 10 control-to-test traceability document in repo docs.
  - Add scripted header/cookie/CORS verification to release validation.
- 90 days:
  - Run quarterly black-box authorization and abuse-control regression checks.
  - Review/renew suppressions and update risk register with owner and due date.

## Priority Order

1. Merge and deploy implemented critical fixes.
2. Make CI security scans blocking.
3. Minimize/remove `localStorage` auth token exposure.
4. Establish recurring review cadence and risk register.
