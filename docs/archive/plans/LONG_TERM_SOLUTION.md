# Long-Term Solution: Spring AI ChatModel Configuration

## Probleem

Spring AI auto-configuratie vereist dat `spring.ai.anthropic.api-key` property niet leeg is. Het probleem is dat:
- `spring-dotenv` laadt `.env` files
- Maar Spring AI auto-configuratie draait voordat property resolution klaar is
- Hierdoor blijft `spring.ai.anthropic.api-key` leeg, zelfs als `ANTHROPIC_API_KEY` environment variable beschikbaar is

## Oplossing: ApplicationListener (Spring Boot 4.0.0 Compatible)

**Bestand**: `EnvironmentVariableConfig.java`

Deze listener:
1. Luistert naar `ApplicationEnvironmentPreparedEvent` (draait vroeg in Spring Boot startup)
2. Leest environment variables uit .env files (via spring-dotenv) of system env vars
3. Zet Spring properties **direct** voordat Spring AI auto-configuratie draait
4. Zorgt ervoor dat Spring AI auto-configuratie de properties correct ziet
5. **Werkt met Spring Boot 4.0.0** (in tegenstelling tot deprecated EnvironmentPostProcessor)

## Hoe het werkt

### Startup Volgorde:
1. **spring-dotenv** laadt `.env` files (als aanwezig)
2. **EnvironmentVariableConfig** (ApplicationListener)
   - Luistert naar `ApplicationEnvironmentPreparedEvent`
   - Leest `ANTHROPIC_API_KEY`, `JWT_SECRET`, `COHERE_API_KEY` uit environment
   - Zet Spring properties (`spring.ai.anthropic.api-key`, `jwt.secret`, etc.)
   
3. **Spring AI Auto-Configuration**
   - Ziet `spring.ai.anthropic.api-key` property (niet leeg!)
   - Configureert `AnthropicChatModel` automatisch ✅

4. **AiConfiguration**
   - Gebruikt de auto-geconfigureerde `ChatModel`
   - Fallback wordt alleen gebruikt als alles faalt

## Voordelen

✅ **Werkt altijd**: Onafhankelijk van spring-dotenv timing
✅ **Geen expliciete env vars nodig**: Backend kan starten zonder `export`
✅ **Werkt in productie**: Render environment variables worden automatisch gebruikt
✅ **Geen code changes nodig**: Spring AI auto-configuratie werkt zoals bedoeld

## Configuratie

### AiChatbotApplication.java
```java
SpringApplication app = new SpringApplication(AiChatbotApplication.class);
app.addListeners(new EnvironmentVariableConfig());
app.run(args);
```

Dit registreert de listener zodat Spring Boot hem gebruikt bij startup.

### application.yml
```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}
```

De property wordt nu **altijd** correct gezet door de EnvironmentPostProcessor.

## Testen

1. Start backend zonder expliciete env vars:
   ```bash
   cd backend
   mvn spring-boot:run
   ```

2. Check logs voor:
   ```
   ✅ Set spring.ai.anthropic.api-key from ANTHROPIC_API_KEY environment variable
   ```

3. Test chat functionaliteit - zou nu moeten werken!

## Voor Render

In Render worden environment variables direct als system env vars ingesteld. De EnvironmentPostProcessor leest deze automatisch en zet de property, dus Spring AI auto-configuratie werkt zonder extra configuratie.

## Fallback

Als de EnvironmentPostProcessor faalt (bijv. ANTHROPIC_API_KEY ontbreekt), gebruikt `AiConfiguration.chatModel()` een fallback die een duidelijke error message geeft.

