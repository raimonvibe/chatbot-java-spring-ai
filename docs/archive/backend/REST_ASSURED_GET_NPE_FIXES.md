# REST Assured GET NullPointerException - Fixes en Implementatieplan

**Date**: December 23, 2025  
**Issue**: 29 tests falen met NPE bij GET requests  
**Status**: Fixes geïdentificeerd, implementatie in progress

## Root Cause Analyse

Op basis van onderzoek zijn de volgende root causes geïdentificeerd:

1. **Static RequestSpecification Pollution** ⭐ **MEEST WAARSCHIJNLIJK**
   - REST Assured's statische configuratie bewaart state tussen tests
   - Leidt tot NPE's wanneer state inconsistent is

2. **@BeforeAll vs @BeforeEach Scope Issue**
   - REST Assured context is method-scoped maar wordt class-scoped geïnitialiseerd

3. **Missing Content-Type Headers**
   - Server retourneert geen Content-Type header, wat REST Assured's parser laat crashen

4. **Variable Redeclaration in Setup Methods**
   - RequestSpecification wordt per ongeluk als lokale variabele hergedeclareerd

5. **Thread-Safety Issues (Parallel Execution)**
   - Tests delen statische REST Assured configuratie

## Fix 1: Reset Static REST Assured Configuration ⭐ HOOGSTE PRIORITEIT

### Probleem
REST Assured gebruikt statische configuratie die niet automatisch wordt gereset tussen tests. Dit zorgt voor state pollution.

### Oplossing A: Reset in @BeforeEach en @AfterEach
```java
@BeforeEach
void setUpRestAssured() {
    RestAssured.reset();  // Reset alle statische configuratie
    RestAssured.baseURI = "http://localhost";
    RestAssured.port = port;
    RestAssured.basePath = "/api";
    RestAssured.defaultParser = Parser.JSON;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
}

@AfterEach
void tearDownRestAssured() {
    RestAssured.reset();  // Reset na elke test
    RestAssured.requestSpecification = null;  // Extra zekerheid
}
```

### Oplossing B: Vermijd Static Configuration (Alternatief)
Gebruik instance-level RequestSpecification:
```java
private RequestSpecification requestSpec;

@BeforeEach
void setUp() {
    requestSpec = new RequestSpecBuilder()
        .setBaseUri("http://localhost")
        .setPort(port)
        .setBasePath("/api")
        .setContentType(ContentType.JSON)
        .setAccept(ContentType.JSON)
        .build();
}

@Test
void shouldReturnChatbots() {
    given()
        .spec(requestSpec)  // Gebruik instance spec
    .when()
        .get("/chatbots")
    .then()
        .statusCode(200);
}
```

**Waarom dit werkt**: Voorkomt state pollution tussen tests.

## Fix 2: Controleer @BeforeAll vs @BeforeEach Scope

### Probleem
Als REST Assured setup in @BeforeAll staat maar tests in instance methods, krijg je NPE's.

### Oplossing
Gebruik @BeforeEach voor instance-level setup:
```java
// ✅ CORRECT
@LocalServerPort
private int port;

@BeforeEach
void setupRestAssured() {
    RestAssured.reset();
    RestAssured.baseURI = "http://localhost";
    RestAssured.port = port;  // Werkt nu wel!
    RestAssured.basePath = "/api";
}
```

## Fix 3: Expliciet Content-Type Accepteren voor GET Requests

### Probleem
Sommige endpoints retourneren geen Content-Type header, wat REST Assured's parser doet crashen.

### Oplossing A: Set Accept Header Expliciet
```java
@Test
void shouldGetChatbots() {
    given()
        .accept(ContentType.JSON)  // Expliciet accept header
        .contentType(ContentType.JSON)
    .when()
        .get("/chatbots")
    .then()
        .statusCode(200);
}
```

### Oplossing B: Configureer Default Parser
```java
@BeforeEach
void setUp() {
    RestAssured.reset();
    RestAssured.baseURI = "http://localhost";
    RestAssured.port = port;
    RestAssured.defaultParser = Parser.JSON;  // Default parser
}
```

## Fix 4: Controleer Variable Redeclaration

### Probleem
Field wordt per ongeluk als lokale variabele hergedeclareerd.

### Check Pattern
```java
// ❌ FOUT - Herverklaart als lokale variabele!
@BeforeEach
void setUp() {
    String token = authenticateAndGetToken();  // Lokale variabele!
}

// ✅ CORRECT - Assign aan field
@BeforeEach
void setUp() {
    token = authenticateAndGetToken();  // Field assignment
}
```

## Fix 5: Thread-Safe Tests (Parallel Execution)

### Probleem
Als tests parallel draaien, kunnen ze statische REST Assured configuratie delen.

### Oplossing A: Disable Parallel Execution
```java
@Execution(ExecutionMode.SAME_THREAD)
public class ChatbotApiE2ETest {
    // tests
}
```

### Oplossing B: ThreadLocal RequestSpecification
```java
private static ThreadLocal<RequestSpecification> requestSpec = new ThreadLocal<>();

@BeforeEach
void setUp() {
    requestSpec.set(
        new RequestSpecBuilder()
            .setBaseUri("http://localhost")
            .setPort(port)
            .build()
    );
}
```

## Fix 6: Verbeter Request Logging voor Debugging

### Tijdelijke Debug Fix
```java
@Test
void shouldGetChatbots() {
    given()
        .log().all()  // Log request
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
    .when()
        .get("/chatbots")
    .then()
        .log().all()  // Log response
        .statusCode(200);
}
```

## Implementatie Strategie

### Stap 1: Implementeer Fix 1 (Reset Configuration) ⭐
Dit lost waarschijnlijk 80% van de problemen op.

### Stap 2: Controleer alle Test Classes
- Zoek naar @BeforeAll met REST Assured setup
- Zoek naar variable redeclaration
- Zoek naar statische RestAssured.requestSpecification usage

### Stap 3: Voeg Content-Type Headers Toe
Voor alle GET requests expliciete Accept/Content-Type headers.

### Stap 4: Test Incremental
- Fix één test class
- Run de tests
- Als het werkt, apply pattern naar andere classes

## Quick Wins Checklist

- [ ] `RestAssured.reset()` in @BeforeEach en @AfterEach
- [ ] Geen @BeforeAll met REST Assured instance fields
- [ ] Geen variable redeclaration in setup methods
- [ ] Accept/Content-Type headers op GET requests
- [ ] ThreadLocal voor parallel tests (indien nodig)
- [ ] Logging enabled voor debugging

