# Security Action Plan

**Date:** 2026-04-07
**Scope:** Backend, frontend, CI/CD, dependency posture, and runtime security verification.

## 1) Immediate (This Week) - Close Critical/High Gaps

- [ ] Deploy completed fixes to main and production:
  - Require authentication for all `/api/chatbots/**` routes.
  - Enforce ownership checks for conversation export by `conversationId`.
  - Enforce OAuth `state` generation and callback validation in frontend login flow.
- [ ] Add regression tests for:
  - Unauthorized export attempts (`401`/`404` behavior).
  - OAuth callback with missing or invalid `state`.
- [ ] Run full verification before release:
  - Backend: `mvn test`
  - Frontend: `npm run test:ci`
  - Security checks: Trivy + dependency scan

## 2) Short Term (Next 2 Weeks) - Make Security Enforceable

- [ ] Make security scans blocking quality gates in CI:
  - Remove `continue-on-error` from critical security steps.
  - Fail PRs on High/Critical vulnerabilities.
- [ ] Re-enable CodeQL (if repository settings/licensing permit).
- [ ] Tighten suppression hygiene:
  - Review each `.trivyignore` suppression.
  - Keep only justified suppressions with expiry and owner.

## 3) Medium Term (30 Days) - Reduce Token Exposure Risk

- [ ] Migrate browser auth flows toward cookie-only auth:
  - Minimize/remove `localStorage` token usage.
  - Prefer `HttpOnly`, `Secure` cookies with appropriate `SameSite` policy.
- [ ] Add auth hardening tests for:
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

## Priority Order

1. Merge and deploy implemented critical fixes.
2. Make CI security scans blocking.
3. Minimize/remove `localStorage` auth token exposure.
4. Establish recurring review cadence and risk register.
