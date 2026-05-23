# Render Environment Variables Configuration

## Overzicht

Deze applicatie gebruikt **spring-dotenv 4.0.0** voor lokale ontwikkeling (laadt `.env` files automatisch) en **system environment variables** in productie op Render.

## Belangrijk: Verschil tussen lokale .env en Render

- **Lokaal**: spring-dotenv laadt automatisch `.env` files uit de working directory
- **Render/Productie**: Environment variables worden direct ingesteld als system environment variables (geen .env file nodig)

## Vereiste Environment Variables voor Render

Zorg ervoor dat alle volgende variabelen zijn ingesteld in het Render dashboard:

### 🔐 Security & Authentication
```
JWT_SECRET=<jouw-jwt-secret-key>
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000
```

### 🤖 AI Services
```
ANTHROPIC_API_KEY=<jouw-anthropic-api-key>
COHERE_API_KEY=<jouw-cohere-api-key>
# Optional: if Render logs show "HttpTimeoutException" from Cohere during indexing
# (values are clamped server-side: timeout 5–120s, max-text-chars 256–4096)
COHERE_EMBED_TIMEOUT_SECONDS=90
COHERE_EMBED_MAX_TEXT_CHARS=2048
```

### 🔑 OAuth2 (Google Login)
```
GOOGLE_CLIENT_ID=<jouw-google-client-id>
GOOGLE_CLIENT_SECRET=<jouw-google-client-secret>
```

### 💳 Stripe Payments
```
STRIPE_SECRET_KEY=<jouw-stripe-secret-key>
STRIPE_WEBHOOK_SECRET=<jouw-stripe-webhook-secret>
STRIPE_PRICE_ID=<jouw-stripe-price-id>
STRIPE_SUCCESS_URL=https://www.prayer-chat.com/account?payment=success&session_id={CHECKOUT_SESSION_ID}
STRIPE_CANCEL_URL=https://www.prayer-chat.com/pricing
```

**Stripe redirect security (Render):**  
The backend validates `STRIPE_SUCCESS_URL` and `STRIPE_CANCEL_URL` at startup: the URL host must be in the allowed list. The default allowed list includes `https://www.prayer-chat.com`, so the URL above is **secure to set on Render**. Do **not** set these to a third-party domain. If you use a different domain, set `STRIPE_ALLOWED_REDIRECT_ORIGINS` to a comma-separated list that includes that host (e.g. `https://www.prayer-chat.com,https://prayer-chat.com`); otherwise the app will fail to start with a clear error.

### 🗄️ Database (PostgreSQL op Render)
```
DATABASE_URL=jdbc:postgresql://<host>:<port>/<database>
DATABASE_DRIVER=org.postgresql.Driver
DATABASE_USERNAME=<username>
DATABASE_PASSWORD=<password>
```

### 🌐 Server Configuration
```
PORT=10000
CORS_ALLOWED_ORIGINS=https://prayer-chat.com,https://www.prayer-chat.com,https://prayer-chat*.vercel.app,http://localhost:3000
APP_BASE_URL=https://chatbot-java-spring-ai.onrender.com
FRONTEND_URL=https://prayer-chat.com
```

**Embed code / backend URL:** Use `APP_BASE_URL=https://chatbot-java-spring-ai.onrender.com` so the “Get embed code” snippet uses the current backend. If you still have the old value (`chatbot-backend-4mp4.onrender.com`), set it to the URL above and redeploy; the backend also forces the new URL in generated embed snippets when the old one is configured.

**CRITICAL FOR OAUTH:** Ensure `CORS_ALLOWED_ORIGINS` includes all frontend domains where users will log in.
The backend CORS configuration reads this environment variable dynamically. Prefer a project-specific Vercel pattern (e.g. `https://prayer-chat*.vercel.app`) instead of `https://*.vercel.app`; see `docs/CORS_VERCEL_SECURITY.md`.

### 📊 Optional Configuration
```
DDL_AUTO=update
SHOW_SQL=false
HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
LOG_LEVEL=INFO
H2_CONSOLE_ENABLED=false
```

### 🔔 Security alerting (optional)
```
SECURITY_ALERT_WEBHOOK_URL=<slack-or-pagerduty-webhook-url>
```
When set, security events (failed login spikes, rate limit violations, payment failures, fraud risk) are sent to this webhook in addition to ERROR logging. Leave unset to only log.

## Mapping: .env → Render Environment Variables

| .env Variable | Render Environment Variable | application.yml Reference |
|--------------|----------------------------|---------------------------|
| `JWT_SECRET` | `JWT_SECRET` | `${JWT_SECRET}` |
| `ANTHROPIC_API_KEY` | `ANTHROPIC_API_KEY` | `${ANTHROPIC_API_KEY}` |
| `COHERE_API_KEY` | `COHERE_API_KEY` | `${COHERE_API_KEY}` |
| `GOOGLE_CLIENT_ID` | `GOOGLE_CLIENT_ID` | `${GOOGLE_CLIENT_ID:}` |
| `GOOGLE_CLIENT_SECRET` | `GOOGLE_CLIENT_SECRET` | `${GOOGLE_CLIENT_SECRET:}` |
| `STRIPE_SECRET_KEY` | `STRIPE_SECRET_KEY` | `${STRIPE_SECRET_KEY:}` |
| `STRIPE_WEBHOOK_SECRET` | `STRIPE_WEBHOOK_SECRET` | `${STRIPE_WEBHOOK_SECRET:}` |
| `DATABASE_URL` | `DATABASE_URL` | `${DATABASE_URL:jdbc:h2:mem:chatbotdb}` |
| `PORT` | `PORT` | `${PORT:8081}` |

## Hoe spring-dotenv werkt

### Lokale ontwikkeling:
1. spring-dotenv 4.0.0 laadt automatisch `.env` files
2. Variabelen worden beschikbaar gemaakt als Spring properties
3. `${VARIABLE_NAME}` in `application.yml` wordt vervangen

### Productie (Render):
1. Render stelt environment variables in als **system environment variables**
2. Spring Boot leest deze automatisch (zonder spring-dotenv nodig)
3. `${VARIABLE_NAME}` in `application.yml` wordt vervangen door system env vars

## Probleem: Waarom werkt het niet?

**Mogelijke oorzaken:**
1. ✅ Variabele namen komen overeen (JWT_SECRET = JWT_SECRET)
2. ❌ **JWT_SECRET had geen default waarde** - als deze ontbreekt, crasht de app
3. ❌ **ANTHROPIC_API_KEY had geen default** - als deze ontbreekt, werkt chat niet

## Oplossing: Graceful error handling

✅ **FIXED**: 
- `JWT_SECRET`: **VERPLICHT** - App crasht met duidelijke error als deze ontbreekt
- `ANTHROPIC_API_KEY`: Optioneel - App start, maar chat functionaliteit werkt niet zonder
- `COHERE_API_KEY`: Optioneel - App start, maar embeddings werken niet zonder
- `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`: Optioneel - OAuth2 login wordt alleen geconfigureerd als beide zijn ingesteld

## Belangrijk voor Render

**Zorg ervoor dat ALLE vereiste variabelen zijn ingesteld in Render dashboard:**

### ✅ Verplicht (zonder deze crasht de app):
- `JWT_SECRET` - **VERPLICHT** - App start niet zonder deze variabele (security requirement)

### ⚠️ Aanbevolen (app start zonder, maar functionaliteit werkt niet):
- `ANTHROPIC_API_KEY` - Voor chat functionaliteit (app start zonder, maar chat werkt niet)
- `COHERE_API_KEY` - Voor embeddings (app start zonder, maar embeddings werken niet)
- `DATABASE_URL` - Voor PostgreSQL (anders gebruikt H2 in-memory)
- `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` - **Voor OAuth login** (beide vereist, OAuth2 wordt alleen geconfigureerd als beide zijn ingesteld)
- `STRIPE_SECRET_KEY` - Voor betalingen

## Verificatie in Render

Na het instellen van environment variables in Render:
1. Deploy de applicatie
2. Check de logs voor errors over ontbrekende variabelen
3. Test `/actuator/health` endpoint
4. Test chat functionaliteit

## Spring-dotenv gedrag

- **Lokaal**: spring-dotenv 4.0.0 laadt automatisch `.env` files
- **Render**: Environment variables worden direct als system env vars ingesteld
- **Spring Boot**: Leest automatisch system environment variables (zonder spring-dotenv nodig)
- **Conclusie**: In Render werkt het ZONDER .env file - alleen system env vars nodig!

