# Uploading the Embeddings File to Render (Next Time)

Use this when you have `bible_embeddings.json` locally and want to load it on your Render backend (e.g. 2GB RAM instance). The file is uploaded to `/tmp/data/` and imported into the database in one run.

---

## Important: Why `/tmp` Disappears

On Render, **`/tmp` is ephemeral**: it is cleared on every **deploy or restart**. So:

1. **Do not redeploy** after uploading the file until the import has finished.
2. After you see "Embedding import completed successfully!", the data is in **PostgreSQL**. You can then remove the env vars and redeploy; `/tmp` will be cleared but the embeddings stay in the DB.

---

## Steps (for next time)

### 1. Set env vars on Render first

In **Render Dashboard → your Backend Service → Environment**:

| Variable | Value |
|----------|--------|
| `IMPORT_EMBEDDINGS_FILE` | `/tmp/data/bible_embeddings.json` |
| `IMPORT_EMBEDDINGS_MAX_RETRIES` | `15` or `20` (so the app waits while you upload) |
| `IMPORT_EMBEDDINGS_RETRY_DELAY_MS` | `30000` (30 sec between checks; optional) |

Save so the service restarts **once**.

### 2. Upload the file via Render Shell

1. Open **Render Dashboard → your Backend Service → Shell**.
2. Run:
   ```bash
   mkdir -p /tmp/data
   cd /tmp/data
   ```
3. Upload `bible_embeddings.json` to `/tmp/data/bible_embeddings.json`:
   - **Google Drive:** The "Share" link (`https://drive.google.com/file/d/XXX/view?usp=sharing`) does **not** work with wget — it returns HTML. Use the script to get the direct download URL:
     ```bash
     # On your machine (or in Render Shell if you have the script):
     ./scripts/google-drive-download-url.sh "https://drive.google.com/file/d/YOUR_FILE_ID/view?usp=sharing"
     ```
     It prints the correct URL. Then in Render Shell:
     ```bash
     wget -O bible_embeddings.json "https://drive.usercontent.google.com/download?id=YOUR_FILE_ID&export=download&confirm=t"
     ```
   - Or any other **direct download URL** (e.g. temporary host):
     ```bash
     wget -O bible_embeddings.json "YOUR_DIRECT_DOWNLOAD_URL"
     ```
   - Or use whatever upload method Render Shell supports (e.g. paste, or upload from your machine if available).
4. Check the file:
   ```bash
   ls -lh /tmp/data/bible_embeddings.json
   ```

Do **not** change Environment or trigger a new deploy while uploading. The app is already running and will keep checking for the file (up to `IMPORT_EMBEDDINGS_MAX_RETRIES` × delay). Once the file exists, it imports into the DB.

### 3. Wait for import to finish

In the service **Logs**, wait for:

- `✅ File found: /tmp/data/bible_embeddings.json (... bytes)`
- `✅ Embedding import completed successfully!`
- `📊 Imported embeddings for N verses`

### 4. After success

1. In **Environment**, **remove** `IMPORT_EMBEDDINGS_FILE` (and the retry vars if you want).
2. Save. The service will restart; `/tmp` will be cleared, but embeddings are already in the database.

---

## If the file is not found in time

- Increase `IMPORT_EMBEDDINGS_MAX_RETRIES` (e.g. 30) and/or `IMPORT_EMBEDDINGS_RETRY_DELAY_MS`, save so the service restarts, then upload the file in Shell before the retries run out.
- Or use **URL-based import**: set `IMPORT_EMBEDDINGS_URL` to a direct download URL; the app will download and import on startup. See **docs/EMBEDDINGS_IMPORT_RENDER.md**.

---

## References

- **scripts/google-drive-download-url.sh** — pass your Drive share link; it prints the direct download URL for wget.
- **EmbeddingImportRunner** — uses `IMPORT_EMBEDDINGS_FILE` and optional `IMPORT_EMBEDDINGS_URL`.
- **docs/EMBEDDINGS_IMPORT_RENDER.md** — single URL, multiple URLs, and split-file options.
