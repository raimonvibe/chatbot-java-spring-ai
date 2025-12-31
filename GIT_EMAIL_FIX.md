# Git Email Configuratie Fix

## Probleem
Vercel geeft een foutmelding omdat `stefan@example.com` probeert te deployen, maar dit account is geen lid van het Vercel team.

**Status:** ✅ Opgelost - Email configuratie bijgewerkt naar `robertjanstefan@gmail.com`

## Oorzaak
Je lokale Git configuratie gebruikt `stefan@example.com` als email adres. Dit is een test/example email die niet gekoppeld is aan je GitHub/Vercel account.

## Oplossing

### Stap 1: Check je huidige configuratie
```bash
git config user.email
```

### Stap 2: Wijzig naar je echte email adres

**Voor alleen deze repository:**
```bash
git config user.email "jouw-echte-email@example.com"
```

**Voor alle repositories (aanbevolen):**
```bash
git config --global user.email "jouw-echte-email@example.com"
```

### Stap 3: Verifieer
```bash
git config user.email
```

## Belangrijk
- Gebruik het **zelfde email adres** dat gekoppeld is aan je **GitHub account**
- Dit email adres moet ook gekoppeld zijn aan je **Vercel account**
- Of voeg `stefan@example.com` toe aan je Vercel team (vereist Pro plan)

## Alternatieve Oplossing
Als je `stefan@example.com` wilt blijven gebruiken:
1. Voeg dit email adres toe aan je Vercel team (vereist Pro plan)
2. Of maak je repository public (gratis, maar minder veilig)

---

**Let op:** Oude commits blijven het oude email adres hebben. Alleen nieuwe commits gebruiken het nieuwe adres.

