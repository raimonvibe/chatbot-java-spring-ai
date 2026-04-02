# Session Timeout Uitleg

## ❌ Misverstand: "Cleanup na 1 uur = uitloggen na 1 uur"

**NEE!** De cleanup-cron heeft **NIETS** te maken met wanneer gebruikers worden uitgelogd.

## ✅ Hoe het WERKELIJK werkt:

### 1. Session Timeout (Wanneer gebruikers worden uitgelogd)
```yaml
spring:
  session:
    timeout: 24h  # Gebruikers blijven 24 uur ingelogd
```

**Dit bepaalt**: Na hoeveel tijd van **inactiviteit** een gebruiker wordt uitgelogd.
- ✅ **24 uur** = Gebruiker blijft ingelogd zolang hij actief is
- ✅ Na 24 uur **inactiviteit** wordt hij uitgelogd
- ✅ Als hij actief blijft, blijft hij ingelogd

### 2. Cleanup Cron (Alleen database opruiming)
```yaml
spring:
  session:
    jdbc:
      cleanup-cron: "0 0 * * * *"  # Elke uur op minuut 0
```

**Dit doet**: Verwijderd **verlopen** sessie records uit de database.
- ✅ Ruimt alleen op wat **al verlopen** is
- ✅ Heeft **GEEN** invloed op actieve gebruikers
- ✅ Is alleen voor database onderhoud

## 📊 Voorbeeld:

**Scenario**: Gebruiker logt in om 10:00

| Tijd | Actie | Session Status |
|------|-------|----------------|
| 10:00 | Login | ✅ Actief (24h timeout) |
| 10:30 | Actief | ✅ Nog steeds actief |
| 11:00 | Cleanup cron draait | ✅ Gebruiker nog steeds ingelogd |
| 12:00 | Actief | ✅ Nog steeds actief |
| 14:00 | Inactief | ✅ Nog steeds ingelogd (binnen 24h) |
| 10:00 (volgende dag) | Nog steeds inactief | ❌ Uitgelogd (24h inactiviteit) |

## 🔧 Huidige Configuratie:

```yaml
spring:
  session:
    timeout: 24h  # ✅ Gebruikers blijven 24 uur ingelogd

server:
  servlet:
    session:
      cookie:
        max-age: 86400  # ✅ 24 uur (86400 seconden)
      timeout: 24h  # ✅ 24 uur
```

**Resultaat**: 
- ✅ Gebruikers blijven **24 uur** ingelogd
- ✅ Cleanup ruimt alleen verlopen sessies op
- ✅ Geen automatisch uitloggen na 1 uur

## 💡 Aanbeveling:

Voor een chatbot applicatie is **24 uur** een goede keuze:
- ✅ Gebruikers hoeven niet constant opnieuw in te loggen
- ✅ Veilig genoeg (automatisch uitloggen na inactiviteit)
- ✅ Goede user experience

Als je het korter wilt (bijv. 2 uur), kan je dit aanpassen:
```yaml
spring:
  session:
    timeout: 2h
```

