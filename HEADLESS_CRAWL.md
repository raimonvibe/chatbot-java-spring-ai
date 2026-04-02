# Headless browser crawl for SPAs (safe use)

## What it does

When the crawler fetches a URL with **Jsoup** and gets **minimal content** (e.g. &lt; 200 chars, typical for React/Next/Vercel SPAs), it can **retry that URL with a headless Chrome** browser. The browser runs the page’s JavaScript and returns the rendered HTML; we then parse it with Jsoup and extract content as usual. That way sites like **Lagos Health Navigator** or **nigerian-tech-opportunities.vercel.app** can be crawled and the chatbot can answer from real content.

## Safety

- **URL validation**: Headless fetch is only used for URLs that have already passed `UrlValidationService` (SSRF checks). We validate again inside `HeadlessFetchService` before opening the URL.
- **No arbitrary code**: We only navigate to the URL and read `pageSource()`. We do not execute user-provided scripts.
- **Timeouts**: Page load timeout (default 25s) and script timeout (10s) limit how long the browser can run.
- **Headless only**: Chrome runs with `--headless=new`, no GPU, no sandbox escape (subject to your environment).
- **Cap per scan**: At most **5 pages per scan** are fetched with headless (configurable). The rest use Jsoup only.
- **Graceful fallback**: If Chrome is not installed or headless fails, we keep the Jsoup result (or nothing) and do not fail the whole scan.

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `app.website-analysis.headless-enabled` | `true` | Set to `false` to disable headless and use Jsoup only. |
| `app.website-analysis.max-headless-pages-per-scan` | `5` | Max number of pages to fetch with headless in a single scan. |
| `app.website-analysis.headless-timeout-seconds` | `25` | Page load timeout for each headless fetch. |

Environment variable: `HEADLESS_CRAWL_ENABLED=false` to disable without changing code.

## Requirements

- **Chrome or Chromium** must be installed on the server (e.g. `chromium` or `google-chrome`). The Selenium Manager (Selenium 4) will use the system Chrome/Chromium.
- **Tests**: In the `test` profile, `app.website-analysis.headless-enabled` is `false`, so tests do not require Chrome.

## Deployment (Docker / Render)

**The backend Dockerfile now includes Chromium** (`chromium-browser` on the runtime image) so headless crawl works in production. Vercel/SPA sites (e.g. lagos-health-navigator.vercel.app) are analyzed with full content when the service runs in this image.

- **Optional:** Set `CHROME_BIN` to the Chromium binary path if the driver does not find it (e.g. `CHROME_BIN=/usr/bin/chromium-browser`). The app uses this when set.
- To disable headless (e.g. in a minimal image): set `HEADLESS_CRAWL_ENABLED=false`. The service will still start; the crawler will use Jsoup only and title/meta fallback for SPAs.

## When headless is used

- We only call headless when **Jsoup has already returned minimal content** for that URL (e.g. body text &lt; 200 chars).
- We only use it for URLs that **passed SSRF validation**.
- We respect **max-headless-pages-per-scan** so the number of browser launches per scan is bounded.
