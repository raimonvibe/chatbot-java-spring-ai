# E2E Test Problem Analysis

**Datum:** 2025-12-19  
**Status:** In onderzoek

---

## 🔍 Probleem Beschrijving

### Symptoom
- **NullPointerException** in `ApiTestClient.get()` method
- Fout treedt op bij: `request.get(path)` 
- Test: `SampleE2ETest.shouldCompleteFullUserJourney`
- Error: `NullPointerException executing GET request to /api/chatbots`

### Context
- baseUrl: `http://localhost:39695` (correct)
- port: `39695` (correct)
- authToken: `present` (correct)
- Request specification wordt correct aangemaakt
- NPE treedt op IN `request.get(path)` - niet in onze code

---

## 🔬 Root Cause Analyse

### Mogelijke Oorzaken

#### 1. REST Assured HTTP Client Initialisatie
**Hypothese:** REST Assured's HTTP client is niet correct geïnitialiseerd na `RestAssured.reset()`

**Bewijs:**
- `RestAssured.reset()` wordt aangeroepen in constructor
- NPE treedt op in REST Assured's interne code, niet in onze code
- Andere methoden (POST) werken mogelijk wel

**Test:**
- ✅ `StripeWebhookE2ETest` werkt (9 tests, 0 failures)
- ❌ `SampleE2ETest.shouldCompleteFullUserJourney` faalt

#### 2. Conflicterende baseUri/port Configuratie
**Hypothese:** Zowel statische configuratie als per-request configuratie veroorzaken conflict

**Huidige Code:**
```java
// Constructor
RestAssured.baseURI = this.baseUrl;
RestAssured.port = port;

// createRequest()
RequestSpecification spec = RestAssured.given()
    .baseUri(baseUrl)  // ← Mogelijk conflict
    .port(extractPort())  // ← Mogelijk conflict
```

**Probleem:** Mogelijk dat REST Assured verward raakt door dubbele configuratie

#### 3. REST Assured Versie Issue
**Hypothese:** Bepaalde REST Assured versies hebben bekende bugs met baseUri/port

**Huidige Versie:** (moet worden gecontroleerd in pom.xml)

---

## 🧪 Test Resultaten

### Werkende Tests
- ✅ `StripeWebhookE2ETest` - 9 tests, 0 failures
  - Gebruikt `RestAssured.given()` direct (zonder baseUri/port in createRequest)
  - Werkt correct

### Failing Tests
- ❌ `SampleE2ETest.shouldCompleteFullUserJourney` - NPE in `get()`
- ❌ `SampleE2ETest.shouldHandleAuthenticationFailure` - NPE in `get()`

### Verschil Analyse
**StripeWebhookE2ETest:**
```java
// sendStripeWebhook gebruikt:
RestAssured.given()
    .contentType(ContentType.JSON)
    .header("Stripe-Signature", signature)
    .body(payload)
    .post("/api/webhooks/stripe");
// ← Geen baseUri/port in request, gebruikt statische config
```

**SampleE2ETest:**
```java
// getChatbots() gebruikt:
apiClient.getChatbots() 
  → get("/api/chatbots")
    → createRequest().get(path)
      ← baseUri/port worden gebruikt in createRequest()
```

---

## 💡 Mogelijke Oplossingen

### Oplossing 1: Gebruik Volledige URL (Geprobeerd)
```java
String fullUrl = baseUrl + path;
request.get(fullUrl);
```
**Status:** ❌ Nog steeds NPE

### Oplossing 2: Verwijder baseUri/port uit createRequest()
```java
// Alleen statische config gebruiken
RequestSpecification spec = RestAssured.given()
    .contentType(ContentType.JSON)
    .accept(ContentType.JSON);
// baseUri/port komen van statische config
```
**Status:** ⏳ Te testen

### Oplossing 3: Verwijder RestAssured.reset()
```java
// Constructor zonder reset
public ApiTestClient(int port) {
    this.baseUrl = "http://localhost:" + port;
    // RestAssured.reset(); // ← Verwijderen
    RestAssured.baseURI = this.baseUrl;
    RestAssured.port = port;
}
```
**Status:** ⏳ Te testen

### Oplossing 4: Gebruik RequestSpecBuilder
```java
RequestSpecification spec = new RequestSpecBuilder()
    .setBaseUri(baseUrl)
    .setPort(port)
    .setContentType(ContentType.JSON)
    .build();
```
**Status:** ⏳ Te testen

### Oplossing 5: Lazy Initialisatie van HTTP Client
```java
// Force REST Assured om HTTP client te initialiseren
RestAssured.given().get(baseUrl + "/api/health");
// Dan pas echte requests
```
**Status:** ⏳ Te testen

---

## 📊 Huidige Code Structuur

### ApiTestClient Constructor
```java
public ApiTestClient(int port) {
    this.baseUrl = "http://localhost:" + port;
    RestAssured.reset();  // ← Mogelijk probleem
    RestAssured.baseURI = this.baseUrl;
    RestAssured.port = port;
    RestAssured.urlEncodingEnabled = false;
}
```

### createRequest() Method
```java
private RequestSpecification createRequest() {
    // Ensure static config is set
    if (RestAssured.baseURI == null || !RestAssured.baseURI.equals(baseUrl)) {
        RestAssured.baseURI = baseUrl;
    }
    if (RestAssured.port != extractPort()) {
        RestAssured.port = extractPort();
    }
    
    RequestSpecification spec = RestAssured.given()
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON);
    // ← Geen baseUri/port meer hier
    
    if (authToken != null && !authToken.isEmpty()) {
        spec.header("Authorization", "Bearer " + authToken);
    }
    return spec;
}
```

### get() Method (Huidige Versie)
```java
public Response get(String path) {
    try {
        String fullUrl = baseUrl + (path.startsWith("/") ? path : "/" + path);
        RequestSpecification request = RestAssured.given()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON);
        if (authToken != null && !authToken.isEmpty()) {
            request.header("Authorization", "Bearer " + authToken);
        }
        Response response = request.get(fullUrl);  // ← NPE hier
        return response;
    } catch (NullPointerException e) {
        // Error handling
    }
}
```

---

## 🎯 Volgende Stappen

1. ✅ **Verwijder RestAssured.reset()** uit constructor - GEDAAN
2. ✅ **Gebruik alleen statische configuratie** - GEDAAN
3. ⚠️ **Probleem blijft bestaan** - NPE in `Class.isAssignableFrom()`
4. **Mogelijke oplossing:** HTTP client dependency toevoegen
5. **Mogelijke oplossing:** REST Assured versie updaten/downgraden
6. **Mogelijke oplossing:** RequestSpecBuilder gebruiken

## 🔍 Nieuwe Inzichten

### Stack Trace Analyse
```
NullPointerException executing GET request to /api/chatbots
Stack: java.base/java.lang.Class.isAssignableFrom(Native Method)
```

Dit suggereert dat REST Assured intern een null Class object heeft. Dit kan betekenen:
- HTTP client is niet geïnitialiseerd
- Er ontbreekt een dependency (Apache HttpClient?)
- REST Assured 5.3.2 heeft een bug

### REST Assured Versie
- **Huidige versie:** 5.3.2
- **Mogelijk probleem:** REST Assured 5.x heeft breaking changes t.o.v. 4.x
- **Oplossing:** Mogelijk expliciete HTTP client dependency nodig

---

## 📝 Notities

- `StripeWebhookE2ETest` werkt perfect - gebruik als referentie
- NPE komt uit REST Assured's interne code, niet uit onze code
- Probleem is specifiek voor GET requests (POST werkt mogelijk wel)
- baseUrl en port zijn correct geconfigureerd
- authToken is aanwezig

---

**Laatste Update:** 2025-12-19

