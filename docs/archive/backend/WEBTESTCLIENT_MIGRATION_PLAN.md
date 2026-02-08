# WebTestClient Migratieplan - REST Assured → WebTestClient

**Date**: December 23, 2025  
**Status**: Planning  
**Reason**: REST Assured GET NPE bug (29 tests failing)

## Executive Summary

Migreer alle E2E tests van REST Assured naar Spring WebTestClient om de GET request NPE bug te omzeilen en betere Spring Boot integratie te krijgen.

## Voordelen van WebTestClient

✅ **Native Spring Boot Support** - Geen externe dependencies  
✅ **Geen REST Assured Bugs** - Geen NPE issues  
✅ **Betere Spring Integration** - Directe integratie met Spring Security, filters, etc.  
✅ **Reactive Ready** - Ondersteunt reactive programming  
✅ **Type Safety** - Betere type checking en IDE support  
✅ **Consistent API** - Zelfde patterns als Spring WebFlux  

## Huidige Situatie

### Aantal Tests Te Migreren

- **8 E2E Test Classes**:
  1. `AuthApiE2ETest` (7 tests met GET requests)
  2. `ChatbotApiE2ETest` (4 tests met GET requests)
  3. `ErrorHandlingE2ETest` (3 tests met GET requests)
  4. `SampleE2ETest` (1 test met GET request)
  5. `SecurityE2ETest` (5 tests met GET requests)
  6. `SubscriptionApiE2ETest` (6 tests met GET requests)
  7. `UserJourneyE2ETest` (3 tests met GET requests)
  8. `ChatApiE2ETest` (mogelijk ook GET requests)

- **Totaal**: ~29 tests met GET requests die falen
- **Totaal E2E Tests**: ~100+ tests (inclusief POST requests die werken)

### Huidige Code Structuur

```java
// ApiTestClient.java - Wrapper rond REST Assured
public class ApiTestClient {
    public Response get(String path);
    public Response post(String path, Object body);
    public Response put(String path, Object body);
    public Response delete(String path);
    // ... etc
}

// E2E Tests gebruiken ApiTestClient
Response response = apiClient.getChatbots();
response.then()
    .statusCode(200)
    .body("$", isA(List.class));
```

## Migratie Strategie

### Optie A: Incrementele Migratie (AANBEVOLEN) ⭐⭐⭐⭐⭐

**Voordelen:**
- ✅ Tests blijven werken tijdens migratie
- ✅ Kan per test class migreren
- ✅ Makkelijker te testen en debuggen
- ✅ Minder risico

**Nadelen:**
- ⚠️ Twee systemen naast elkaar (tijdelijk)
- ⚠️ Iets meer complexiteit tijdens transitie

**Aanpak:**
1. Maak nieuwe `WebTestClientApiTestClient`
2. Migreer één test class per keer
3. Test elke migratie
4. Verwijder REST Assured na volledige migratie

### Optie B: Volledige Migratie (Sneller maar Risicovoller)

**Voordelen:**
- ✅ Sneller
- ✅ Geen dubbele code

**Nadelen:**
- ❌ Alle tests falen tijdens migratie
- ❌ Moeilijker te debuggen
- ❌ Hoger risico

## Stap-voor-Stap Implementatieplan

### Fase 1: Setup en Dependencies (30 minuten)

#### Stap 1.1: Voeg WebTestClient Dependency Toe

```xml
<!-- In pom.xml - al aanwezig via spring-boot-starter-webflux -->
<!-- Check of spring-boot-starter-webflux al in dependencies staat -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
    <scope>test</scope>
</dependency>
```

#### Stap 1.2: Verifieer Spring Boot Version

- ✅ Spring Boot 4.0.0 - WebTestClient is standaard beschikbaar
- ✅ Geen extra dependencies nodig

### Fase 2: Nieuwe WebTestClient ApiTestClient (2-3 uur)

#### Stap 2.1: Maak WebTestClientApiTestClient

**Nieuwe File**: `backend/src/test/java/com/prayer_chat/chatbot/helpers/WebTestClientApiTestClient.java`

```java
package com.prayer_chat.chatbot.helpers;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import java.util.Map;

/**
 * API Test Client using WebTestClient instead of REST Assured
 * Provides same interface as ApiTestClient but uses WebTestClient internally
 */
public class WebTestClientApiTestClient {
    
    private final WebTestClient webTestClient;
    private String authToken;
    
    public WebTestClientApiTestClient(WebTestClient webTestClient) {
        this.webTestClient = webTestClient;
    }
    
    /**
     * Set authentication token for subsequent requests
     */
    public WebTestClientApiTestClient withAuth(String token) {
        this.authToken = token;
        return this;
    }
    
    /**
     * Clear authentication token
     */
    public WebTestClientApiTestClient clearAuth() {
        this.authToken = null;
        return this;
    }
    
    /**
     * GET request
     */
    public WebTestClient.ResponseSpec get(String path) {
        WebTestClient.RequestBodySpec request = webTestClient.get()
            .uri(path)
            .accept(MediaType.APPLICATION_JSON);
        
        if (authToken != null && !authToken.isEmpty()) {
            request.header("Authorization", "Bearer " + authToken);
        }
        
        return request.exchange();
    }
    
    /**
     * POST request
     */
    public WebTestClient.ResponseSpec post(String path, Object body) {
        WebTestClient.RequestBodySpec request = webTestClient.post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON);
        
        if (authToken != null && !authToken.isEmpty()) {
            request.header("Authorization", "Bearer " + authToken);
        }
        
        if (body != null) {
            request.body(BodyInserters.fromValue(body));
        }
        
        return request.exchange();
    }
    
    /**
     * PUT request
     */
    public WebTestClient.ResponseSpec put(String path, Object body) {
        WebTestClient.RequestBodySpec request = webTestClient.put()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON);
        
        if (authToken != null && !authToken.isEmpty()) {
            request.header("Authorization", "Bearer " + authToken);
        }
        
        if (body != null) {
            request.body(BodyInserters.fromValue(body));
        }
        
        return request.exchange();
    }
    
    /**
     * DELETE request
     */
    public WebTestClient.ResponseSpec delete(String path) {
        WebTestClient.RequestHeadersSpec<?> request = webTestClient.delete()
            .uri(path)
            .accept(MediaType.APPLICATION_JSON);
        
        if (authToken != null && !authToken.isEmpty()) {
            request.header("Authorization", "Bearer " + authToken);
        }
        
        return request.exchange();
    }
    
    /**
     * GET request with query parameters
     */
    public WebTestClient.ResponseSpec get(String path, Map<String, ?> queryParams) {
        WebTestClient.RequestBodySpec request = webTestClient.get()
            .uri(uriBuilder -> {
                uriBuilder.path(path);
                if (queryParams != null) {
                    queryParams.forEach(uriBuilder::queryParam);
                }
                return uriBuilder.build();
            })
            .accept(MediaType.APPLICATION_JSON);
        
        if (authToken != null && !authToken.isEmpty()) {
            request.header("Authorization", "Bearer " + authToken);
        }
        
        return request.exchange();
    }
    
    // Convenience methods matching ApiTestClient interface
    public WebTestClient.ResponseSpec getChatbots() {
        return get("/api/chatbots");
    }
    
    public WebTestClient.ResponseSpec getChatbot(Long id) {
        return get("/api/chatbots/" + id);
    }
    
    public WebTestClient.ResponseSpec createChatbot(String name, String websiteUrl, String description) {
        Map<String, String> body = Map.of(
            "name", name,
            "websiteUrl", websiteUrl,
            "description", description != null ? description : ""
        );
        return post("/api/chatbots", body);
    }
    
    public WebTestClient.ResponseSpec updateChatbot(Long id, String name, String websiteUrl, String description) {
        Map<String, String> body = Map.of(
            "name", name,
            "websiteUrl", websiteUrl,
            "description", description != null ? description : ""
        );
        return put("/api/chatbots/" + id, body);
    }
    
    public WebTestClient.ResponseSpec deleteChatbot(Long id) {
        return delete("/api/chatbots/" + id);
    }
    
    public WebTestClient.ResponseSpec getSubscriptionStatus() {
        return get("/api/subscription/status");
    }
    
    public String getAuthToken() {
        return authToken;
    }
}
```

#### Stap 2.2: Update E2ETestBase

**Wijziging in**: `backend/src/test/java/com/prayer_chat/chatbot/helpers/E2ETestBase.java`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class E2ETestBase {
    
    @LocalServerPort
    protected int port;
    
    // Oude REST Assured client (voor backward compatibility tijdens migratie)
    protected ApiTestClient apiClient;
    
    // Nieuwe WebTestClient (voor gemigreerde tests)
    protected WebTestClient webTestClient;
    protected WebTestClientApiTestClient webApiClient;
    
    @BeforeEach
    void setUp() {
        // ... existing setup ...
        
        // Initialize WebTestClient
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
        
        webApiClient = new WebTestClientApiTestClient(webTestClient);
        
        // Keep REST Assured client for non-migrated tests
        apiClient = new ApiTestClient(port);
        
        // ... rest of setup ...
    }
}
```

### Fase 3: Migratie Code Patterns (Referentie)

#### Pattern 1: GET Request

**REST Assured:**
```java
Response response = apiClient.getChatbots();
response.then()
    .statusCode(200)
    .body("$", isA(List.class))
    .body("size()", greaterThanOrEqualTo(0));
```

**WebTestClient:**
```java
webApiClient.getChatbots()
    .expectStatus().isOk()
    .expectBodyList(Map.class)
    .consumeWith(result -> {
        List<Map<String, Object>> chatbots = result.getResponseBody();
        assertNotNull(chatbots);
        // Additional assertions
    });
```

#### Pattern 2: POST Request

**REST Assured:**
```java
Response response = apiClient.createChatbot("Test", "https://example.com", "Desc");
response.then()
    .statusCode(anyOf(is(200), is(201)))
    .body("name", equalTo("Test"))
    .body("id", notNullValue());
Long id = response.jsonPath().getLong("id");
```

**WebTestClient:**
```java
Long id = webApiClient.createChatbot("Test", "https://example.com", "Desc")
    .expectStatus().isIn(HttpStatus.OK, HttpStatus.CREATED)
    .expectBody()
    .jsonPath("$.name").isEqualTo("Test")
    .jsonPath("$.id").exists()
    .jsonPath("$.id").value(idValue -> {
        assertNotNull(idValue);
        // id is available here
    })
    .returnResult()
    .getResponseBody(); // Or use extractBody() for full body
```

#### Pattern 3: Extract ID from Response

**REST Assured:**
```java
Long chatbotId = extractChatbotId(createResponse);
// Where extractChatbotId does:
Long id = response.jsonPath().getLong("id");
```

**WebTestClient:**
```java
Long chatbotId = webApiClient.createChatbot(...)
    .expectStatus().isCreated()
    .expectBody()
    .jsonPath("$.id").value(id -> {
        assertNotNull(id);
        return ((Number) id).longValue();
    })
    .returnResult()
    .getResponseBody(); // Or use AtomicReference pattern
```

**Betere Pattern met AtomicReference:**
```java
AtomicReference<Long> chatbotIdRef = new AtomicReference<>();
webApiClient.createChatbot(...)
    .expectStatus().isCreated()
    .expectBody()
    .jsonPath("$.id").value(id -> chatbotIdRef.set(((Number) id).longValue()));
Long chatbotId = chatbotIdRef.get();
```

#### Pattern 4: Status Code Assertions

**REST Assured:**
```java
response.then()
    .statusCode(anyOf(is(200), is(201)));
```

**WebTestClient:**
```java
.expectStatus().isIn(HttpStatus.OK, HttpStatus.CREATED)
// Or
.expectStatus().is2xxSuccessful()
```

#### Pattern 5: Body Assertions

**REST Assured:**
```java
response.then()
    .body("name", equalTo("Test"))
    .body("id", notNullValue())
    .body("websiteUrl", equalTo("https://example.com"));
```

**WebTestClient:**
```java
.expectBody()
    .jsonPath("$.name").isEqualTo("Test")
    .jsonPath("$.id").exists()
    .jsonPath("$.websiteUrl").isEqualTo("https://example.com")
```

#### Pattern 6: Array Assertions

**REST Assured:**
```java
response.then()
    .body("$", isA(List.class))
    .body("size()", greaterThanOrEqualTo(0))
    .body("[0].name", equalTo("First Bot"));
```

**WebTestClient:**
```java
.expectBodyList(Map.class)
    .hasSize(greaterThanOrEqualTo(0))
    .consumeWith(result -> {
        List<Map<String, Object>> list = result.getResponseBody();
        if (list != null && !list.isEmpty()) {
            assertEquals("First Bot", list.get(0).get("name"));
        }
    })
```

### Fase 4: Migratie per Test Class (Incrementeel)

#### Stap 4.1: Migreer ChatbotApiE2ETest (Eerste)

**Reden**: Relatief eenvoudig, veel GET requests

**Aanpak:**
1. Kopieer `ChatbotApiE2ETest` naar `ChatbotApiE2ETestWebClient`
2. Vervang alle `apiClient` calls met `webApiClient`
3. Converteer alle assertions naar WebTestClient format
4. Test de gemigreerde tests
5. Als succesvol: verwijder oude test, hernoem nieuwe

#### Stap 4.2: Migreer Rest van Test Classes

Herhaal voor:
- `AuthApiE2ETest`
- `ErrorHandlingE2ETest`
- `SecurityE2ETest`
- `SubscriptionApiE2ETest`
- `UserJourneyE2ETest`
- `ChatApiE2ETest`
- `SampleE2ETest`

### Fase 5: Cleanup (Na Volledige Migratie)

1. Verwijder `ApiTestClient.java`
2. Verwijder REST Assured dependency uit `pom.xml`
3. Verwijder `RestAssured.reset()` calls uit `E2ETestBase`
4. Update documentatie

## Helper Methods voor WebTestClient

### Extract ID Helper

```java
protected Long extractChatbotId(WebTestClient.ResponseSpec responseSpec) {
    AtomicReference<Long> idRef = new AtomicReference<>();
    responseSpec
        .expectStatus().is2xxSuccessful()
        .expectBody()
        .jsonPath("$.id").value(id -> {
            if (id instanceof Integer) {
                idRef.set(((Integer) id).longValue());
            } else if (id instanceof Long) {
                idRef.set((Long) id);
            } else {
                fail("ID is not a valid number type: " + id.getClass().getName());
            }
        });
    return idRef.get();
}
```

### Assert Response Status Helper

```java
protected void assertResponseStatus(WebTestClient.ResponseSpec responseSpec, 
                                   HttpStatus... expectedStatuses) {
    responseSpec.expectStatus().isIn(expectedStatuses);
}
```

## Testing Strategie

### Per Migratie

1. **Run gemigreerde test class**
2. **Vergelijk resultaten** met oude REST Assured versie (als nog beschikbaar)
3. **Fix eventuele verschillen** in assertions
4. **Verifieer alle edge cases**

### Volledige Test Suite

Na volledige migratie:
1. Run alle E2E tests
2. Verifieer dat alle 29 GET request tests nu slagen
3. Verifieer dat POST requests nog steeds werken
4. Check CI/CD pipeline

## Mogelijke Uitdagingen en Oplossingen

### Uitdaging 1: Response Body Extraction

**Probleem**: WebTestClient heeft andere API voor body extraction

**Oplossing**: Gebruik `AtomicReference` pattern of `returnResult().getResponseBody()`

### Uitdaging 2: Hamcrest Matchers

**Probleem**: REST Assured gebruikt Hamcrest, WebTestClient niet altijd

**Oplossing**: Gebruik JUnit assertions of WebTestClient's built-in matchers

### Uitdaging 3: Error Response Assertions

**Probleem**: Verschillende API voor error responses

**Oplossing**: 
```java
.expectStatus().is4xxClientError()
.expectBody()
    .jsonPath("$.error").exists()
```

### Uitdaging 4: Authentication Headers

**Probleem**: Mogelijk verschillende header handling

**Oplossing**: Test authentication early in migratie

## Tijdsinschatting

- **Fase 1 (Setup)**: 30 minuten
- **Fase 2 (Nieuwe Client)**: 2-3 uur
- **Fase 3 (Patterns)**: 1 uur (documentatie)
- **Fase 4 (Migratie)**: 4-6 uur (8 test classes × 30-45 min)
- **Fase 5 (Cleanup)**: 30 minuten

**Totaal**: ~8-11 uur werk

## Success Criteria

✅ Alle 29 GET request tests slagen  
✅ Alle POST request tests blijven werken  
✅ CI/CD pipeline groen  
✅ Geen REST Assured dependencies meer  
✅ Code is cleaner en meer Spring-native  

## Volgende Stappen

1. ✅ Review dit migratieplan
2. ⏳ Start met Fase 1 (Setup)
3. ⏳ Implementeer WebTestClientApiTestClient
4. ⏳ Migreer eerste test class als proof of concept
5. ⏳ Continue met rest van migratie

## Referenties

- [Spring WebTestClient Documentation](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html)
- [WebTestClient API Reference](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/test/web/reactive/server/WebTestClient.html)

