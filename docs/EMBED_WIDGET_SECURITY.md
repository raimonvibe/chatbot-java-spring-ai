# Embed Widget Security

This document describes the security design of the Prayer-Chat embeddable widget flow: how embed code is generated, how the widget loads and talks to the API, and what we do to keep it secure.

## Flow Overview

1. **Embed code** is only available to authenticated users with a **paid subscription** (access control in `ChatbotController.getEmbedCode`). The code is generated server-side using a **configured base URL** (`app.base-url`), never user input.
2. **Widget script** is loaded from the backend (`/js/chatbot-widget.js`) on the customer’s site. It calls:
   - `GET /api/chat/embed/{chatbotId}` — public, returns non-sensitive config (name, description, sanitized branding).
   - `POST /api/chat/{chatbotId}` — public, sends messages; rate-limited and validated.
3. **No authentication** is required for widget endpoints; they are designed to be called from any origin (customer websites). Security is enforced by **rate limiting**, **input validation**, and **not exposing secrets or PII** in public responses.

## Threats and Mitigations

| Threat | Mitigation |
|--------|------------|
| **XSS in generated embed code** | Base URL is validated (`EmbedSecurity.validateAndNormalizeBaseUrl`) and escaped for use inside a JavaScript string (`EmbedSecurity.escapeForJsString`). Only `http`/`https` URLs with a safe host pattern are allowed. |
| **SSRF** | Base URL comes from configuration only (`app.base-url`). No user-controlled URL is used for script or API base. |
| **XSS via brandingConfig** | Public embed config response sanitizes `brandingConfig` (`EmbedSecurity.sanitizeBrandingConfig`): only keys `primaryColor`, `secondaryColor`, `fontFamily`, `borderRadius` are allowed, with value patterns that cannot inject script. |
| **Abuse / DoS** | (1) **Global**: `/api/chat/*` is rate-limited per client (IP or token) via `RateLimitingFilter` (e.g. 20 req/min). (2) **Per-IP per-chatbot**: POST `/api/chat/{id}` enforces 30 messages per hour per IP per chatbot so one visitor cannot exhaust the owner’s quota. (3) GET `/api/chat/embed/{embedCodeOrId}` rejects path length &gt; 255 to prevent DoS. (4) Chat request body is validated (message 1–2000 chars, pattern; sessionId and language format). |
| **Sensitive data leakage** | GET `/api/chat/embed/{id}` returns only: `chatbotId`, `name`, `description` (capped), `primaryLanguage`, `supportedLanguages`, `brandingConfig` (sanitized). No tokens, API keys, or owner PII. |
| **Inactive chatbot** | Embed config and chat endpoints return 403 if the chatbot is not active. |

## Configuration

- **app.base-url**: Must be the public base URL of this backend (e.g. `https://chatbot-java-spring-ai.onrender.com`). Used only in generated embed snippets. If missing or invalid, a hardcoded default is used.
- **CORS**: Widget endpoints are public; CORS is configured at the application level. No need to allowlist customer domains for the widget API, since the script and API are on the same backend origin from the browser’s perspective when the script is loaded from our server.

## Widget Script

- Served from the same backend as the API (`/js/chatbot-widget.js`). No credentials or cookies are sent with widget requests.
- Uses `textContent` (or equivalent) for chatbot name to avoid XSS when the config is applied.
- Branding is applied only to style properties (color, font, border-radius); values come from the sanitized `brandingConfig` from the API.

## Summary

Embed code generation and the public widget API are scoped so that:

- Only **paid, authenticated** users can obtain embed code.
- Generated **snippets are safe** (no injectable base URL).
- **Public config** exposes only non-sensitive, sanitized data.
- **Chat** is rate-limited and input-validated, with no auth required for end-users.
