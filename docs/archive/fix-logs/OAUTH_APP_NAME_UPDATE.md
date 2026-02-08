# OAuth App Name Update - Tjanabot → Prayer-Chat

## Probleem

De OAuth consent screen toont nog steeds "Tjanabot" in plaats van "Prayer-Chat" op de **productie website** (Render deployment). Dit komt omdat de app naam in Google Cloud Console nog niet is bijgewerkt.

⚠️ **BELANGRIJK**: Dit is voor de **productie OAuth app** die wordt gebruikt op Render (`chatbot-backend-4mp4.onrender.com` of `prayer-chat.com`).

## Oplossing

De app naam wordt **niet** in de code geconfigureerd, maar in **Google Cloud Console**. Volg deze stappen:

### Stap 1: Identificeer de Juiste OAuth App

**Voor productie (Render deployment):**
- De OAuth app wordt gebruikt voor: `https://chatbot-backend-4mp4.onrender.com` of `https://prayer-chat.com`
- Controleer welke **Client ID** wordt gebruikt in Render environment variables
- Deze Client ID moet overeenkomen met de OAuth app in Google Cloud Console

### Stap 2: Ga naar Google Cloud Console

1. Open [Google Cloud Console](https://console.cloud.google.com/)
2. Selecteer het **juiste project** (waar je productie OAuth credentials zijn aangemaakt)
3. **Tip**: Als je meerdere projecten hebt, controleer welke Client ID wordt gebruikt op Render

### Stap 3: Update OAuth Consent Screen

1. Ga naar **"APIs & Services"** → **"OAuth consent screen"**
2. Klik op **"EDIT APP"** (of "Create" als je nog geen app hebt)
3. Zoek het veld **"App name"**
4. Wijzig van: `Tjanabot` → `Prayer-Chat`
5. **App logo** (optioneel): Upload een Prayer-Chat logo als je die hebt
6. Klik op **"SAVE AND CONTINUE"**
7. Ga door alle stappen (Scopes, Test users, etc.) en klik op **"BACK TO DASHBOARD"**

### Stap 4: Verifieer Redirect URIs

Zorg dat de volgende redirect URIs zijn toegevoegd in **"Credentials"** → je OAuth 2.0 Client ID:

- `https://chatbot-backend-4mp4.onrender.com/login/oauth2/code/google`
- `https://prayer-chat.com/login/oauth2/code/google` (als je custom domain gebruikt)
- `https://www.prayer-chat.com/login/oauth2/code/google` (als je www subdomain gebruikt)

### Stap 5: Verifieer op Productie

1. Ga naar je productie website: `https://prayer-chat.com` of `https://chatbot-backend-4mp4.onrender.com`
2. Log uit van je Google account (of gebruik incognito/private window)
3. Probeer opnieuw in te loggen via Google OAuth
4. Je zou nu **"Prayer-Chat"** moeten zien in plaats van **"Tjanabot"** in de consent screen

**⚠️ Let op**: Wijzigingen kunnen 5-15 minuten duren voordat ze zichtbaar zijn. Als je nog steeds "Tjanabot" ziet:
- Wacht nog een paar minuten
- Clear je browser cache
- Probeer een andere browser of incognito mode

## Belangrijke Notities

- ⚠️ **De app naam kan NIET via code worden gewijzigd** - dit is een Google Cloud Console instelling
- ⚠️ **Wijzigingen kunnen 5-15 minuten duren** voordat ze zichtbaar zijn op productie
- ⚠️ **Test users** moeten mogelijk opnieuw worden toegevoegd na wijzigingen
- ⚠️ Als je app in **"Testing"** status is, kunnen alleen test users inloggen
- ⚠️ **Voor productie**: Zorg dat je de juiste OAuth app bijwerkt (dezelfde Client ID die op Render wordt gebruikt)
- ⚠️ **Meerdere OAuth apps**: Als je aparte apps hebt voor development en productie, update beide

## Checklist voor Productie Update

- [ ] Google Cloud Console geopend
- [ ] Juiste project geselecteerd (productie OAuth credentials)
- [ ] OAuth consent screen → App name gewijzigd naar "Prayer-Chat"
- [ ] Alle stappen doorlopen en opgeslagen
- [ ] Redirect URIs gecontroleerd (Render URLs aanwezig)
- [ ] 5-15 minuten gewacht
- [ ] Getest op productie website (incognito/private window)
- [ ] "Prayer-Chat" zichtbaar in consent screen ✅

## Alternatieve App Namen

Als je een andere naam wilt gebruiken:
- `Prayer-Chat`
- `Prayer-Chat AI`
- `Prayer-Chat Chatbot`
- `Prayer-Chat AI Chatbot`

**Aanbeveling**: Gebruik `Prayer-Chat` voor consistentie met de rest van de applicatie.

