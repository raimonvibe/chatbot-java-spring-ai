# OAuth2 Troubleshooting Guide

## "Invalid credentials" Error

Als je een "Invalid credentials" fout krijgt bij OAuth2 login, controleer het volgende:

### 1. Redirect URI in Google Cloud Console

De redirect URI in Google Cloud Console **moet exact overeenkomen** met:

**Voor lokale ontwikkeling:**
```
http://localhost:8081/login/oauth2/code/google
```

**Voor productie:**
```
https://jouw-domein.com/login/oauth2/code/google
```

### 2. Controleer Google Cloud Console Instellingen

1. Ga naar [Google Cloud Console](https://console.cloud.google.com/)
2. Selecteer je project
3. Ga naar "APIs & Services" → "Credentials"
4. Klik op je OAuth 2.0 Client ID
5. Controleer:

   **Authorized JavaScript origins:**
   - `http://localhost:8081` (voor development)
   - `https://jouw-domein.com` (voor productie)

   **Authorized redirect URIs:**
   - `http://localhost:8081/login/oauth2/code/google` (voor development)
   - `https://jouw-domein.com/login/oauth2/code/google` (voor productie)

### 3. Controleer Environment Variables

Zorg dat in je `.env` file (of Render environment variables):

```bash
GOOGLE_CLIENT_ID=je-client-id-hier
GOOGLE_CLIENT_SECRET=je-client-secret-hier
```

**Belangrijk:**
- Geen quotes rond de waarden
- Geen extra spaties
- Volledige client ID en secret (niet afgekort)

### 4. Veelvoorkomende Fouten

#### Fout: "redirect_uri_mismatch"
- **Oorzaak**: Redirect URI in Google Cloud Console komt niet overeen
- **Oplossing**: Controleer dat de redirect URI exact is: `http://localhost:8081/login/oauth2/code/google`

#### Fout: "invalid_client"
- **Oorzaak**: Client ID of Client Secret is incorrect
- **Oplossing**: Controleer je `.env` file en Google Cloud Console

#### Fout: "access_denied"
- **Oorzaak**: Gebruiker heeft toegang geweigerd of OAuth consent screen is niet correct geconfigureerd
- **Oplossing**: Controleer OAuth consent screen in Google Cloud Console

### 5. Test de Configuratie

1. Start de backend: `cd backend && mvn spring-boot:run`
2. Open: `http://localhost:8081/oauth2/authorization/google`
3. Controleer de browser console en backend logs voor specifieke foutmeldingen

### 6. Backend Logs Controleren

Check de backend logs voor meer details:
```bash
tail -f /tmp/backend.log | grep -i oauth
```

### 7. OAuth Consent Screen

Zorg dat je OAuth consent screen is geconfigureerd:
1. Ga naar "APIs & Services" → "OAuth consent screen"
2. Zorg dat je app is gepubliceerd (of test users zijn toegevoegd)
3. Voor development: voeg je eigen Google email toe als test user

### 8. Quick Fix Checklist

- [ ] Redirect URI in Google Cloud Console: `http://localhost:8081/login/oauth2/code/google`
- [ ] JavaScript origin in Google Cloud Console: `http://localhost:8081`
- [ ] `GOOGLE_CLIENT_ID` is correct in `.env`
- [ ] `GOOGLE_CLIENT_SECRET` is correct in `.env`
- [ ] Backend draait op poort 8081
- [ ] OAuth consent screen heeft test users (voor development)
- [ ] Geen quotes of extra spaties in environment variables

