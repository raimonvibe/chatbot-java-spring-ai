# Website analysis & chatbot creation – technical review

## Summary

- **Chatbot creation** (onboarding and dashboard) and **website analysis** flows are technically correct. One fix was applied: **re-scan now clears previous website content** so duplicates are not accumulated.
- Security (SSRF, plan limits, scan/cost caps) is applied in the right places.
- Frontend correctly creates then polls `analysis-status` until ready.

---

## 1. Chatbot creation

### Onboarding (`POST /api/chatbots/onboarding`)

- **Auth:** Requires `CustomOAuth2User`; 401 if missing.
- **Validation:** URL required; `https://` added if missing.
- **Limits:** Onboarding only when user has 0 chatbots; subscription or preview required.
- **Size check:** In preview mode, `estimateSize(websiteUrl)` runs; 402 if over plan limit (with suggested plan).
- **Create:** `generateNameFromUrl` + defaults (Christian messaging on, active, embed code). `chatbotService.createChatbotEnforcingLimit(chatbot, user, 1)` so max 1 chatbot for onboarding.
- **Analysis:** `analyzeWebsite(savedChatbot)` run async; 201 returned immediately. On completion: `indexWebsiteContent`, then `findRelevantVerses`, set verse and Jesus teachings, save. Exceptions in the callback are logged; 201 already sent.

**Verdict:** Correct. No blocking wait; preview page can poll until ready.

### Dashboard create (`POST /api/chatbots`)

- Same size check in preview mode (estimateSize, 402 if over limit).
- Creates chatbot with request body (name, description, websiteUrl, etc.). No automatic analysis trigger in the snippet reviewed; analysis is triggered separately via `POST /api/chatbots/{id}/analyze` when the user clicks Analyze.

**Verdict:** Correct.

---

## 2. Website analysis

### Analyze endpoint (`POST /api/chatbots/{id}/analyze`)

- **Auth & access:** User must own chatbot (`verifyAccess`).
- **Scan limit:** `rateLimitingService.checkScanLimit(user)`; 402 when over daily limit.
- **Size:** `estimateSize(chatbot.getWebsiteUrl())`; 403 if above plan max pages.
- **Cost:** Estimated cost and `costTrackingService.checkCostLimit(user, estimatedCost)`; 403 if over cap.
- **Audit:** `WebsiteScanAudit` saved before starting (persists even if chatbot is deleted).
- **Run:** `websiteAnalysisService.analyzeWebsite(chatbot)` then `thenAccept(onAnalysisDone)`. Response is always `"status": "analysis_started"` (no blocking).
- **onAnalysisDone:** Loads chatbot, `indexWebsiteContent(c)`, then Christian content (findRelevantVerses, set verse + Jesus teachings, save).

**Verdict:** Correct and secure.

### WebsiteAnalysisService.analyzeWebsite

- **Input:** Validates `chatbotId` and `baseUrl`; returns completed empty future if missing.
- **SSRF:** `urlValidationService.isValidAndSafe(baseUrl)` before any network call; same for each seed and crawl URL; after redirect, `finalUrl` is validated.
- **Re-scan:** **Fixed.** Before crawling, existing website content for the chatbot is removed with `websiteContentRepository.deleteByChatbot(ref)` so re-analyze replaces content instead of appending duplicates.
- **Crawl:** Seeds = homepage + sitemap URLs (same domain, validated). `crawlWebsite` respects `maxPages`, `maxDepth`, `visitedUrls`, and SSRF on every URL. Content is saved per page; `extractPageContent` builds title + meta + content and skips pages with &lt; 50 chars.
- **Threading:** Uses `executorService`; minimal `Chatbot ref` (id only) for saving to avoid detached-entity issues.

**Verdict:** Correct. Re-scan no longer accumulates duplicate DB rows.

### Indexing (AiChatbotService.indexWebsiteContent)

- Loads `websiteContentRepository.findByChatbot(chatbot)` (after our fix, this is only the new content from the last run).
- For each content: build Spring AI `Document`, `vectorStore.add(List.of(document))`, set `isIndexed(true)` and `vectorId`, save.
- **Vector store:** Retrieval uses `filterExpression("chatbotId == '" + chatbot.getId() + "'")` so only this chatbot’s documents are used. With in-memory `SimpleVectorStore`, old vectors from a previous scan are not removed when we re-scan (no delete-by-filter in this store). So after re-scan you can have both old and new vectors for the same chatbot until the app restarts. For production vector stores (e.g. Pinecone), you could add a step to delete by `chatbotId` before re-indexing if the store supports it.

**Verdict:** Correct. DB and indexing no longer duplicate content; in-memory store may keep stale vectors until restart.

---

## 3. Analysis status & frontend

- **GET /api/chatbots/{id}/analysis-status:** Returns `ready` (true if any `WebsiteContent` for the chatbot has `isIndexed == true`) and `pagesIndexed`. Used unauthenticated so the preview page can poll.
- **Frontend (chatbot [id] page):** Loads chatbot; if `websiteUrl` is set, calls `pollUntilAnalysisReady(chatbotId)` (2s interval, 2 min timeout), then sets `analysisLoading = false`. So the fancy loader is shown until at least one page is indexed.

**Verdict:** Correct. Polling and “ready” semantics match backend.

---

## 4. Fix applied

- **Repository:** `WebsiteContentRepository` now has `void deleteByChatbot(Chatbot chatbot);`
- **WebsiteAnalysisService:** At the start of the async analysis (after SSRF check, before crawl), calls `websiteContentRepository.deleteByChatbot(ref)` so each run replaces previous website content instead of appending. Exceptions are caught and logged so a delete failure does not block the crawl.

---

## 5. SPA / client-rendered sites (e.g. Lagos Health Navigator, Vercel)

**Why "still being analyzed" after a minute:** Sites like https://lagos-health-navigator.vercel.app/ are often **client-side rendered** (React/Next.js on Vercel). The crawler uses **Jsoup**, which does **not** run JavaScript. It only sees the initial HTML: a shell with `<div id="root"></div>` and script tags. So `extractMainContent` gets almost no body text, we skip the page (content &lt; 50 chars), and no `WebsiteContent` is saved. The chatbot then has nothing to answer from and shows "website content is still being analyzed".

**Fix applied:** When body content is minimal (&lt; 50 chars), we now build a **fallback** from **title + meta description + og:description** (and use **og:title** when the document title is empty). If that gives at least 30 characters, we save one page so the chatbot can at least answer from meta/SEO content. This helps SPAs that have good meta tags in the initial HTML.

**Limitation:** If the SPA sends no meta tags in the initial HTML (everything injected by JS), we still get nothing. Full support would require a headless browser (e.g. Puppeteer/Playwright), which is heavier to run and maintain.

---

## 6. Optional follow-ups

- **Vector store cleanup on re-scan:** For a persistent vector store, consider deleting documents with `chatbotId == chatbot.getId()` before re-indexing (if the store supports filter-based delete or delete-by-id using stored vector IDs).
- **Indexing in batches:** `indexWebsiteContent` currently calls `vectorStore.add(List.of(document))` per page; batching (e.g. 10–20 documents per add) could reduce round-trips to the embedding API.
