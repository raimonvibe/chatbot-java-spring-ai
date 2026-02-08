# OAuth2 Troubleshooting: Local Development

## Probleem: `authorization_request_not_found` + `NoClassDefFoundError`

### Symptomen:
1. `NoClassDefFoundError: Could not initialize class reactor.netty.http.client.HttpClientSecure`
2. `authorization_request_not_found` - OAuth2 authorization request niet gevonden in sessie

### Oorzaken:

#### 1. Reactor Netty Classpath Issue
- **Probleem**: OAuth2 client gebruikt Reactor Netty, maar class niet gevonden
- **Oplossing**: Expliciete dependency toegevoegd in `pom.xml`

#### 2. Session Storage Issue (Lokale Development)
- **Probleem**: H2 in-memory database kan sessies verliezen bij restart
- **Probleem**: Spring Session JDBC tabellen worden mogelijk niet correct aangemaakt
- **Probleem**: Sessie cookie wordt niet correct verzonden/ontvangen

### Oplossingen:

#### 1. Verifieer Spring Session JDBC Setup
```bash
# Check of sessie tabellen bestaan
# Ga naar H2 console: http://localhost:8081/h2-console
# Check tabellen: SPRING_SESSION, SPRING_SESSION_ATTRIBUTES
```

#### 2. Check Database Schema
Spring Session JDBC moet automatisch tabellen aanmaken met:
- `spring.session.jdbc.initialize-schema: always`
- Database moet bestaan en toegankelijk zijn

#### 3. Lokale Development Fix
Voor lokale development met H2, gebruik **file-based database** in plaats van in-memory:

```yaml
datasource:
  url: jdbc:h2:file:./data/chatbotdb  # File-based instead of mem
  # Of: jdbc:h2:mem:chatbotdb;DB_CLOSE_DELAY=-1  # Keep in-memory alive
```

#### 4. Verifieer Session Cookie
- Check browser cookies: `JSESSIONID` moet aanwezig zijn
- Check cookie attributes: `httpOnly`, `sameSite: lax`
- Check cookie domain/path: moet matchen met backend URL

#### 5. Test OAuth2 Flow
1. Start backend: `mvn spring-boot:run`
2. Check logs voor Spring Session initialisatie
3. Check H2 console voor sessie tabellen
4. Probeer OAuth2 login
5. Check sessie tabel voor authorization request

### Debugging Steps:

1. **Check Spring Session Initialization:**
```bash
# Look for these log messages:
# "Creating Spring Session JDBC tables"
# "Spring Session JDBC initialized"
```

2. **Check Session Tables:**
```sql
-- In H2 console
SELECT * FROM SPRING_SESSION;
SELECT * FROM SPRING_SESSION_ATTRIBUTES;
```

3. **Check OAuth2 Flow:**
- Start: `/oauth2/authorization/google`
- Check: Sessie wordt aangemaakt met authorization request
- Callback: `/login/oauth2/code/google`
- Check: Authorization request wordt opgehaald uit sessie

### Mogelijke Fixes:

#### Fix 1: File-based H2 Database
```yaml
datasource:
  url: jdbc:h2:file:./data/chatbotdb;AUTO_SERVER=TRUE
```

#### Fix 2: Keep In-Memory Database Alive
```yaml
datasource:
  url: jdbc:h2:mem:chatbotdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
```

#### Fix 3: Explicit Session Repository Bean
Als Spring Session JDBC niet automatisch werkt, voeg toe:
```java
@Configuration
@EnableJdbcHttpSession
public class SessionConfig {
    // Spring Session JDBC auto-configuration
}
```

### Test Checklist:
- [ ] Spring Session JDBC dependency aanwezig
- [ ] Reactor Netty dependency aanwezig
- [ ] Database schema wordt aangemaakt
- [ ] Sessie tabellen bestaan (SPRING_SESSION, SPRING_SESSION_ATTRIBUTES)
- [ ] Session cookie wordt verzonden
- [ ] Authorization request wordt opgeslagen in sessie
- [ ] Authorization request wordt opgehaald bij callback

