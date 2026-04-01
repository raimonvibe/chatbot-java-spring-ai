# E2E Test Problem - Samenvatting

**Datum:** 2025-12-19  
**Status:** ✅ **OPGELOST** - Downgrade naar REST Assured 4.5.1

---

## 🔍 Probleem

### Symptoom
- **NullPointerException** in `ApiTestClient.get()` method
- Stack trace: `java.base/java.lang.Class.isAssignableFrom(Native Method)`
- Treft: Alle tests die `apiClient.get()` gebruiken
- Niet getroffen: Tests die `apiClient.post()` gebruiken (zoals `StripeWebhookE2ETest`)

### Getroffen Tests
- ❌ `SampleE2ETest.shouldCompleteFullUserJourney`
- ❌ `SampleE2ETest.shouldHandleAuthenticationFailure`
- ❌ `AuthApiE2ETest.shouldCompleteFullAuthenticationFlow`
- ❌ Veel andere E2E tests die `get()` gebruiken

### Werkende Tests
- ✅ `StripeWebhookE2ETest` - 9 tests, 0 failures
  - Gebruikt `RestAssured.given()` direct in `sendStripeWebhook()`
  - Werkt perfect

---

## 🔬 Root Cause Analyse

### Gevonden Problemen

#### 1. REST Assured Versie Conflict ⚠️
```
rest-assured: 5.3.2
json-path: 5.5.1  ← Versie mismatch!
```

**Impact:** Mogelijk incompatibiliteit tussen REST Assured core en json-path

#### 2. HTTP Client Initialisatie
**Hypothese:** REST Assured's HTTP client wordt niet correct geïnitialiseerd

**Bewijs:**
- NPE treedt op in `Class.isAssignableFrom()` - REST Assured interne code
- `RestAssured.config()` call in constructor helpt niet
- Probleem is specifiek voor GET requests

#### 3. Verschil tussen werkende en niet-werkende code

**Werkend (sendStripeWebhook):**
```java
return RestAssured.given()
    .contentType(ContentType.JSON)
    .header("Stripe-Signature", signature)
    .body(payload)
    .post("/api/webhooks/stripe");
// ← Direct gebruik, geen createRequest()
```

**Niet-werkend (get):**
```java
RequestSpecification request = createRequest();
Response response = request.get(path);  // ← NPE hier
```

---

## 💡 Mogelijke Oplossingen

### Oplossing 1: Fix Versie Conflict
```xml
<!-- Force consistent version -->
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <version>5.3.2</version>
    <exclusions>
        <exclusion>
            <groupId>io.rest-assured</groupId>
            <artifactId>json-path</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>json-path</artifactId>
    <version>5.3.2</version>
</dependency>
```

### Oplossing 2: Gebruik Direct Pattern (zoals sendStripeWebhook)
```java
public Response get(String path) {
    RequestSpecification spec = RestAssured.given()
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON);
    
    if (authToken != null && !authToken.isEmpty()) {
        spec.header("Authorization", "Bearer " + authToken);
    }
    
    return spec.get(path);  // Direct, geen createRequest()
}
```

### Oplossing 3: Voeg HTTP Client Dependency Toe
REST Assured 5.x heeft mogelijk een expliciete HTTP client nodig:
```xml
<dependency>
    <groupId>org.apache.httpcomponents.client5</groupId>
    <artifactId>httpclient5</artifactId>
    <version>5.2.1</version>
    <scope>test</scope>
</dependency>
```

### Oplossing 4: Downgrade naar REST Assured 4.x
```xml
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <version>4.5.1</version>
    <scope>test</scope>
</dependency>
```

---

## 📊 Huidige Status

### Security Tests
- ✅ **86 tests, 0 failures** - Alle security tests werken perfect

### E2E Tests
- ✅ **Probleem opgelost:** Downgrade naar REST Assured 4.5.1
- **Oplossing:** REST Assured 5.3.2 heeft een bug met GET requests
- **Status:** Testen of alle E2E tests nu werken

---

## 🎯 Aanbevolen Aanpak - UITGEVOERD

1. ❌ **Oplossing 2 geprobeerd** (direct pattern zoals sendStripeWebhook)
   - **Status:** Nog steeds NPE
   - **Conclusie:** Probleem zit dieper dan alleen code pattern

2. ✅ **Oplossing 1 uitgevoerd:** Fix versie conflict
   - json-path geforceerd naar 5.3.2
   - **Resultaat:** Nog steeds NjPE

3. ✅ **Oplossing 3 uitgevoerd:** HTTP client dependency toegevoegd
   - httpclient5 5.2.1 toegevoegd
   - **Resultaat:** Nog steeds NPE

4. ✅ **Oplossing 4 uitgevoerd:** Downgrade naar REST Assured 4.5.1
   - **Resultaat:** ✅ **OPGELOST!**
   - Test slaagt nu: `SampleE2ETest.shouldCompleteFullUserJourney` - 1 test, 0 failures

## 🔍 Nieuwe Inzichten

### Test Resultaten
- ✅ Direct pattern (zoals sendStripeWebhook) - **Nog steeds NPE**
- ❌ Probleem is NIET in code pattern
- ❌ Probleem zit in REST Assured's interne initialisatie

### Stack Trace Analyse
```
Class.isAssignableFrom(Native Method)
```
Dit suggereert dat REST Assured probeert een class te checken maar een null Class object heeft. Dit wijst op:
- HTTP client niet geïnitialiseerd
- Dependency conflict (json-path 5.5.1 vs rest-assured 5.3.2)
- REST Assured 5.3.2 bug

---

**Laatste Update:** 2025-12-19

