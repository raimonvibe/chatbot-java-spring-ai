# Session Timeout: Industry Best Practices

## 📊 Gangbare Timeouts (2024-2025)

### Standaard Praktijken:
- **Banking/Financial**: 5-15 minuten
- **Healthcare**: 15-30 minuten
- **E-commerce**: 30 minuten - 2 uur
- **SaaS Platforms**: 1-4 uur
- **Social Media**: 2-8 uur
- **Chatbot/Customer Service**: 1-4 uur

### Veiligheidsrichtlijnen:
- **OWASP**: Aanbeveelt 15-30 minuten voor gevoelige applicaties
- **NIST**: 30 minuten voor standaard web applicaties
- **GDPR**: Vereist "passende" timeouts (geen specifieke tijd)

## ⚠️ 24 Uur is ONGEBRUIKELIJK

**Problemen met 24 uur:**
- ❌ Verhoogd security risico (gedeelde computers, verlaten devices)
- ❌ Niet conform industry standards
- ❌ Kan compliance issues veroorzaken
- ❌ Gebruikers vergeten dat ze ingelogd zijn

## ✅ Aanbeveling voor Prayer-Chat

### Optie 1: Conservatief (Veiliger) - **2 uur**
```yaml
spring:
  session:
    timeout: 2h
```
**Voordelen:**
- ✅ Goede balans tussen UX en security
- ✅ Conform industry standards
- ✅ Gebruikers blijven ingelogd tijdens actief gebruik
- ✅ Automatisch uitloggen na 2 uur inactiviteit

### Optie 2: Standaard - **4 uur**
```yaml
spring:
  session:
    timeout: 4h
```
**Voordelen:**
- ✅ Goede UX (hele werkdag ingelogd)
- ✅ Nog steeds acceptabel voor SaaS
- ✅ Gebruikers hoeven niet constant in te loggen

### Optie 3: Lang - **8 uur** (Maximum aanbevolen)
```yaml
spring:
  session:
    timeout: 8h
```
**Voordelen:**
- ✅ Zeer goede UX
- ✅ Gebruikers blijven hele dag ingelogd
- ⚠️ Iets minder veilig, maar acceptabel voor chatbot

## 🔒 Security Mitigaties

Ongeacht de timeout, zorg voor:
1. ✅ **Secure cookies** (`secure: true` voor HTTPS)
2. ✅ **HttpOnly cookies** (beschermt tegen XSS)
3. ✅ **SameSite: lax** (beschermt tegen CSRF)
4. ✅ **Automatische logout** na inactiviteit
5. ✅ **"Remember me" optie** voor langere sessies (optioneel)

## 💡 Aanbeveling voor Prayer-Chat

**Voor een chatbot applicatie met OAuth2:**
- **Aanbevolen: 2-4 uur**
- **Reden**: Goede balans tussen UX en security
- **Alternatief**: Implementeer "Remember me" voor 7-30 dagen (optioneel)

**Huidige configuratie (24h) is te lang voor:**
- ❌ Security best practices
- ❌ Industry standards
- ❌ Compliance (GDPR, etc.)

## 🎯 Conclusie

**24 uur is NIET gangbaar** - de meeste applicaties gebruiken:
- **Korte sessies**: 15-30 minuten (banking, healthcare)
- **Middel**: 1-4 uur (SaaS, e-commerce)
- **Lang**: 4-8 uur (social media, entertainment)

**Voor Prayer-Chat: 2-4 uur is een goede keuze.**

