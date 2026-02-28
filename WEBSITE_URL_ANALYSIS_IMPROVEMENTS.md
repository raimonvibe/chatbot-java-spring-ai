# Website URL Analysis Improvements

## Chatbot creation flow (safe URLs, all sites, large → paid)

- **All safe URLs** (validated for SSRF, public only) can be used to create a chatbot: Vercel sites, normal sites, small or large.
- **Small sites** (within plan page limit): chatbot is created and website analysis runs (Jsoup + headless when needed).
- **Large sites**: Before creating the chatbot, the backend estimates site size. If **estimated pages > plan limit** (FREE 50, BASIC 500, PRO 2000, ENTERPRISE 10000), the API returns **402 Payment Required** with `upgradeRequired: true`, `suggestedPlan`, and a message to upgrade. The frontend can redirect the user to the pricing/paid plan page. This is already implemented in the onboarding and create-chatbot flows.
- **Live/Docker**: The backend Dockerfile includes **Chromium** so headless crawl runs in production. Vercel/SPA sites are analyzed with full content. The service runs correctly live when built with this image.

## Why some URLs (e.g. lagos-health-navigator.vercel.app) weren’t analyzed well

1. **URL not completed**  
   Users could paste `lagos-health-navigator.vercel.app` without `https://`. The crawler needs a full, valid URL. If the stored or passed URL was incomplete or had a fragment, analysis could fail or behave inconsistently.

2. **SPA / client-rendered sites**  
   Sites like Vercel/Next.js apps often serve minimal HTML and render content with JavaScript. Jsoup only sees the initial HTML, so it gets little text. Without a headless browser, the chatbot had almost no content to answer “what is this site about?”.

3. **No canonical URL stored**  
   The URL used for analysis wasn’t always normalized (scheme, no fragment). That could lead to duplicate or inconsistent crawls and a worse experience when asking questions.

## What was implemented

### 1. URL completion and validation (`UrlValidationService`)

- **`completeAndValidate(String url)`**
  - Trims input.
  - Adds `https://` if the URL has no scheme.
  - Strips the fragment (`#...`).
  - Builds a canonical form (e.g. `https://host/path`).
  - Runs full SSRF validation (`isValidAndSafe`) and returns `Optional.of(completedUrl)` only if the URL is safe.

All analysis now starts from this completed URL, so any valid URL (e.g. `lagos-health-navigator.vercel.app` or `https://example.com/page#section`) is turned into a single, safe form before crawling.

### 2. Use completed URL in analysis (`WebsiteAnalysisService`)

- At the start of `analyzeWebsite`, the chatbot’s `websiteUrl` is passed through `completeAndValidate`.
- If the result is empty, analysis is skipped (no crawl, no cost).
- Seeds and crawling use the completed URL.
- After a successful run, if the completed URL differs from the stored one, the chatbot’s `websiteUrl` is updated so the canonical form is persisted.

### 3. Headless-first for known SPA hosts

- For the **first page only** (depth 0), if the host is a known SPA host (e.g. `*.vercel.app`, `*.netlify.app`, `*.web.app`, `*.firebaseapp.com`), the crawler tries **headless Chrome first** instead of Jsoup.
- If headless returns enough content (≥ 50 chars), that is used and one headless budget is consumed.
- If headless isn’t used or doesn’t return enough, the existing logic runs: Jsoup first, then headless when Jsoup content is minimal (< 200 chars).

So for URLs like `https://lagos-health-navigator.vercel.app/`, the homepage is fetched with a real browser when possible, and the chatbot gets enough content to answer questions about the site.

### 4. Security

- **SSRF**: All URLs (including completed and post-redirect) are validated with `UrlValidationService`; no change to existing security guarantees.
- **Persistence**: Only the completed, validated URL is stored on the chatbot.
- **Headless**: Headless is only used for URLs that have already passed validation; no new network surface.

## Configuration

- **Headless**: `app.website-analysis.headless-enabled` (default `true`), `max-headless-pages-per-scan`, `headless-timeout-seconds`. If headless is disabled or Chrome isn’t available (e.g. minimal Docker image), the crawler falls back to Jsoup only (and title/meta fallback for SPAs).
- **Crawl**: Existing `max-pages`, `max-depth`, `timeout-seconds` apply unchanged.

## Deployment note

For SPAs (e.g. Vercel apps), headless must be enabled and Chrome/Chromium installed in the runtime image. If not, the app still works but SPA homepages may yield only title/meta, so answers like “what is this site about?” may be limited. When the crawler only gets minimal content (e.g. title "frontend"), the chatbot is now instructed to give a friendly reply: suggest visiting the site directly and offer to help with other questions. For full SPA content, install Chrome/Chromium in your image—see `HEADLESS_CRAWL.md` for setup.
