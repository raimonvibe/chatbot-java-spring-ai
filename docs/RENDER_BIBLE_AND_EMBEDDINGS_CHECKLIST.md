# Render: Bible Data & Embeddings Checklist (new / starting over)

Use this when you create a **new** Render web service or start with a fresh database.

---

## 1. Bible data (load verses into the database)

The app loads Bible verses from JSON files bundled in the JAR on first startup **if** auto-load is enabled.

| Env var | Value | Notes |
|--------|--------|--------|
| `BIBLE_AUTO_LOAD` | `true` | Default is `true`; only set if you need to turn it off. |
| `BIBLE_LOAD_OLD_TESTAMENT` | `false` or `true` | `false` = New Testament only (default). `true` = Old + New. |
| `FORCE_RELOAD_BIBLE_DATA` | *(don’t set)* or `true` | Only set to `true` if you want to **reload** and overwrite existing verses. Leave unset for a new app. |

**Check:** After first deploy, logs should show something like “Successfully loaded … Bible verses”.  
**API:** `GET https://<your-backend>.onrender.com/api/admin/bible/status` — `dataLoaded` should be `true`, `totalVerses` > 0.

---

## 2. Embeddings (import pre-generated vectors into the DB)

Bible verses need embeddings for semantic search. You can either **import** a pre-built `bible_embeddings.json` (recommended) or generate them at runtime (slow and costly).

### Option A: Single file – auto-download on startup (easiest)

1. Host `bible_embeddings.json` at a **direct download URL** (e.g. Google Drive direct link).
2. In **Render Dashboard → Backend Service → Environment**, add:

| Variable | Value |
|----------|--------|
| `IMPORT_EMBEDDINGS_FILE` | `/tmp/data/bible_embeddings.json` |
| `IMPORT_EMBEDDINGS_URL` | Your direct download URL (e.g. `https://drive.usercontent.google.com/download?id=FILE_ID&export=download&confirm=t`) |

3. Save (service restarts). The app will download the file and import embeddings into PostgreSQL.
4. When logs show **“Embedding import completed successfully!”**, **remove** both `IMPORT_EMBEDDINGS_FILE` and `IMPORT_EMBEDDINGS_URL` and save again. Data stays in the DB.

### Option B: Large file – split into parts (multiple URLs)

If the file is too large for one URL (e.g. 400MB+):

1. Split locally:  
   `python scripts/split-embeddings-for-import.py data/bible_embeddings.json --max-mb 80 --out-dir data/embedding_parts`
2. Upload each part and get a **direct download URL** for each.
3. On Render, set **only**:

| Variable | Value |
|----------|--------|
| `IMPORT_EMBEDDINGS_URLS` | Comma-separated list of part URLs, e.g. `https://...,https://...,https://...` |

4. Save. The app downloads and imports each part in order.
5. After success, **remove** `IMPORT_EMBEDDINGS_URLS`. Embeddings remain in the database.

---

## 3. Optional env vars (embeddings import)

| Variable | Default | Purpose |
|----------|---------|---------|
| `IMPORT_EMBEDDINGS_MAX_RETRIES` | `10` | Retries if file not yet available. |
| `IMPORT_EMBEDDINGS_RETRY_DELAY_MS` | `30000` | Delay between retries (ms). |

---

## 4. Summary for “starting over” on Render

**Minimum for Bible data:**

- `BIBLE_AUTO_LOAD` = `true` (or leave unset; default is true).

**Minimum for embeddings (one-time import):**

- `IMPORT_EMBEDDINGS_FILE` = `/tmp/data/bible_embeddings.json`  
- `IMPORT_EMBEDDINGS_URL` = your direct download URL  

Then after a successful run, remove `IMPORT_EMBEDDINGS_FILE` and `IMPORT_EMBEDDINGS_URL`.

**Verify:**

- `GET /api/admin/bible/status` → `dataLoaded: true`, `embeddingsReady: true`, `versesWithEmbeddings` = total verses.

More detail: `BIBLE_DATA_SETUP.md`, `docs/EMBEDDINGS_IMPORT_RENDER.md`.
