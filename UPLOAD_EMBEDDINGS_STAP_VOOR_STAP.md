# Stap-voor-stap: Embeddings Bestand Uploaden naar Render

## Overzicht

Je hebt een 662MB `bible_embeddings.json` bestand lokaal. Dit moet naar Render worden geüpload voordat je het kunt importeren.

## Methode 1: Via Cloud Storage (Aanbevolen) ⭐

Dit is de **makkelijkste en meest betrouwbare** methode voor grote bestanden.

### Stap 1: Upload naar Cloud Storage

**Optie A: Google Drive**
1. Ga naar [Google Drive](https://drive.google.com)
2. Upload `data/bible_embeddings.json`
3. Rechtsklik op bestand → "Get link" → "Anyone with the link"
4. Kopieer de link (bijvoorbeeld: `https://drive.google.com/file/d/ABC123/view?usp=sharing`)

**Optie B: Dropbox**
1. Upload naar Dropbox
2. Rechtsklik → "Copy link"
3. Vervang `?dl=0` door `?dl=1` in de URL (voor directe download)

**Optie C: WeTransfer / SendAnywhere**
1. Upload naar WeTransfer of SendAnywhere
2. Kopieer de download link
3. Link is 7 dagen geldig

### Stap 2: Download in Render Shell

1. **Open Render Shell:**
   - Ga naar [Render Dashboard](https://dashboard.render.com)
   - Selecteer je **Backend Service** (niet de database!)
   - Klik op **"Shell"** tab
   - Dit opent een terminal

2. **Maak data directory:**
   ```bash
   mkdir -p data
   cd data
   ```

3. **Download bestand:**
   ```bash
   # Voor Google Drive (vervang FILE_ID met je bestand ID)
   curl -L "https://drive.google.com/uc?export=download&id=FILE_ID" -o bible_embeddings.json
   
   # OF voor directe download link (WeTransfer, Dropbox met ?dl=1, etc.)
   curl -L "JE_DOWNLOAD_LINK_HIER" -o bible_embeddings.json
   ```

4. **Verifieer download:**
   ```bash
   ls -lh bible_embeddings.json
   # Moet ~662M zijn
   ```

### Stap 3: Import via API

Zie [EMBEDDINGS_IMPORT_RENDER.md](./EMBEDDINGS_IMPORT_RENDER.md) voor import instructies.

---

## Methode 2: Via Render Shell + Base64 (Voor kleine delen)

Als cloud storage niet werkt, kun je het bestand in delen uploaden via base64.

### Stap 1: Split bestand lokaal

```bash
# Split in delen van 100MB
split -b 100M data/bible_embeddings.json data/bible_embeddings.json.part
```

### Stap 2: Upload elk deel

```bash
# In Render Shell
cd data
# Plak base64 encoded content van elk deel
echo "BASE64_CONTENT_HIER" | base64 -d >> bible_embeddings.json
# Herhaal voor elk deel
```

**Let op:** Dit is tijdrovend en foutgevoelig. Methode 1 is beter!

---

## Methode 3: Via Git LFS (Geavanceerd)

Als je Git LFS hebt ingesteld:

```bash
# Lokaal
git lfs track "data/bible_embeddings.json"
git add .gitattributes data/bible_embeddings.json
git commit -m "Add embeddings file via LFS"
git push origin main

# Render haalt het automatisch op bij deploy
```

**Let op:** Git LFS kost geld op GitHub voor grote bestanden. Niet aanbevolen voor 662MB.

---

## Methode 4: Via SCP/SFTP (Als Render het ondersteunt)

Render ondersteunt normaal gesproken geen directe SCP/SFTP, maar je kunt proberen:

```bash
# Van je lokale machine
scp data/bible_embeddings.json render@your-service.onrender.com:/opt/render/project/src/data/
```

**Let op:** Dit werkt meestal niet op Render. Gebruik Methode 1.

---

## Aanbevolen Workflow

### ✅ Stap-voor-stap (Methode 1 - Cloud Storage)

1. **Lokaal:**
   ```bash
   # Verifieer bestand bestaat
   ls -lh data/bible_embeddings.json
   ```

2. **Upload naar Google Drive:**
   - Upload `data/bible_embeddings.json` naar Google Drive
   - Deel link (Anyone with link)
   - Kopieer bestand ID uit URL

3. **Render Shell:**
   ```bash
   # Maak directory
   mkdir -p data
   cd data
   
   # Download (vervang FILE_ID met je Google Drive bestand ID)
   curl -L "https://drive.google.com/uc?export=download&id=FILE_ID" -o bible_embeddings.json
   
   # Verifieer download
   ls -lh bible_embeddings.json
   # Moet ~662M tonen
   ```

4. **Import:**
   - Zet `SPRING_PROFILES_ACTIVE=local` in Render environment variables
   - Wacht op restart
   - Call: `POST /api/admin/bible/import-embeddings?filePath=data/bible_embeddings.json`

5. **Cleanup:**
   - Verwijder `SPRING_PROFILES_ACTIVE=local`
   - Optioneel: verwijder bestand van Render (`rm data/bible_embeddings.json`)

---

## Troubleshooting

### Download faalt

**Probleem:** `curl` download is te groot of faalt
**Oplossing:** 
- Gebruik `wget` in plaats van `curl`:
  ```bash
  wget -O bible_embeddings.json "JE_LINK"
  ```

### Google Drive download werkt niet

**Probleem:** Google Drive vraagt om bevestiging voor grote bestanden
**Oplossing:**
1. Gebruik `gdown` (Python tool):
   ```bash
   pip install gdown
   gdown JE_FILE_ID -O bible_embeddings.json
   ```
2. Of gebruik Dropbox/WeTransfer in plaats daarvan

### Onvoldoende schijfruimte

**Probleem:** Render heeft niet genoeg ruimte
**Oplossing:**
- Upgrade Render plan (meer storage)
- Of gebruik externe storage (S3) en download direct naar database

### Bestand corrupt na download

**Probleem:** Download is niet compleet
**Oplossing:**
```bash
# Verifieer bestandsgrootte
ls -lh bible_embeddings.json
# Moet ~662M zijn

# Verifieer JSON format
head -20 bible_embeddings.json
# Moet geldige JSON zijn
```

---

## Snelle Referentie

| Methode | Moeilijkheid | Betrouwbaarheid | Snelheid |
|---------|--------------|-----------------|----------|
| Cloud Storage | ⭐ Easy | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| Base64 Split | ⭐⭐⭐ Hard | ⭐⭐ | ⭐⭐ |
| Git LFS | ⭐⭐ Medium | ⭐⭐⭐ | ⭐⭐⭐ |
| SCP/SFTP | ⭐⭐⭐ Hard | ⭐ | ⭐⭐ |

**Aanbeveling:** Gebruik **Methode 1 (Cloud Storage)** - het is het makkelijkst en meest betrouwbaar!

