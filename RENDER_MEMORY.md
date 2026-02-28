# Render memory limit and fixes

If your Web Service **exceeds its memory limit** and restarts (e.g. email from Render), or **crashes after website analysis**, use the following.

## Why it can happen

- **JVM heap**: The app uses a percentage of container RAM for the Java heap.
- **Chromium**: Headless crawl spawns Chromium; each instance can use **~200–400MB**.
- **Concurrent load**: Multiple crawls or traffic can push total usage over the instance limit.
- **Post-analysis**: Right after crawl, embedding indexing runs (Cohere API + vector store). Crawl + indexing together can spike memory.

## What we did in code

1. **Only one headless browser at a time**  
   A semaphore in `HeadlessFetchService` ensures at most **one** Chromium process. If another headless fetch is already running, new ones skip headless and use Jsoup-only (no extra Chromium = no extra memory spike).

2. **Lower JVM heap in Docker**  
   `MaxRAMPercentage` was reduced from 75% to **50%** so more memory is left for Chromium and the OS. On a 512MB instance, that’s ~256MB heap and ~256MB for Chromium + native + system.

3. **Limited crawl concurrency**  
   Website analysis uses a **fixed thread pool of 3**; all crawl work (including following links) runs on that pool. Previously, link-following could run many crawls in parallel and spike memory. Now at most 3 crawl tasks run at once.

4. **Batch indexing after analysis**  
   After crawl, content is indexed for the vector store in **batches of 10** instead of loading all pages at once, reducing peak memory during the post-analysis step.

5. **Optional: disable headless on small instances**  
   If you’re on a **free or very small** instance (e.g. 512MB), you can turn off headless crawl so the app never starts Chromium:

   - In Render: **Environment** → add:
     - `HEADLESS_CRAWL_ENABLED` = `false`
   - Or set `app.website-analysis.headless-enabled=false` in config.

   The app will still run; only headless (SPA) crawl is disabled. Crawl will use Jsoup only (and title/meta for SPAs).

## Recommended actions

| Situation | Action |
|-----------|--------|
| **Instance keeps OOM’ing** | Set `HEADLESS_CRAWL_ENABLED=false` and redeploy, **or** upgrade to an instance with **at least 2GB RAM** if you want headless/SPA crawl. |
| **Traffic spikes** | Consider scaling (more instances or larger instance). |
| **Need more heap** | Set `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60` (or another value) in Render env; keep in mind Chromium still needs space. |

## Checking logs

- After deploy, check Render logs for:
  - `Headless fetch skipped: another headless browser in use` → semaphore is doing its job (only one Chromium at a time).
  - `Headless fetch disabled by config` → headless is off via `HEADLESS_CRAWL_ENABLED=false`.

If OOM persists with headless **disabled**, the cause is likely JVM or other components; consider a larger instance or profiling.
