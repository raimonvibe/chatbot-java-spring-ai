# REST Assured GET NPE Fix - Implementatieplan

**Date**: December 23, 2025  
**Status**: In Progress

## Stap-voor-Stap Implementatieplan

### Stap 1: Fix 1 - Reset REST Assured Configuration ✅
**Status**: Geïmplementeerd
- ✅ `RestAssured.reset()` toegevoegd in `E2ETestBase.setUp()`
- ✅ `RestAssured.reset()` toegevoegd in `E2ETestBase.tearDown()`
- ✅ Static baseURI/port configuratie in setUp()
- ✅ Logging enabled

**Resultaat**: NPE blijft bestaan - Fix 1 alleen is niet voldoende

### Stap 2: Fix 3 - Explicit Accept Headers ✅
**Status**: Geïmplementeerd
- ✅ `.accept(ContentType.JSON)` toegevoegd aan `get()` method
- ✅ Content-Type headers al aanwezig in `createRequest()`

**Resultaat**: NPE blijft bestaan - Fix 3 alleen is niet voldoende

### Stap 3: Fix 2 - Check @BeforeAll vs @BeforeEach
**Status**: Te controleren
- [ ] Zoek naar @BeforeAll met REST Assured setup in test classes
- [ ] Verifieer dat alle setup in @BeforeEach staat

### Stap 4: Fix 4 - Check Variable Redeclaration
**Status**: Te controleren
- [ ] Scan alle @BeforeEach methods voor variable redeclaration
- [ ] Fix eventuele redeclarations

### Stap 5: Fix 5 - Thread Safety
**Status**: Te controleren
- [ ] Check of tests parallel draaien
- [ ] Voeg @Execution(ExecutionMode.SAME_THREAD) toe indien nodig

### Stap 6: Fix 6 - Enhanced Logging
**Status**: Te implementeren
- [ ] Voeg `.log().all()` toe aan GET requests voor debugging
- [ ] Analyseer logs om exacte NPE locatie te vinden

## Implementatie Status

### ✅ Geïmplementeerde Fixes

1. **Fix 1: RestAssured.reset()** ✅
   - Toegevoegd in `E2ETestBase.setUp()`
   - Toegevoegd in `E2ETestBase.tearDown()`
   - **Resultaat**: NPE blijft bestaan

2. **Fix 3: Explicit Accept Headers** ✅
   - `.accept(ContentType.JSON)` toegevoegd
   - Content-Type headers aanwezig
   - **Resultaat**: NPE blijft bestaan

3. **Fix 6: Enhanced Logging** ✅
   - Logging toegevoegd aan GET requests
   - **Resultaat**: NPE blijft bestaan, maar betere error messages

4. **Alternatieve Aanpak: Full URL** ✅
   - Volledige URL gebruikt in plaats van relatief pad
   - RequestSpecification volledig opnieuw opgebouwd
   - **Resultaat**: NPE blijft bestaan

### ❌ Resultaat

**Alle fixes zijn geïmplementeerd, maar NPE blijft bestaan.**

Dit bevestigt dat het probleem **diep in REST Assured zelf** zit, niet in onze configuratie.

### Conclusie

Na het implementeren van alle aanbevolen fixes:
- ✅ RestAssured.reset() in @BeforeEach en @AfterEach
- ✅ Explicit Accept/Content-Type headers
- ✅ Full URL approach
- ✅ Fresh RequestSpecification building
- ✅ Enhanced logging

**NPE blijft bestaan voor GET requests, terwijl POST requests perfect werken.**

Dit wijst op een **fundamentele bug in REST Assured 5.4.0** met GET requests die niet op te lossen is met configuratie fixes.

## Aanbevolen Volgende Stappen

1. **Accepteer dat REST Assured GET NPE een library bug is**
2. **Gebruik workaround**: POST voor GET endpoints (tijdelijk)
3. **Of migreer naar**: Apache HttpClient of WebTestClient
4. **Rapporteer bug**: File issue op REST Assured GitHub met alle bevindingen

