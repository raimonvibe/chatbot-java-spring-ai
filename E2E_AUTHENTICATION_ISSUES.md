# E2E Test Authentication Issues

**Datum:** 2025-12-19  
**Status:** 🔴 In onderzoek

---

## 🔍 Probleem

### Symptoom
- **403 Forbidden** errors in `AuthApiE2ETest` en andere E2E tests
- Tests die `apiClient.getChatbots()` aanroepen krijgen 403 in plaats van 200
- `SampleE2ETest` werkt wel (9 tests, 0 failures)

### Getroffen Tests
- ❌ `AuthApiE2ETest.shouldCompleteFullAuthenticationFlow` - 403
- ❌ `AuthApiE2ETest.shouldValidateJWTTokens` - 403
- ❌ `AuthApiE2ETest.shouldCreateGoogleOAuth2Users` - 403
- ❌ `AuthApiE2ETest.shouldPersistTokenAcrossRequests` - 403
- ❌ `ChatbotApiE2ETest.shouldCompleteFullCRUDLifecycle` - 401
- ❌ Veel andere E2E tests

### Werkende Tests
- ✅ `SampleE2ETest` - 9 tests, 0 failures
  - Doet eerst POST (createChatbot), dan GET (getChatbots)
  - Werkt perfect

---

## 🔬 Analyse

### Security Configuratie

**TestSecurityConfig.java:**
```java
.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/chatbots").permitAll()
.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/chatbots/**").permitAll()
// Protect write operations (POST, PUT, DELETE)
.requestMatchers("/api/chatbots/**").authenticated()
```

**Probleem:** 
- GET requests naar `/api/chatbots` zouden `permitAll()` moeten zijn
- Maar tests krijgen 403, wat betekent dat authenticatie wordt vereist
- Dit suggereert dat de security rules niet correct worden geëvalueerd

### Verschil tussen werkende en niet-werkende tests

**Werkend (SampleE2ETest):**
```java
createOAuth2User(email);
createActiveSubscriptionForUser(email);
apiClient.createChatbot(...);  // POST - vereist authenticatie
apiClient.getChatbots();        // GET - zou permitAll() moeten zijn
```

**Niet-werkend (AuthApiE2ETest):**
```java
createOAuth2User(email);
apiClient.getChatbots();        // GET - krijgt 403
```

**Hypothese:** 
- Mogelijk dat de JWT filter niet correct werkt wanneer er geen eerdere authenticatie is geweest
- Of de security rules worden niet correct geëvalueerd voor GET requests

---

## 💡 Mogelijke Oorzaken

### 1. Security Rules Volgorde
**Probleem:** Spring Security evalueert rules in volgorde. Misschien wordt `/api/chatbots/**` geëvalueerd vóór de GET-specifieke rule.

**Oplossing:** Zet GET-specifieke rules VOOR de algemene `/api/chatbots/**` rule.

### 2. JWT Filter Configuratie
**Probleem:** JWT filter wordt mogelijk niet correct toegepast in test omgeving.

**Bewijs:**
- `TestSecurityConfig` heeft `@Autowired(required = false)` voor `jwtAuthenticationFilter`
- Als de filter null is, wordt authenticatie niet uitgevoerd

**Oplossing:** Zorg dat JWT filter altijd beschikbaar is in E2E tests.

### 3. Request Matching
**Probleem:** `/api/chatbots` (zonder trailing slash) matcht mogelijk niet met `/api/chatbots/**`.

**Oplossing:** Voeg expliciete rule toe voor `/api/chatbots` (zonder trailing slash).

---

## 🎯 Volgende Stappen

1. **Fix Security Rules Volgorde**
   - Zet GET-specifieke rules VOOR algemene rules
   - Voeg expliciete rule toe voor `/api/chatbots` (zonder trailing slash)

2. **Verifieer JWT Filter**
   - Zorg dat JWT filter altijd beschikbaar is in E2E tests
   - Test of JWT filter correct tokens valideert

3. **Debug Logging**
   - Voeg debug logging toe aan JWT filter
   - Log welke security rules worden geëvalueerd

4. **Test Isolatie**
   - Zorg dat elke test een schone state heeft
   - Mogelijk dat eerdere tests de security context beïnvloeden

---

**Laatste Update:** 2025-12-19

