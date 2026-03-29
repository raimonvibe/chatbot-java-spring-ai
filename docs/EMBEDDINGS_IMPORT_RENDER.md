# Importing Bible Embeddings on Render (No Large File on GitHub)

The `bible_embeddings.json` file is too large for GitHub. On Render the filesystem is **ephemeral** (e.g. `/tmp` and the app directory are wiped on deploy/restart), so you cannot rely on uploading the file once in Shell — it will be gone after the next deploy.

---

## When the file is too large for Google Drive (or a single URL)

If the full file (e.g. 400MB+) is too large to download from Google Drive or another host in one go, **split it into smaller parts** and use **multiple URLs**. The app will download each part, import it into the DB, then move to the next.

### Step 1: Split the file locally

Run (requires enough RAM to load the full JSON, e.g. 2GB free for a 600MB file):

```bash
python scripts/split-embeddings-for-import.py data/bible_embeddings.json --max-mb 80 --out-dir data/embedding_parts
```

This creates `data/embedding_parts/bible_embeddings_part_1.json`, `part_2.json`, … each under ~80MB. Adjust `--max-mb` if your host has stricter limits (e.g. 50).

### Step 2: Upload each part and get direct download URLs

- Upload **each** part file to Google Drive (or Dropbox, etc.).
- Get a **direct download URL** for each part (e.g. Google Drive:  
  `https://drive.usercontent.google.com/download?id=FILE_ID&export=download&confirm=t`).

### Step 3: Set one env var on Render

In **Render Dashboard → Backend Service → Environment**, set **only**:

| Variable | Value |
|----------|--------|
| `IMPORT_EMBEDDINGS_URLS` | Comma-separated list of direct download URLs, e.g. `https://drive.usercontent.google.com/download?id=ID1&export=download&confirm=t,https://drive.usercontent.google.com/download?id=ID2&export=download&confirm=t` |

Do **not** set `IMPORT_EMBEDDINGS_FILE` or `IMPORT_EMBEDDINGS_URL` when using `IMPORT_EMBEDDINGS_URLS`. Save so the service restarts.

### Step 4: What happens

On startup the app downloads part 1, imports it, deletes the temp file, then part 2, etc. Logs will show progress (e.g. `Part 1/7: imported N verses`). When all parts are done: `✅ Multi-part embedding import completed! Total verses: N`.

### Step 5: After success

Remove the `IMPORT_EMBEDDINGS_URLS` environment variable and save. Embeddings stay in the database.

---

## Single file: Auto-download on startup (no Shell needed)

The app can **download the file from a URL on startup** and then import it into the database. After that, you remove the env vars; the data stays in PostgreSQL.

### Step 1: Host the file somewhere

- **Google Drive:** Upload `bible_embeddings.json`, share “Anyone with the link”, then get a **direct download** URL.  
  For large files, use the format:
  `https://drive.usercontent.google.com/download?id=YOUR_FILE_ID&export=download&confirm=t`
  (Replace `YOUR_FILE_ID` with the ID from the share link, e.g. from `https://drive.google.com/file/d/YOUR_FILE_ID/view`.)
- **Or** use Dropbox (link with `?dl=1`), WeTransfer, or any HTTPS URL that returns the raw file.

### Step 2: Set environment variables in Render

In **Render Dashboard → your Backend Service → Environment**:

| Variable | Value |
|----------|--------|
| `IMPORT_EMBEDDINGS_FILE` | `/tmp/data/bible_embeddings.json` |
| `IMPORT_EMBEDDINGS_URL`  | Your direct download URL (e.g. the Google Drive URL above) |

You do **not** need to add `import-embeddings` to `SPRING_PROFILES_ACTIVE`; the runner is always registered and only runs when `IMPORT_EMBEDDINGS_FILE` is set.

Save so the service restarts.

### Step 3: What happens

1. On startup the app sees `IMPORT_EMBEDDINGS_FILE` and looks for the file.
2. File is missing → it uses `IMPORT_EMBEDDINGS_URL` to **download** the file to `/tmp/data/bible_embeddings.json`.
3. It then imports from that file into the **database** (PostgreSQL).
4. Logs will show: `✅ Embedding import completed successfully!` and `📊 Imported embeddings for N verses`.

### Step 4: After a successful import

1. In Render Environment, **remove**:
   - `IMPORT_EMBEDDINGS_FILE`
   - `IMPORT_EMBEDDINGS_URL`
2. Save (service restarts). The app will log “IMPORT_EMBEDDINGS_FILE not set. Skipping embedding import.” — that’s expected. Embeddings remain in the database.

---

## Why not only `IMPORT_EMBEDDINGS_FILE=data/bible_embeddings.json`?

- `data/bible_embeddings.json` is **not** in the repo (too large), so it never exists on Render.
- Writing the file in Render Shell to `/tmp` or `data/` only lasts until the next deploy or restart; then the filesystem is reset.
- So you must either **download on each run** (using `IMPORT_EMBEDDINGS_URL`) until import succeeds, or use the manual Shell flow below and trigger import before any restart.

---

## Alternative: Manual download in Render Shell, then immediate import

If you prefer not to use a public URL:

1. In **Render Shell**, create the dir and download (replace with your link):
   ```bash
   mkdir -p /tmp/data
   cd /tmp/data
   wget --no-check-certificate "YOUR_DIRECT_DOWNLOAD_URL" -O bible_embeddings.json
   ls -lh bible_embeddings.json
   ```
2. In **Environment**, set **before** starting the app (or already set):
   - `IMPORT_EMBEDDINGS_FILE` = `/tmp/data/bible_embeddings.json`
3. **Restart the service** so the app starts and runs the import. The runner will find the file (within its retry window) and import into the DB.
4. After “Embedding import completed successfully!”, remove `IMPORT_EMBEDDINGS_FILE` and redeploy/restart as needed. The file in `/tmp` will disappear on next restart, but the data is already in the database.

---

## Optional: Retry tuning

If the download or file is slow, you can set:

- `IMPORT_EMBEDDINGS_MAX_RETRIES` — default `10`
- `IMPORT_EMBEDDINGS_RETRY_DELAY_MS` — default `30000` (30 seconds)

---

## References in this repo

- **EmbeddingImportRunner** (`backend/.../config/EmbeddingImportRunner.java`) — supports single `IMPORT_EMBEDDINGS_URL` or multiple `IMPORT_EMBEDDINGS_URLS` (comma-separated); downloads each (part) and imports.
- **scripts/split-embeddings-for-import.py** — splits a large `bible_embeddings.json` into smaller part files for the multi-URL flow.
- **docs/archive/misc/** — older step-by-step and “split-file” (base64) options; the URL-based flow above is simpler and recommended.
