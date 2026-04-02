# Session and authentication

How session timeout and cleanup work, and how to configure them securely.

---

## Session timeout vs cleanup cron

- **Session timeout** (e.g. `spring.session.timeout: 24h`) controls when a user is logged out: after that period of **inactivity** the session expires and the user must log in again. Activity resets the timer.
- **Cleanup cron** (e.g. `spring.session.jdbc.cleanup-cron: "0 0 * * * *"`) only **removes expired** session rows from the database. It does **not** log users out; it just keeps the DB tidy.

So: users are logged out by the timeout; the cron only deletes already-expired data.

---

## Recommended timeout

- **Banking/healthcare:** often 5–30 minutes.
- **SaaS / e‑commerce:** often 30 minutes–4 hours.
- **Chatbot / customer service:** 1–4 hours is typical.

For this app, **2–4 hours** is a good balance of security and UX. **24 hours** is long; consider shortening in production.

Example:

```yaml
spring:
  session:
    timeout: 2h   # or 4h
```

Cookie and server timeout should match (e.g. 24h = 86400 seconds for `cookie.max-age`).

---

## Cookie and security

- **secure: true** on cookies in production (HTTPS).
- **httpOnly: true** to reduce XSS risk.
- **sameSite: lax** for CSRF mitigation while allowing OAuth redirects.

Spring Session JDBC stores session data in the database; Spring encrypts sensitive data and sessions are removed after timeout. This is the usual approach for cloud deployments and works with multiple instances.

---

## Optional tweaks

- **Custom session cookie name** (e.g. `PRAYER_CHAT_SESSION`) to avoid default fingerprinting.
- **Shorter cleanup interval** if you want expired rows removed more often (optional).
- **Database encryption at rest** in production where available (e.g. Render).

---

*Merged from SESSION_TIMEOUT_EXPLANATION, SESSION_TIMEOUT_BEST_PRACTICES, and SESSION_SECURITY_ANALYSIS.*
