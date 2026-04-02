# Incident Response Procedures

**Purpose:** Standard steps for handling security incidents. Keep this doc and SECURITY_AUDIT_PLAN.md updated when contacts or tooling change.

---

## 1. Severity levels

Use these to decide response speed and who to involve.

| Severity   | Examples | Response target |
|-----------|----------|------------------|
| **CRITICAL** | Data breach, payment compromise, auth bypass | Immediate; consider user notification |
| **HIGH**     | Unauthorized access, cost/rate limit bypass   | Fix within 24 hours |
| **MEDIUM**   | Information disclosure, DoS                  | Fix within 1 week |
| **LOW**      | Minor vulnerabilities                        | Fix in next sprint |

---

## 2. Response steps (high level)

1. **Triage** – Confirm it’s a real security issue and assign severity (use table above).
2. **Contain** – Limit impact (e.g. revoke tokens, disable a key, block IP, rollback or feature flag off).
3. **Investigate** – Preserve logs and audit trail; identify root cause and scope (who/what affected).
4. **Remediate** – Apply fix, rotate credentials if needed (see API_KEY_ROTATION.md), deploy.
5. **Communicate** – Notify stakeholders; notify users if data was exposed or actions required (e.g. password reset).
6. **Post-incident** – Short write-up (what happened, cause, fix, follow-ups); update runbooks and monitoring.

---

## 3. Per-severity actions

### CRITICAL

- Assemble response lead and (if applicable) backend/frontend/ops.
- Contain immediately (e.g. take affected service or endpoint offline, revoke keys, disable compromised accounts).
- Preserve evidence: logs, audit logs, DB snapshots if needed; avoid altering evidence before capture.
- Fix and deploy; rotate any potentially exposed secrets (JWT, Stripe, OAuth, API keys) per API_KEY_ROTATION.md.
- Decide on user notification (legal/compliance may require it); use a clear channel (email, in-app).
- Document timeline, cause, and follow-up actions.

### HIGH

- Assign owner; contain within hours (e.g. block abuse, revert or patch).
- Use audit logs and SecurityAlertService / app logs to determine scope.
- Deploy fix; rotate credentials if they may have been exposed.
- Internal communication; user notification only if their data or account was affected.

### MEDIUM / LOW

- Track in issues; schedule fix within target (1 week / next sprint).
- Contain if trivial (e.g. disable a feature); otherwise fix in normal release.
- No formal post-mortem unless recurrence or high impact.

---

## 4. Useful resources in this repo

- **Audit logs** – `AuditService` / audit log storage: login failures, subscription and payment events, security events.
- **Security alerts** – `SecurityAlertService`: failed login spike, rate limit, payment failure, fraud risk. Optional webhook: `SECURITY_ALERT_WEBHOOK_URL`.
- **Rotation** – API_KEY_ROTATION.md for JWT, Stripe, OAuth, API keys.
- **Security plan** – SECURITY_AUDIT_PLAN.md for architecture, controls, and checklist.

---

## 5. Contacts and tooling (fill in)

- **Incident lead:** _[e.g. tech lead or security owner]_
- **Hosting/ops:** _[e.g. Render dashboard, who can restart or scale]_
- **Monitoring/alerts:** _[e.g. where SECURITY_ALERT_WEBHOOK_URL points: Slack, PagerDuty]_
- **Log/audit access:** _[e.g. where audit logs and app logs are viewed]_

---

## 6. After an incident

- Update this doc if you discover missing steps or wrong contacts.
- Consider: Do we need a new alert or check to detect this earlier? Update SecurityAlertService or monitoring as needed.
- If credentials were exposed, complete rotation per API_KEY_ROTATION.md and confirm old credentials are revoked.

---

*See also: SECURITY_AUDIT_PLAN.md (Incident Response Plan section), API_KEY_ROTATION.md.*
