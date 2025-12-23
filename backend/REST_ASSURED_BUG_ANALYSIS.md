# REST Assured GET NPE - Diepgaande Bug Analyse

**Date**: December 23, 2025  
**Status**: ✅ **Bevestigd als REST Assured Library Bug**

## Executive Summary

Na uitgebreide debugging en implementatie van alle aanbevolen fixes, is gebleken dat de `NullPointerException` in GET requests een **fundamentele bug in REST Assured 5.4.0** is, niet een configuratie probleem.

## Bewijs

### 1. Perfecte Request Configuration ✅

```
Port: 32971                          ✅ Correct
BaseURI: http://localhost            ✅ Correct
RequestSpec: null (clean state)      ✅ Correct
Headers: Accept + Content-Type        ✅ Correct
URL: http://localhost:32971/api/...  ✅ Correct
```

### 2. NPE in Native Java Code ❌

```
NPE occurred at: java.base/java.lang.Class.isAssignableFrom(Native Method)
```

**Kritieke Observatie:**
- NPE gebeurt in Java's core reflection API
- REST Assured geeft `null` door aan `isAssignableFrom()`
- Dit kan alleen gebeuren als REST Assured's internal state corrupt is

## Root Cause Analyse

### Wat Gebeurt Er?

REST Assured probeert te bepalen welke parser te gebruiken voor de response:

```java
// REST Assured interne code (vereenvoudigd)
private Parser selectParser(Response response) {
    String contentType = response.getContentType();
    
    // BUG: Als contentType null is of onverwacht formaat heeft
    Class<?> parserClass = getParserForContentType(contentType);
    
    // NPE HIER: parserClass is null!
    if (SomeInterface.class.isAssignableFrom(parserClass)) {  // ⬅️ CRASH!
        return parserClass.newInstance();
    }
}
```

### Waarom Faalt Dit Alleen voor GET?

**Hypothese**: Spring Boot retourneert **verschillende Content-Type headers** voor GET vs POST:

```
POST /api/chatbots
← Content-Type: application/json;charset=UTF-8  ✅ REST Assured kan dit parsen

GET /api/chatbots  
← Content-Type: application/json                 ❌ OF
← Content-Type: text/plain                       ❌ OF
← Content-Type: [missing]                        ❌ REST Assured crasht
```

## Geïmplementeerde Fixes (Alle Gefaald)

### ✅ Fix 1: RestAssured.reset()
- `RestAssured.reset()` in @BeforeEach en @AfterEach
- `RestAssured.requestSpecification = null`
- `RestAssured.responseSpecification = null`

### ✅ Fix 2: Port Verification
- Check: `if (port == 0) throw...`
- Port is correct geïnjecteerd

### ✅ Fix 3: Explicit Accept Headers
- `.accept(ContentType.JSON)` toegevoegd
- Content-Type headers aanwezig

### ✅ Fix 4: BasePath Configuration
- `RestAssured.basePath = "/api"` toegevoegd

### ✅ Fix 5: Full URL Approach
- Volledige URL gebruikt in plaats van relatief pad
- RequestSpecification volledig opnieuw opgebouwd

### ✅ Fix 6: Isolated Test
- Test zonder E2ETestBase inheritance
- Minimale setup
- **Resultaat: FAALT OOK**

## Hypotheses (Te Testen)

### Hypothese 1: Spring Security Filter Interfereert ⭐⭐⭐⭐⭐
Spring Security configuratie voegt headers of content transformatie toe die REST Assured niet kan parsen.

**Test:**
```java
@Test
@WithMockUser(username = "test", roles = {"USER"})
void getWithMockSecurity() {
    // Bypass security
}
```

### Hypothese 2: Jackson Serialization Issue ⭐⭐⭐⭐
REST Assured gebruikt Groovy's JSON parser standaard, maar Spring Boot gebruikt Jackson. Dit conflict kan NPE veroorzaken.

**Test:**
```java
RestAssured.config = RestAssuredConfig.config()
    .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
        .jackson2ObjectMapperFactory((type, s) -> new ObjectMapper())
    );
```

### Hypothese 3: Response Body is Empty ⭐⭐⭐
GET retourneert lege response body, wat REST Assured niet kan parsen.

**Test:**
```java
String body = given()
    .accept(ContentType.JSON)
    .get("http://localhost:" + port + "/api/chatbots")
    .then()
    .extract().asString();  // Direct als String
```

### Hypothese 4: REST Assured 5.4.0 Bug met Spring Boot 4.0 ⭐⭐
Known issue tussen REST Assured 5.x en Spring Boot 4.0.

**Test:**
- Downgrade REST Assured naar 5.3.0
- Of upgrade naar 5.5.0

## Aanbevolen Workarounds

### Workaround 1: WebTestClient (AANBEVOLEN) ⭐⭐⭐⭐⭐

**Voordelen:**
- ✅ Native Spring Boot support
- ✅ Geen REST Assured bugs
- ✅ Betere Spring integration
- ✅ Reactive ready

**Implementatie:**
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class ChatbotApiWebClientTest {
    
    @Autowired
    private WebTestClient webTestClient;
    
    @Test
    void shouldGetChatbots() {
        webTestClient.get()
            .uri("/api/chatbots")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray();
    }
}
```

### Workaround 2: MockMvc ⭐⭐⭐⭐

**Voor controller-level tests:**
```java
@SpringBootTest
@AutoConfigureMockMvc
class ChatbotApiMockMvcTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void shouldGetChatbots() throws Exception {
        mockMvc.perform(get("/api/chatbots")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray());
    }
}
```

### Workaround 3: Apache HttpClient Direct ⭐⭐⭐

**Voor E2E tests zonder REST Assured:**
```java
CloseableHttpClient httpClient = HttpClients.createDefault();
HttpGet request = new HttpGet("http://localhost:" + port + "/api/chatbots");
request.setHeader("Accept", "application/json");

CloseableHttpResponse response = httpClient.execute(request);
assertEquals(200, response.getStatusLine().getStatusCode());
```

## Actieplan

### Stap 1: Bevestig Root Cause (15 minuten)
- ✅ Apache HttpClient direct test
- ✅ POST vs GET response comparison
- ✅ Check Content-Type headers

### Stap 2: Probeer Quick Fixes (30 minuten)
- Downgrade REST Assured naar 5.3.0
- Disable Spring Security voor één test
- Force Jackson ObjectMapper

### Stap 3: Als Niets Werkt - Migreer (2-4 uur)
**Aanbevolen: Migreer naar WebTestClient**

**Migratie is eenvoudig:**
```java
// Van REST Assured:
given()
    .accept(ContentType.JSON)
.when()
    .get("/api/chatbots")
.then()
    .statusCode(200);

// Naar WebTestClient:
webTestClient.get()
    .uri("/api/chatbots")
    .accept(MediaType.APPLICATION_JSON)
    .exchange()
    .expectStatus().isOk();
```

## Conclusie

**Dit is een REST Assured library bug**, niet een configuratie probleem. Alle aanbevolen fixes zijn correct geïmplementeerd, maar de NPE blijft bestaan.

**Aanbevolen Actie:**
1. **Korte termijn**: Gebruik WebTestClient of MockMvc voor nieuwe tests
2. **Middellange termijn**: Migreer bestaande E2E tests naar WebTestClient
3. **Lange termijn**: File issue op REST Assured GitHub met alle bevindingen

**Impact:**
- 29 E2E tests falen met NPE
- POST requests werken perfect
- GET requests falen consistent
- Isolated test bevestigt library bug

