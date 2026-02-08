# Volledig Uitloggen met Google OAuth

## Probleem
Wanneer je cookies verwijdert op de frontend, word je nog steeds onthouden door Google OAuth. Dit komt omdat:
1. **Spring Session** blijft actief in de database
2. **Google OAuth sessie** blijft actief bij Google
3. **Browser cookies** kunnen opnieuw worden aangemaakt

## Oplossing: Volledige Logout

### 1. Gebruik de Logout Knop
Er is nu een **"Logout"** knop in het dashboard die:
- ✅ Spring Session invalideert
- ✅ SecurityContext cleart
- ✅ Alle cookies verwijdert
- ✅ Redirect naar Google logout

### 2. Handmatige Volledige Logout

Als je volledig wilt uitloggen om de Google OAuth consent screen te zien:

#### Stap 1: Gebruik de Logout Knop
1. Ga naar het dashboard
2. Klik op de **"Logout"** knop (rechtsboven)
3. Dit opent automatisch Google logout in een nieuw venster

#### Stap 2: Google Logout Bevestigen
1. In het nieuwe venster, bevestig dat je wilt uitloggen bij Google
2. Sluit het venster

#### Stap 3: Browser Cache Clearen (Optioneel)
Voor volledige zekerheid:
1. Open Developer Tools (F12)
2. Ga naar **Application** tab (Chrome) of **Storage** tab (Firefox)
3. Klik op **Clear site data** of **Clear storage**
4. Of gebruik Incognito/Private window

#### Stap 4: Opnieuw Inloggen
1. Ga naar `/login`
2. Klik op "Login with Google"
3. Je ziet nu de Google OAuth consent screen met de huidige "App name"

## Google OAuth Consent Screen Controleren

### Stap 1: Ga naar Google Cloud Console
1. Open [Google Cloud Console](https://console.cloud.google.com/)
2. Selecteer je project

### Stap 2: Navigeer naar OAuth Consent Screen
1. In de linker sidebar: **"APIs & Services"**
2. Klik op **"OAuth consent screen"**

### Stap 3: Controleer de App Name
Je ziet nu:
- **App name**: Dit is wat gebruikers zien tijdens login
- **User support email**: Je email
- **App logo**: (optioneel)
- **App domain**: prayer-chat.com (als ingesteld)

### Stap 4: Update App Name (als nodig)
Als er nog "Tjanabot" staat:
1. Klik op **"EDIT APP"** (bovenaan)
2. Wijzig **"App name"** naar: `Prayer-Chat`
3. Klik op **"SAVE AND CONTINUE"**
4. Wacht 5-10 minuten voor propagatie

### Stap 5: Controleer Publishing Status
- **Testing**: Alleen test users kunnen inloggen
- **In production**: Iedereen kan inloggen (maar verificatie kan nodig zijn)

## Troubleshooting

### Nog steeds ingelogd na logout?
1. **Check Spring Session database**: 
   - Sessies kunnen in PostgreSQL blijven staan
   - Ze worden automatisch opgeruimd na timeout (4 uur)
   
2. **Check browser cookies**:
   - Open Developer Tools → Application → Cookies
   - Verwijder alle cookies voor `prayer-chat.com` en `chatbot-backend-4mp4.onrender.com`

3. **Gebruik Incognito/Private window**:
   - Dit garandeert dat je volledig uitgelogd bent

### Google OAuth consent screen toont nog "Tjanabot"?
1. **Wacht 5-10 minuten**: Changes kunnen tijd nodig hebben om te propagaten
2. **Clear browser cache**: Hard refresh (Ctrl+Shift+R of Cmd+Shift+R)
3. **Gebruik Incognito window**: Om zeker te zijn dat oude cache niet wordt gebruikt
4. **Check of je de juiste project hebt geselecteerd**: In Google Cloud Console

### Kan niet uitloggen?
1. **Check of logout endpoint werkt**: 
   - Open Developer Tools → Network tab
   - Klik op Logout
   - Check of `POST /api/auth/logout` een 200 status geeft

2. **Handmatig cookies verwijderen**:
   - Developer Tools → Application → Cookies
   - Verwijder alle cookies

3. **Force logout via Google**:
   - Ga direct naar: https://accounts.google.com/logout
   - Log in opnieuw in om te testen

## Volledige Reset (Voor Testen)

Als je volledig wilt resetten om de OAuth flow te testen:

1. **Logout via de knop** in dashboard
2. **Google logout**: https://accounts.google.com/logout
3. **Clear browser data**: Developer Tools → Application → Clear storage
4. **Incognito window**: Open een nieuw incognito/private window
5. **Ga naar login**: `/login`
6. **Login met Google**: Je ziet nu de volledige OAuth flow

## Technische Details

### Wat gebeurt er bij logout?

1. **Backend (`POST /api/auth/logout`)**:
   - Invalideert Spring Session (verwijdert uit database)
   - Cleart SecurityContext
   - Retourneert Google logout URL

2. **Frontend (`logout()` functie)**:
   - Verwijdert alle cookies (voor alle paths en domains)
   - Cleart localStorage en sessionStorage
   - Opent Google logout in nieuw venster
   - Redirect naar `/login`

3. **Google OAuth**:
   - Gebruiker moet handmatig bevestigen bij Google logout
   - Dit cleart de OAuth sessie bij Google

### Waarom blijft Google me onthouden?

Google gebruikt **persistent sessions** die:
- Onafhankelijk zijn van je applicatie cookies
- Blijven bestaan tot je expliciet uitlogt bij Google
- Hergebruikt worden voor alle apps die Google OAuth gebruiken

**Oplossing**: Gebruik de Google logout URL die wordt geretourneerd, of ga direct naar https://accounts.google.com/logout

