# Chatbot website URL for Vercel sites

If you create a chatbot for a site hosted on **Vercel** and the bot says it has no information about the website (“website data has not been fully analyzed”), common causes are: (1) the **website URL** is a **preview deployment** URL, or (2) the site is **client-rendered** (React/SPA) and the backend is running **without Chromium**.

## Why Vercel sites sometimes need Chromium

The crawler can run in two ways:

- **Jsoup only** (no browser): fetches the raw HTML. For **client-rendered** Vercel/React apps, the server often sends a minimal shell (e.g. a `<div id="root">` and a title); the real content is added by JavaScript in the browser. So without running a browser we only get that shell.
- **Headless Chromium**: runs a real browser, executes JavaScript, and then we read the full page. That gives full content for SPAs.

**Securing the project did not add a Chromium requirement.** The logic has always been: for known SPA hosts (including `.vercel.app`) we *try* headless first when it’s available. If headless is disabled or unavailable (e.g. on Render without Chrome installed), we fall back to Jsoup. So “Vercel used to work” usually means headless was available before (e.g. Chromium used to be in the Render Docker image or stack) and was later removed or the image was slimmed down. So even when the backend was always on Render, the change is the **environment** (Chromium removed), not the security code. To get full content for client-rendered Vercel sites you either need headless enabled where the backend runs, or the Vercel app must send crawlable HTML (SSR/pre-render).

## What goes wrong

- **Preview URLs** (e.g. `https://my-app-abc123-username.vercel.app` or `https://my-app-git-branch-username.vercel.app`) are often protected and return **401 Unauthorized** to our crawler.
- **Production URLs** for client-rendered apps: we only get full content if the backend uses headless; otherwise we get minimal HTML and the bot has little to work with.

## What to use instead

Use one of these for the chatbot’s **website URL**:

1. **Production Vercel URL**  
   `https://your-project.vercel.app`  
   (Replace `your-project` with your Vercel project name.)

2. **Custom domain**  
   e.g. `https://www.yourdomain.com` if you’ve set it in Vercel.

Do **not** use:

- `https://something-xxx-username.vercel.app` (preview deployment)
- `https://project-git-branch-username.vercel.app` (branch preview)

## After changing the URL

1. Edit the chatbot and set **Website URL** to the production URL or custom domain.
2. Trigger a **re-analyze** (or create a new chatbot with that URL).
3. When the analysis finishes with at least one page extracted, the bot will be able to answer from the site content.

## Environment variables (Render backend)

Set these in your Render **Web Service** → **Environment** so the crawler can use Chromium for Vercel/SPA sites:

| Variable | Example | Purpose |
|----------|---------|--------|
| **HEADLESS_CRAWL_ENABLED** | `true` | Enable headless Chromium for website crawl (default: `true`). Set to `false` to disable (Jsoup-only; SPAs will get minimal content). |
| **CHROME_BIN** | `/usr/bin/chromium-browser` | Path to Chromium. Only set if your image uses a different path; the Dockerfile installs `chromium-browser` so the default is usually correct. Must be an absolute path under `/usr` (e.g. `/usr/bin/chromium` or `/usr/bin/chromium-browser`). |

Optional (Spring Boot will use defaults if unset):

| Variable | Default | Purpose |
|----------|---------|---------|
| **APP_WEBSITE_ANALYSIS_HEADLESS_TIMEOUT_SECONDS** | `25` | Page load timeout for headless (seconds). |
| **APP_WEBSITE_ANALYSIS_MAX_HEADLESS_PAGES_PER_SCAN** | `5` | Max pages per scan that use headless (limits memory). |
| **APP_WEBSITE_ANALYSIS_USER_AGENT** | `AI-Chatbot-Crawler/1.0` | User-Agent for Jsoup and robots.txt; headless uses a fixed browser UA. |

To **enable** headless on Render (Docker build): leave `HEADLESS_CRAWL_ENABLED` unset or set to `true`. The backend Dockerfile already includes Chromium. If the instance has too little memory and Chromium is killed, set `HEADLESS_CRAWL_ENABLED=false` to fall back to Jsoup-only (and the browser User-Agent retry for SPA hosts).

## Chatbot only has minimal content (e.g. “frontend” or “frontend. frontend”)

If the chatbot is created but only knows the site title (e.g. “frontend”) and says it has “limited information”, the crawler did **not** get full content. For Vercel/React SPAs that usually means **headless Chromium did not run or failed** on the backend.

**Checklist so Vercel sites get full content:**

1. **Backend is built with the Dockerfile**  
   On Render, the backend must use **Docker** (not “Native” Maven). The repo Dockerfile installs Chromium. In `render.yaml`, the backend service should have `dockerfilePath: backend/Dockerfile`.

2. **Headless is enabled**  
   In Render → backend service → Environment, do **not** set `HEADLESS_CRAWL_ENABLED=false`. Leave it unset (default `true`) or set to `true`.

3. **Chromium path**  
   The Dockerfile sets `CHROME_BIN=/usr/bin/chromium-browser`. The app also tries `/usr/bin/chromium` if the first is missing. If your image uses another path (e.g. some Ubuntu/Jammy setups use snap), set `CHROME_BIN` to the real binary path (must be under `/usr`).

4. **Memory**  
   Chromium needs roughly 200–400 MB. On very small instances it may be killed; then the crawler falls back to Jsoup and you get minimal content. If you see headless errors or OOM in logs, try a larger instance or set `HEADLESS_CRAWL_ENABLED=false` and accept minimal content for SPAs.

5. **Logs**  
   When headless fails for a Vercel URL, the backend logs:  
   `Headless fetch failed for Vercel URL ...` or  
   `Headless returned no content for Vercel URL ...`  
   Use that to confirm Chromium isn’t running or isn’t returning content.

After fixing the backend (Docker + headless enabled + Chromium available), **re-run “Analyze website”** for the chatbot so the site is crawled again with headless.

## Backend log hint

When no content is extracted for a URL containing `.vercel.app`, the backend logs a warning suggesting you use the production URL. Check Render logs for that message if the chatbot still has no website context.
