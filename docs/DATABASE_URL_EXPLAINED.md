# DATABASE_URL explained

`DATABASE_URL` is the **PostgreSQL connection string** your backend uses to connect to the database. It includes the host, port, database name, and credentials.

## Where to get it on Render

1. Open your **PostgreSQL** service in the Render dashboard.
2. In the **Connect** (or **Info**) panel you'll see two URLs:
   - **Internal Database URL** — use this when your **backend runs on Render** (same network, faster, no egress).
   - **External Database URL** — use when connecting from **outside Render** (e.g. your laptop, another cloud).
3. Copy the **Internal Database URL** and set it as the `DATABASE_URL` environment variable on your **Backend** service.

## Format

Render gives a URL in one of these forms:

- `postgresql://USERNAME:PASSWORD@HOST:5432/DATABASE_NAME`
- `postgres://USERNAME:PASSWORD@HOST:5432/DATABASE_NAME` (same thing; `postgres` is an alias for `postgresql`)

Example (values are placeholders):

```text
postgresql://prayer_chat_user:AbCdEf123@dpg-xxxxx-a.oregon-postgres.render.com:5432/prayer_chat_db
```

- **USERNAME** / **PASSWORD** — Render generates these when the database is created; they're embedded in the URL.
- **HOST** — For Internal URL, something like `dpg-xxxxx-a.oregon-postgres.render.com`.
- **5432** — Default PostgreSQL port.
- **DATABASE_NAME** — The database name (e.g. `prayer_chat_db`).

## What this app does with DATABASE_URL

- **JDBC normalization:** Spring expects a JDBC URL (`jdbc:postgresql://...`). Render gives `postgresql://` or `postgres://`. The app's startup code converts it to `jdbc:postgresql://...` so Spring Session and JPA can detect the driver and connect.
- **SSL on Render:** If the URL host contains `.render.com`, the app may add `?sslmode=require` (or `&sslmode=require` if the URL already has query params) so the connection works when Render requires SSL.

You can paste the URL exactly as Render shows it; you do **not** need to add `jdbc:` or `sslmode=require` yourself unless you're on an older build.

## Internal vs External URL

| Use this URL   | When |
|----------------|------|
| **Internal**   | Backend and PostgreSQL are both on Render (same account). Shorter path, no public internet. |
| **External**   | Connecting from your machine, another provider, or a different Render account. Uses the public hostname and may have different rate/limits. |

For a backend deployed on Render talking to a Render PostgreSQL service, always use the **Internal** URL for `DATABASE_URL`.

## Security

- The URL contains the database **password**. Treat it as a secret.
- Never log `DATABASE_URL` or commit it. The app does not log the URL value.
- In Render, set it only as an **Environment Variable** (or Secret) on the Backend service; Render does not expose it in the UI after you paste it.

## Optional query parameters

You can append query parameters if needed (the app only adds `sslmode=require` when missing for Render-style hosts):

- `?sslmode=require` — Require SSL (Render Postgres typically needs this).
- `?sslmode=verify-full` — SSL and verify server certificate (stricter).
- `?connect_timeout=10` — Connection timeout in seconds.

Example with params:  
`postgresql://user:pass@host:5432/dbname?sslmode=require&connect_timeout=10`

## See also

- [RENDER_NEW_SETUP.md](RENDER_NEW_SETUP.md) — Full Render setup and troubleshooting (build errors, session, EntityManagerFactory).
