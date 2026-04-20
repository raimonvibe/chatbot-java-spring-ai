# Chatbot creation speed – research summary

## Why it was faster before (security & analysis changes)

**Yes – we added proper security and better analysis, and that’s why it takes longer now.**

1. **Size estimation before create/analyze** (from **Dec 31** – `WebsiteSizeEstimator` in commit 3f536a8, then used in controller)
   - **Before:** No size check → create/analyze could start right away.
   - **Now:** We run `estimateSize(url)` first: try **sitemap.xml** (5s timeout) → **robots.txt** (5s) → **homepage sampling** (5s). So the request can sit **5–15 seconds** before any crawl starts. This was added to enforce plan limits and avoid scanning huge sites (cost/abuse).
   - **Security:** Phase 3 (aae1231) added SSRF validation inside the estimator so we don’t hit internal/metadata URLs.

2. **More thorough website analysis** (**Feb 6** – commit 466cd47: “Website analysis: better extraction, sitemap seeds, URL norm; SSRF hardening”)
   - **Before:** Simpler crawl (e.g. start from homepage, follow links; no sitemap).
   - **Now:** We fetch **sitemap** first, use it to **seed** the crawl, **normalize URLs**, and run **SSRF validation on every URL** before fetching. So we discover more pages and do more validation → better quality and safer, but more work and more time.

3. **Scan/cost enforcement** (**Feb 6** – bc82b2e: PlanLimits, `WebsiteScanAudit`, cost cap)
   - We **estimate cost** and **check scan limit** before starting. That uses the same `estimateSize()` result, so no extra network delay, but we do more checks before the crawl.

So the slowdown is from: **(a)** the new **size-estimation step** (5–15s) before create/analyze, and **(b)** **sitemap-based discovery + SSRF on every URL** making the actual analysis phase more thorough and a bit slower. Security and plan enforcement are the reasons; we didn’t break anything – we added steps that take time.

---

## What happens when you paste a URL and create a chatbot

### 1. **Onboarding / “Create from URL”** (dashboard or onboarding page)

- **Before the API responds**
  - **Size estimation** runs: `WebsiteSizeEstimator.estimateSize(websiteUrl)` (for the “website too large” check).
  - It tries, in order: **sitemap.xml** (timeout 5s) → **robots.txt** (5s) → **homepage sampling** (5s).
  - So the **create** request can take **~5–15 seconds** before the chatbot is even created, depending on the site.
- **After create (background)**
  - Analysis runs **async**: crawl (up to 50 pages, 30s timeout per page), then **indexing** (one Cohere embedding call per page, **sequential**), then **Christian content** (one more embedding + DB comparison).
  - The preview/chat page shows “Setting up…” and polls `analysis-status` until ready (up to 2 min). So the **perceived** time is “until analysis is ready”, not the create response time.

### 2. **“Analyze” button** (POST `/api/chatbots/{id}/analyze`)

- **Size estimation** runs again (same 5–15s possible).
- **Then**, for sites with **estimated pages ≤ 50**:
  - The backend **waits synchronously** for analysis + indexing + Christian content, up to **120 seconds**.
  - So the **HTTP request** can block for up to **2 minutes**; the UI waits on this call.
- For larger sites it returns quickly with `"status": "analysis_started"` and work continues in the background.

## Previous code changes that affect “how long it takes”

### Onboarding was made **faster** (commit `5d08748` – “Faster chatbot create + accurate upgrade modal”)

- **Before:** For small sites (≤50 pages), onboarding **blocked** the create request for up to 120s while analysis + indexing + Christian content ran. So “paste URL → see chatbot” could take 1–2 minutes.
- **After:** Onboarding **always** returns 201 quickly and runs analysis in the background. The create response is fast; the user then sees “Setting up…” until analysis is ready.

So **onboarding create** is not slower than before; it was deliberately made faster. The remaining delay is:

1. **Size estimation** before create (5–15s possible).
2. **Background** work (crawl + indexing + Christian content) while the user sees “Setting up…”.

### What can still feel “longer than usual”

1. **Size estimation**  
   If the site’s sitemap/robots/homepage is slow or timing out, the **create** request can sit for 5–15 seconds before returning. That’s the main candidate for “create feels slow” if you didn’t have this check before.

2. **Using “Analyze” on an existing chatbot**  
   For sites ≤50 pages, the **Analyze** endpoint still uses the **sync path** (wait up to 120s). So clicking “Analyze” can freeze the UI for up to 2 minutes. That’s a different flow from “create from URL” but can feel like “chatbot creation / analysis takes forever”.

3. **Indexing is one-by-one**  
   `AiChatbotService.indexWebsiteContent()` calls `vectorStore.add(List.of(document))` in a loop: **one embedding API call per page**, sequentially. 20 pages ⇒ 20 Cohere calls in sequence ⇒ tens of seconds extra. No batching.

4. **Christian content**  
   After indexing, `findRelevantVerses()` runs (one embedding for website context + similarity vs. verses). Adds a few seconds.

## Recommendations

1. **Make POST `/api/chatbots/{id}/analyze` always async**  
   Remove the sync wait for small sites (the “fast path” that blocks up to 120s). Always return immediately with `"status": "analysis_started"` and let the frontend poll `analysis-status`. That way “Analyze” never blocks the UI for minutes.

2. **Batch embedding in `indexWebsiteContent`**  
   Collect all documents and call `vectorStore.add(documents)` once (or in chunks, e.g. 10–20 per batch) so the embedding model/Cohere can process multiple texts per request. This reduces total indexing time.

3. **Speed up size estimation (keep security, reduce wait)**  
   Size estimation is what adds 5–15s before create. You can keep SSRF and plan checks but reduce delay:
   - **Cap total estimation time** (e.g. 2s): try sitemap with 1s timeout; if nothing, try robots with 1s; if still nothing, use default 10 pages. So worst case ~2s instead of 15s.
   - Or **shorter timeouts** in `WebsiteSizeEstimator`: e.g. 2000 ms per step instead of 5000 ms.

4. **Check production**  
   Confirm Cohere/API and network latency haven’t increased; that would make every embedding call slower and compound the one-by-one indexing.

## Summary

- **Onboarding create** was intentionally made faster (no 120s block). Remaining delay is **size estimation** (5–15s) and **background** analysis.
- The **Analyze** endpoint can still block for up to **120 seconds** for small sites; making it always async would remove that “longer than usual” feeling when using Analyze.
- **Indexing** is a major bottleneck (one embedding per page); batching would reduce total time.
