# Secure Coding Guidelines

**Purpose:** In-repo reference for developers. Complements SECURITY_AUDIT_PLAN.md and OWASP practices.  
**Scope:** Backend (Spring Boot/Java), Frontend (Next.js/React), APIs, and configuration.

---

## 1. Input validation & output encoding

### Backend (Spring/Java)

- **Always validate** incoming request bodies and params with Bean Validation: `@Valid`, `@NotBlank`, `@Size`, `@Pattern`, `@Email`. Reject invalid input with 400.
- **Sanitize for XSS** before storing or returning user-controlled HTML: use the project’s `XssSanitizer` (JSoup-based). Never render raw user input in HTML.
- **Validate URLs** used for outbound requests (e.g. website analysis, imports) with `UrlValidationService` to prevent SSRF (no localhost, private IPs, or dangerous schemes).
- **Robots.txt (RobotsTxtService):** Validate crawl URL and robots.txt request URL with `UrlValidationService`. After fetching robots.txt, validate the final response URL (after redirects) to prevent SSRF via redirect. Use `maxBodySize` to limit response size (e.g. 64KB). Sanitize URLs in log messages with `LogSanitizer.sanitizeForLogging`. See `RobotsTxtServiceTest` for security tests.
- **Validate file paths** when reading/writing files: use the existing path validation (e.g. `validateAndResolveFilePath`); block `..` and paths outside allowed directories.
- **Limit lengths** (request body, query params, headers) to prevent DoS and buffer issues. Use `@Size` and framework limits.

### Frontend (Next.js/React)

- **Escape by default:** Use React’s JSX (no `dangerouslySetInnerHTML` with user input unless sanitized).
- **Validate/sanitize** any user input before sending to the API and before displaying; prefer the backend as the source of truth for validation.
- **API responses:** Treat all API data as untrusted when rendering; avoid injecting raw HTML.
- **Dashboard & Footer:** Chatbot name/description are rendered as text (React escapes). Portal redirect URL is validated (Stripe domains only). Auth redirect when unauthenticated. Footer uses internal links only; external link has `rel="noopener noreferrer"`. See `app/dashboard/__tests__/page.test.tsx` and `components/__tests__/Footer.test.tsx` for security and accessibility tests.

---

## 2. Authentication & authorization

- **Never trust client** for privilege or ownership. Resolve the user from the JWT/session on the server and check ownership/roles there.
- **Every resource endpoint** must verify that the authenticated user is allowed to access the resource (e.g. chatbot owner, subscription tier). Use `AccessControlService` and ownership checks.
- **Sensitive operations** (e.g. delete account, change payment method) should require a fresh auth check or re-authentication where appropriate.
- **JWT:** Keep expiration short (e.g. 24h). Store secret only in env (`JWT_SECRET`); never in code or logs. Use `LogSanitizer` so tokens never appear in logs.

---

## 3. Concurrency & quotas

- **Quota and limit checks** that “read then update” (e.g. cost tracking, chatbot count) must run under **pessimistic locking** to avoid race conditions. Use `UserRepository.findByIdWithLock` and service methods that lock before check-then-update.
- **Follow existing patterns** in `CostTrackingService` and `ChatbotService.createChatbotEnforcingLimit` for any new limit enforcement.

---

## 4. Secrets & configuration

- **No secrets in code or repo.** Use environment variables or a secrets manager. Document required vars in `RENDER_ENV_VARIABLES.md` and `.env.example` (values as placeholders only).
- **Logging:** Never log passwords, API keys, tokens, or full credentials. Use `LogSanitizer` and redact in exceptions. See existing `LogSanitizer` usage in controllers.
- **Frontend:** Only `NEXT_PUBLIC_*` vars are exposed to the browser; never put secrets there. Use the backend for any operation that needs a secret.

---

## 5. Payments & webhooks

- **Stripe webhooks:** Always verify signature using `STRIPE_WEBHOOK_SECRET` before processing. See `StripeWebhookController`.
- **Idempotency:** Handle duplicate webhook deliveries; use idempotency keys or stored event IDs where applicable.
- **Do not expose** Stripe secret key or webhook secret to the frontend.

---

## 6. Errors & logging

- **Do not expose** stack traces or internal details to clients. Return generic messages to users; log details server-side with sanitization.
- **Audit security events:** Use `AuditService` for login failures, subscription changes, payment events, and suspicious activity. Use `SecurityAlertService` for alerts (failed login spike, rate limit, payment failure, fraud risk).
- **Structured context:** Include relevant IDs (user, resource) in logs for investigation; never log full tokens or passwords.

---

## 7. Dependencies & builds

- **Backend:** Run OWASP Dependency Check when updating dependencies (`mvn dependency-check:check -Ddependency-check.skip=false`). Address high/critical before release. Use Dependabot and the repo’s dependency-check workflow.
- **Frontend:** Keep `npm audit` clean; fix high/critical. Update dependencies regularly.
- **Pin versions** in production (exact or narrow range); avoid floating tags for security-sensitive images or actions.

---

## 8. HTTP & headers

- **HTTPS only** in production. Enforce HSTS (already in security config).
- **Security headers** are set in `SecurityConfig` (CSP, X-Frame-Options, etc.). Do not disable them for convenience.
- **CORS:** Use explicit origins and the allowed headers list (no wildcard `*` for headers). See `SecurityConfig` and `CORS_ALLOWED_ORIGINS`.

---

## 9. File & upload handling

- **Validate file type and size** before processing. Use the existing embedding import rules (e.g. `.json` only, size limits).
- **Resolve paths** only via the approved helper; never use user-controlled paths directly for filesystem access.
- **Downloads from URLs:** Validate URLs with `UrlValidationService` and enforce size/time limits.

---

## 10. Quick reference

| Topic            | Backend reference                    | Frontend / ops              |
|------------------|--------------------------------------|-----------------------------|
| Validation       | Bean Validation, XssSanitizer        | Validate before submit/display |
| SSRF             | UrlValidationService                 | N/A (backend only)          |
| Ownership        | AccessControlService, repo checks    | Never trust client role     |
| Concurrency      | findByIdWithLock, createChatbotEnforcingLimit | N/A |
| Secrets          | Env vars, LogSanitizer               | No secrets in NEXT_PUBLIC_* |
| Payments         | StripeWebhookController (signature) | No Stripe secret in UI      |
| Alerts           | SecurityAlertService                 | N/A                         |
| Audit            | AuditService                         | N/A                         |

---

*See also: SECURITY_AUDIT_PLAN.md, API_KEY_ROTATION.md, INCIDENT_RESPONSE.md.*
