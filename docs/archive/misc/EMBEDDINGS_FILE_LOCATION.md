# Waar wordt het embeddings bestand geladen?

## Huidige Situatie

Het grote `bible_embeddings.json` bestand (662MB) wordt **NIET automatisch geladen**. Het moet handmatig worden geïmporteerd via de admin endpoint.

## Waar wordt het bestand gezocht?

### 1. Relatief pad (aanbevolen)
```java
// In EmbeddingImporterService.validateAndResolveFilePath()
File workingDir = new File(System.getProperty("user.dir"));  // Application working directory
File dataDir = new File(workingDir, "data");  // data/ subdirectory
```

**Voorbeelden:**
- `data/bible_embeddings.json` → wordt opgelost naar: `{workingDir}/data/bible_embeddings.json`
- `bible_embeddings.json` → wordt opgelost naar: `{workingDir}/bible_embeddings.json`

### 2. Absolute pad
- Moet binnen de working directory of data directory liggen
- Beveiliging voorkomt path traversal attacks

## Waar is de working directory?

### Lokaal (Development)
```bash
# Meestal de project root
/home/stefan/Documenten/web development/022chatbot-java-spring-ai/
```

### Op Render (Production)
```bash
# Render working directory
/opt/render/project/src/
```

Dus `data/bible_embeddings.json` wordt opgelost naar:
- Lokaal: `/home/stefan/.../022chatbot-java-spring-ai/data/bible_embeddings.json`
- Render: `/opt/render/project/src/data/bible_embeddings.json`

## Hoe wordt het geladen?

### Stap 1: Upload bestand
Het bestand moet eerst worden geüpload naar de server (lokaal of Render).

### Stap 2: Import via API
```bash
# Via admin endpoint
POST /api/admin/bible/import-embeddings?filePath=data/bible_embeddings.json
```

### Stap 3: Code laadt het bestand
```java
// EmbeddingImporterService.importEmbeddings()
File file = validateAndResolveFilePath(jsonFilePath);  // Valideert en resolveert pad
JsonNode root = objectMapper.readTree(new FileInputStream(file));  // Leest JSON
// ... verwerkt embeddings en slaat op in database
```

## Beveiliging

De `validateAndResolveFilePath()` methode:
1. ✅ Voorkomt path traversal (`..` in pad)
2. ✅ Controleert dat pad binnen working directory is
3. ✅ Vereist `.json` extensie
4. ✅ Normaliseert pad om security issues te voorkomen

## Samenvatting

| Locatie | Pad | Volledige pad (voorbeeld) |
|---------|-----|-------------------------|
| **Lokaal** | `data/bible_embeddings.json` | `/home/stefan/.../data/bible_embeddings.json` |
| **Render** | `data/bible_embeddings.json` | `/opt/render/project/src/data/bible_embeddings.json` |

**Belangrijk:**
- Bestand wordt NIET automatisch geladen bij startup
- Moet handmatig worden geüpload en geïmporteerd
- Pad is relatief ten opzichte van de application working directory
- Gebruik `data/bible_embeddings.json` als relatief pad

## Automatisch laden in de toekomst?

Momenteel is er **geen automatisch laden** van embeddings. Als je dit wilt toevoegen, zou je kunnen:

1. **Check bij startup** of embeddings bestaan
2. **Als bestand bestaat**, automatisch importeren
3. **Alleen als** `app.bible.auto-import-embeddings=true` is gezet

Maar dit is momenteel **niet geïmplementeerd** - het moet handmatig via de admin endpoint.

