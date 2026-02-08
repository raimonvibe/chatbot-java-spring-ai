# PostgreSQL + Spring Session JDBC Setup voor Render

## Configuratie Status

### ✅ Correct Geconfigureerd:

1. **Spring Session JDBC**
   ```yaml
   spring:
     session:
       store-type: jdbc
       jdbc:
         initialize-schema: always  # Auto-maakt tabellen aan in PostgreSQL
   ```

2. **PostgreSQL Database**
   - Render stelt automatisch `DATABASE_URL` in
   - `DATABASE_DRIVER=org.postgresql.Driver` is ingesteld
   - `HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect` is ingesteld

3. **Session Cookie Security**
   - `secure: true` - Vereist voor HTTPS (Render gebruikt HTTPS)
   - `httpOnly: true` - Beschermt tegen XSS
   - `sameSite: lax` - Staat OAuth2 redirects toe

## Verificatie op Render

### 1. Check Session Tabellen in PostgreSQL

Spring Session JDBC maakt automatisch deze tabellen aan:
- `SPRING_SESSION` - Hoofd sessie tabel
- `SPRING_SESSION_ATTRIBUTES` - Sessie attributen

**Verificatie via Render Dashboard:**
1. Ga naar je PostgreSQL database in Render
2. Klik op "Connect" → "psql"
3. Run:
   ```sql
   \dt  -- List alle tabellen
   SELECT * FROM SPRING_SESSION LIMIT 5;
   SELECT * FROM SPRING_SESSION_ATTRIBUTES LIMIT 5;
   ```

### 2. Check Application Logs

Zoek naar deze log berichten bij startup:
```
Creating Spring Session JDBC tables
Spring Session JDBC initialized
```

### 3. Check OAuth2 Flow

**Verwachte flow:**
1. Gebruiker klikt "Login with Google"
2. Redirect naar `/oauth2/authorization/google`
3. Spring Security maakt sessie aan en slaat authorization request op in `SPRING_SESSION`
4. Google redirect terug naar `/login/oauth2/code/google`
5. Spring Security haalt authorization request op uit `SPRING_SESSION`
6. Token exchange met Google
7. Gebruiker wordt ingelogd

## Troubleshooting

### Probleem: `authorization_request_not_found`

**Mogelijke oorzaken:**

1. **Session tabellen niet aangemaakt**
   - Check: `SELECT * FROM SPRING_SESSION;`
   - Fix: Zorg dat `initialize-schema: always` is ingesteld

2. **Session cookie niet verzonden**
   - Check: Browser DevTools → Application → Cookies
   - Check: `JSESSIONID` cookie moet aanwezig zijn
   - Check: Cookie moet `Secure` en `HttpOnly` zijn

3. **Session cookie domain/path mismatch**
   - Check: Cookie domain moet matchen met backend URL
   - Check: Cookie path moet `/` zijn

4. **HTTPS/HTTP mismatch**
   - Render gebruikt HTTPS
   - Cookie `secure: true` is vereist
   - Check: `server.servlet.session.cookie.secure: true`

### Debugging Steps:

1. **Check Session Storage:**
   ```sql
   -- In PostgreSQL (via Render psql)
   SELECT session_id, creation_time, last_access_time, max_inactive_interval 
   FROM SPRING_SESSION 
   ORDER BY creation_time DESC 
   LIMIT 10;
   ```

2. **Check Session Attributes:**
   ```sql
   SELECT sa.session_id, sa.attribute_name, sa.attribute_bytes
   FROM SPRING_SESSION_ATTRIBUTES sa
   JOIN SPRING_SESSION s ON sa.session_id = s.session_id
   ORDER BY s.creation_time DESC
   LIMIT 10;
   ```

3. **Check Application Logs:**
   - Zoek naar "OAuth2 authentication failed"
   - Check session ID in logs
   - Check of sessie bestaat in database

## Environment Variables op Render

Zorg dat deze zijn ingesteld:
- ✅ `DATABASE_URL` - Automatisch ingesteld door Render
- ✅ `DATABASE_DRIVER=org.postgresql.Driver`
- ✅ `HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect`
- ✅ `DDL_AUTO=update`
- ✅ `GOOGLE_CLIENT_ID` - Je Google OAuth Client ID
- ✅ `GOOGLE_CLIENT_SECRET` - Je Google OAuth Client Secret

## Aanbevelingen

1. **Monitor Session Tabellen:**
   - Check regelmatig of sessies worden opgeslagen
   - Check of verlopen sessies worden opgeruimd

2. **Session Cleanup:**
   - `cleanup-cron: "0 0 * * * *"` ruimt verlopen sessies op
   - Dit voorkomt database groei

3. **Security:**
   - ✅ `secure: true` voor HTTPS
   - ✅ `httpOnly: true` voor XSS bescherming
   - ✅ `sameSite: lax` voor CSRF bescherming + OAuth2

## Conclusie

De configuratie is correct voor PostgreSQL op Render. Als je nog steeds `authorization_request_not_found` krijgt:

1. ✅ Check of session tabellen bestaan in PostgreSQL
2. ✅ Check of session cookie wordt verzonden (browser DevTools)
3. ✅ Check application logs voor meer details
4. ✅ Verifieer dat `secure: true` is ingesteld voor HTTPS

