# Render: New Backend + DB Setup

## Build failing with Spring Boot 4.0.3 / Jackson BOM error?

The error:
```text
tools.jackson:jackson-bom:pom:2.18.6 (absent)
'dependencies.dependency.version' for org.springframework.boot:spring-boot-starter-aop:jar is missing
```

means the **code being built** still has **Spring Boot 4.0.3** in `backend/pom.xml`. This repo uses **3.3.13** (fixed in commit `ea94d54`).

**Fix:**

1. In Render Dashboard → your **Backend** service → **Settings** (or **Build & Deploy**):
   - **Branch** must be the one that has the fix (e.g. `main`).
2. **Manual deploy:** Trigger **Manual Deploy** → **Deploy latest commit** (so it uses current `main`).
3. If the service is from a **fork**, merge or pull from `raimonvibe/chatbot-java-spring-ai` so your fork’s `backend/pom.xml` has `<version>3.3.13</version>` in the `<parent>` block.

**Docker path (already correct in this repo):**

- **Root Directory:** leave **empty** (build context = repo root).
- **Dockerfile Path:** `backend/Dockerfile`.

The Dockerfile does `COPY backend/pom.xml` and `COPY backend/src`, so the build context must be the **repo root**, not `backend/`.

---

## Env vars for new backend + new DB on Render

Set these in **Render Dashboard → Backend service → Environment**.

### Required (backend won’t start without them)

| Key | Value / source |
|-----|----------------|
| `DATABASE_URL` | From your **PostgreSQL** service: Connect → **Internal Database URL** (or External). Can be `postgresql://user:pass@host:5432/dbname` or `postgres://...` — the app normalizes it to JDBC format for Spring Session. |
| `DATABASE_DRIVER` | `org.postgresql.Driver` |
| `HIBERNATE_DIALECT` | `org.hibernate.dialect.PostgreSQLDialect` |
| `DDL_AUTO` | `update` |
| `JWT_SECRET` | Long random string (e.g. 32+ chars). Render can generate. |
| `ANTHROPIC_API_KEY` | Your Anthropic API key |
| `COHERE_API_KEY` | Your Cohere API key |

### Optional but recommended

| Key | Value |
|-----|--------|
| `BIBLE_AUTO_LOAD` | `true` (load Bible verses on first start) |
| `CORS_ALLOWED_ORIGINS` | Your frontend URL(s), e.g. `https://your-app.onrender.com,https://your-custom-domain.com` |
| `GOOGLE_CLIENT_ID` | If using Google login |
| `GOOGLE_CLIENT_SECRET` | If using Google login |

### One-time embedding import (after first deploy)

| Key | Value |
|-----|--------|
| `IMPORT_EMBEDDINGS_FILE` | `/tmp/data/bible_embeddings.json` |
| `IMPORT_EMBEDDINGS_URL` | Direct download URL of your `bible_embeddings.json` |

Remove both after logs show “Embedding import completed successfully!” (see `docs/RENDER_BIBLE_AND_EMBEDDINGS_CHECKLIST.md`).

---

## “Failed to determine DatabaseDriver” (JdbcSessionConfiguration)

If you see:
```text
Error creating bean with name 'jdbcSessionDataSourceScriptDatabaseInitializer' ...
Failed to determine DatabaseDriver
```

**Cause:** Render’s `DATABASE_URL` is often `postgresql://...` or `postgres://...` without the `jdbc:` prefix. Spring Session JDBC needs a proper JDBC URL to detect the driver.

**Fix (in code):** The app’s `AnthropicApiKeyEnvironmentPostProcessor` now normalizes `DATABASE_URL` to `jdbc:postgresql://...` before the DataSource and session initializer run. Ensure you’re on a commit that includes this change.

**If it still fails:** The message can hide the real error (e.g. connection refused, wrong password). Check that:
- `DATABASE_URL` is the **Internal** (or External) URL from the same Render PostgreSQL service.
- The backend and database are in the same Render account and the DB is running.
- Username/password in the URL are correct (Render injects the full URL when you use “Connect” → Internal Database URL).

---

## If you use Blueprint (render.yaml)

Blueprint sets `dockerfilePath: backend/Dockerfile` and does **not** set a root directory, so the build context is the repo root. Ensure the **linked GitHub repo and branch** are the ones that contain the 3.3.13 fix (e.g. `main`), then redeploy.
