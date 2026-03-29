# CORS and Vercel: Safer configuration

## Risk of `https://*.vercel.app`

If you set `CORS_ALLOWED_ORIGINS` (or `cors.allowed-origins`) to include **`https://*.vercel.app`**, the backend will accept cross-origin requests from **any** site hosted on `*.vercel.app`. That includes:

- Your own Vercel app and preview deployments (e.g. PR previews, branch deploys).
- **Any other** Vercel deployment, including apps created by others or by an attacker.

So a malicious site at `https://evil-app.vercel.app` could:

- Call your API from the browser (subject to CORS and your auth rules).
- Rely on the browser sending cookies to your backend if the user has a session and cookie policy allows it (e.g. `SameSite=None`).

Even if you rely on authentication to protect sensitive endpoints, allowing every `*.vercel.app` origin is broader than necessary and increases attack surface (e.g. abuse of public endpoints, confusion with your real frontend).

## Safer approach: project-specific pattern

Use a **tighter origin pattern** that matches only your project’s Vercel URLs.

Typical Vercel URLs:

- Production: `https://<project-name>.vercel.app`
- Previews: `https://<project-name>-xxx-<team>.vercel.app`, `https://<project-name>-git-<branch>-<team>.vercel.app`

So instead of:

```text
https://*.vercel.app
```

use a pattern that starts with your project name, for example:

```text
https://prayer-chat*.vercel.app
```

That allows:

- `https://prayer-chat.vercel.app`
- `https://prayer-chat-abc123-yourteam.vercel.app`
- `https://prayer-chat-git-main-yourteam.vercel.app`

but **not** arbitrary apps like `https://other-app.vercel.app` or `https://evil.vercel.app`.

## Example `CORS_ALLOWED_ORIGINS`

**Recommended (production + Vercel previews, project-specific):**

```text
CORS_ALLOWED_ORIGINS=https://prayer-chat.com,https://www.prayer-chat.com,https://prayer-chat*.vercel.app,http://localhost:3000
```

**Avoid (too permissive):**

```text
CORS_ALLOWED_ORIGINS=...,https://*.vercel.app
```

Replace `prayer-chat` with your actual Vercel project name if different.

## If you don’t use Vercel previews

If you only use production (e.g. `prayer-chat.com`) and localhost, omit Vercel entirely:

```text
CORS_ALLOWED_ORIGINS=https://prayer-chat.com,https://www.prayer-chat.com,http://localhost:3000
```

No `*.vercel.app` pattern is needed.
