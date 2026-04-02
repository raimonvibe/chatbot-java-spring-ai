# Chatbot creation – content not loading (crawl debug)

## Safety and token cost (quick answers)

- **Is the relaxed validation safe?** Yes. We only accept **plain text** already extracted by Jsoup (title + meta or body text). No raw HTML is stored. URL/SSRF checks are unchanged. The relaxed rule applies only to the **first page** (depth 0); all other pages still require > 100 chars and > 20 words.
- **Does the debug test charge tokens?** **No.** The test only runs the **crawl** (HTTP fetch + text extraction + DB save). It does **not** call `indexWebsiteContent()` or the embedding API, so **no Cohere or other API tokens are used** when you run the test. Normal `mvn test` does not run this test (it’s `@Disabled`); you only run it when you explicitly enable it.

---

## Problem

When creating a chatbot from a URL like **https://nigerian-tech-opportunities.vercel.app/**, the chat can show “content isn’t loaded” or “website content is still being analyzed” even after 2+ minutes. The chatbot then can’t answer properly about the site.

## Root cause

1. **No JavaScript execution**  
   The crawler uses **Jsoup**, which only fetches the initial HTML. It does **not** run JavaScript. Many Vercel/Next/React apps are client-rendered, so the main text is often injected by JS. Jsoup only sees a minimal shell (e.g. `<div id="root"></div>` and script tags).

2. **Strict content validation**  
   Previously we only saved a page when:
   - content length **> 100** characters, and  
   - word count **> 20**.  
   For SPAs, we build a **fallback** from `<title>` + meta description + `og:description`. That fallback is often short (e.g. 30–80 chars). So we never saved anything → 0 pages → indexing never had content → “content not loaded” forever.

3. **Optional: sitemap/homepage structure**  
   If the site is SSR or has good meta tags in the initial HTML, we might still get content. If the main content lives in a container we don’t target (e.g. a specific `#__next` or `[data-react-root]`), we added those selectors so we pick up more content when it’s present in the first response.

## Fixes applied

1. **Relaxed validation for the first page (homepage)**  
   In `WebsiteAnalysisService` we now accept the **first page** (depth == 0) if it has “minimal but usable” content:
   - content length **≥ 40** and  
   - word count **≥ 3**.  
   So a short title + description from meta is enough to save at least one page. The chatbot can then answer “about this site” from that meta/SEO content instead of saying content isn’t loaded.

2. **More main-content selectors**  
   We added `#__next` (Next.js) and `[data-react-root]` (React) to `MAIN_CONTENT_SELECTORS` so we don’t miss main content when it’s already in the initial HTML.

3. **Local debug test**  
   `WebsiteAnalysisCrawlDebugIT` runs a real crawl against `https://nigerian-tech-opportunities.vercel.app/` and asserts that at least one page is extracted. It is **disabled by default** so CI doesn’t hit the network.

## How to run the debug test locally

1. Open:
   - `backend/src/test/java/com/prayer_chat/chatbot/service/WebsiteAnalysisCrawlDebugIT.java`
2. Remove or comment out the `@Disabled` annotation on the class.
3. Run:
   ```bash
   cd backend
   mvn test -Dtest=WebsiteAnalysisCrawlDebugIT
   ```
4. Check the console: it logs how many pages were extracted and, for each page, title, content length, and word count. Use this to see why a given URL might still yield 0 pages (e.g. blocking, timeout, or no meta in initial HTML).

## Limitations

- **No headless browser**  
  Full support for heavy client-rendered SPAs would require a headless browser (e.g. Puppeteer/Playwright) to execute JS. That would add weight and operational cost; we didn’t add it.

- **Meta-only content**  
  For SPAs with no or minimal meta in the initial HTML, we still get nothing. The only robust fix would be either a headless browser or the site exposing meta/SSR.

## Summary

- **Why it happened:** Jsoup doesn’t run JS; strict “> 100 chars and > 20 words” rejected SPA fallbacks.  
- **What we did:** Accept the first page with ≥ 40 chars and ≥ 3 words, and add Next/React content selectors.  
- **How to debug:** Enable and run `WebsiteAnalysisCrawlDebugIT` locally and inspect the logged crawl result.
