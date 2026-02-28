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

**Note:** The default backend Dockerfile uses a minimal JRE image and does **not** install Chrome/Chromium. In that setup, headless fetch will no-op (returns empty) and the crawler uses Jsoup only. To enable headless in Docker you must use an image that includes the browser (see below).

If you run the backend in Docker or on Render and want headless crawling:

1. **Install Chrome/Chromium** in the image, e.g.:
   - Debian/Ubuntu: `apt-get install -y chromium` or the official Chrome package.
   - Alpine: `apk add chromium`.
2. Set the environment so the driver finds the browser (e.g. `CHROME_BIN` or `CHROMIUM_FLAGS` if needed by your setup).
3. Keep `HEADLESS_CRAWL_ENABLED=true` (default) or set it explicitly.

If you **do not** install Chrome, the service will still start; headless fetch will simply return empty and the crawler will behave as before (Jsoup-only, with fallback to title/meta when possible).

## When headless is used

- We only call headless when **Jsoup has already returned minimal content** for that URL (e.g. body text &lt; 200 chars).
- We only use it for URLs that **passed SSRF validation**.
- We respect **max-headless-pages-per-scan** so the number of browser launches per scan is bounded.
