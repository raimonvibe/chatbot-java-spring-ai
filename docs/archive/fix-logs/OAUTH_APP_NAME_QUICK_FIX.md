# Quick Fix: Google OAuth App Name - "chatbot-backend-4mp4.onrender.com" → "Prayer-Chat"

## ⚠️ Probleem
De Google login pagina toont nog steeds:
- ❌ "Doorgaan naar chatbot-backend-4mp4.onrender.com"
- ❌ In plaats van "Doorgaan naar Prayer-Chat"

## ✅ Oplossing (5 minuten)

### Stap 1: Google Cloud Console Openen
1. Ga naar: https://console.cloud.google.com/
2. **Selecteer het juiste project** (waar je OAuth credentials zijn)

### Stap 2: OAuth Consent Screen
1. Links in menu: **"APIs & Services"**
2. Klik: **"OAuth consent screen"**

### Stap 3: App Name Wijzigen
1. Klik op **"EDIT APP"** (bovenaan rechts)
2. Zoek het veld **"App name"** (eerste veld bovenaan)
3. Wijzig naar: **`Prayer-Chat`**
4. Scroll naar beneden
5. Klik **"SAVE AND CONTINUE"**
6. Klik door alle stappen (of "BACK TO DASHBOARD")

### Stap 4: Wachten & Testen
1. **Wacht 5-10 minuten** (Google heeft tijd nodig om te updaten)
2. **Log volledig uit** (gebruik de Logout knop in dashboard)
3. **Test in incognito venster** (om cache te vermijden)
4. Login opnieuw - je zou nu "Prayer-Chat" moeten zien

## 📍 Waar staat het precies?

```
Google Cloud Console
├── APIs & Services
    └── OAuth consent screen
        └── EDIT APP
            └── App name: [Prayer-Chat] ← HIER!
```

## ❓ Nog steeds niet gewijzigd?

1. **Check of je het juiste project hebt**:
   - Ga naar Render → Environment Variables
   - Check welke `GOOGLE_CLIENT_ID` wordt gebruikt
   - Zorg dat je dat project hebt geselecteerd in Google Cloud Console

2. **Check Publishing Status**:
   - In OAuth consent screen, zie je "Testing" of "In production"
   - Als "Testing": alleen test users kunnen inloggen
   - Als "In production": iedereen kan inloggen (maar verificatie kan nodig zijn)

3. **Force refresh**:
   - Hard refresh: Ctrl+Shift+R (Windows) of Cmd+Shift+R (Mac)
   - Of gebruik incognito/private window

4. **Check of je hebt opgeslagen**:
   - Ga terug naar OAuth consent screen
   - Check of "Prayer-Chat" nog steeds in het "App name" veld staat

## 🔍 Verificatie

Na de wijziging zou je moeten zien:
- ✅ "Inloggen bij Prayer-Chat"
- ✅ "Doorgaan naar Prayer-Chat"
- ❌ NIET meer "chatbot-backend-4mp4.onrender.com"

## ⏱️ Tijdlijn

- **Direct**: Wijziging wordt opgeslagen in Google Cloud Console
- **5-10 minuten**: Wijziging wordt doorgevoerd bij Google
- **Na logout/login**: Je ziet de nieuwe naam

## 💡 Tip

Als je meerdere OAuth apps hebt (development, staging, production):
- Maak een notitie van welke Client ID bij welke app hoort
- Update alle apps die "Prayer-Chat" moeten tonen

