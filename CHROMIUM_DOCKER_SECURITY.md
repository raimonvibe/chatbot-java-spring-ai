# Chromium in Docker – Security Review

This document describes how Chromium is used in the backend Docker image and the security measures in place.

## What Chromium is used for

- **Headless website crawl** for single-page apps (e.g. Vercel, React, Next.js). When Jsoup gets minimal HTML, the app can retry with a headless Chromium instance to get rendered content.
- Only **validated URLs** are ever opened (see below). No user-supplied script or arbitrary URL is passed to the browser without validation.

## Security measures

### 1. URL validation (SSRF and abuse)

- **Before any fetch**, every URL is validated by `UrlValidationService.isValidAndSafe()`:
  - Blocked: localhost, 127.0.0.1, private IPs (10.x, 192.168.x, 172.16–31.x), link-local, cloud metadata endpoints (e.g. 169.254.169.254), file://, non-HTTP(S) schemes.
  - Allowed: public HTTP/HTTPS URLs only after DNS resolution and IP checks.
- `HeadlessFetchService.fetchRenderedHtml()` **re-checks** the URL with the same validation before opening it (defense in depth).
- Only **already-validated** URLs from the crawler are passed to headless; the crawler never passes raw user input to Chromium.

### 2. Docker image

- **Non-root user**: The container runs as `spring` (non-root). Chromium is started by the JVM and therefore also runs as non-root.
- **Minimal install**: Only `chromium-browser` and `wget` are installed; `--no-install-recommends` and `rm -rf /var/lib/apt/lists/*` keep the image and attack surface smaller.
- **No extra capabilities**: The Dockerfile does not add `--cap-add` or similar. Default seccomp remains in place at runtime (unless overridden by the orchestrator).

### 3. CHROME_BIN environment variable

- If set, `CHROME_BIN` tells the app where the Chromium binary is. To avoid path injection or running an arbitrary binary:
  - The app only uses `CHROME_BIN` when `isSafeChromeBinPath()` returns true.
  - Allowed: absolute paths under `/usr/` (e.g. `/usr/bin/chromium-browser`), no `..`, no leading `-`, length ≤ 256.
  - Rejected: anything else (e.g. `/tmp/evil`, `..`, or paths outside `/usr/`).

### 4. Chromium flags (hardening)

- **--headless=new**: No display; no GUI.
- **--no-sandbox**: Required for Chromium to run in many Docker environments. Trade-off: the browser process is not sandboxed. Mitigations: we only open SSRF-validated public URLs, we use timeouts and no persistent profile, and the container runs as non-root.
- **--disable-setuid-sandbox**: Avoids setuid helper in container.
- **--disable-dev-shm-usage**: Reduces reliance on /dev/shm (avoids some crashes in containers).
- **--disable-gpu**, **--disable-software-rasterizer**: No GPU/rendering pipeline.
- **--disable-extensions**: No extensions.
- **--no-first-run**, **--disable-background-networking**, **--disable-default-apps**, **--disable-sync**, **--disable-translate**, **--metrics-recording-only**: Fewer background services and features.
- **Timeouts**: Page load and script timeouts (configurable, default 25s / 10s) limit how long the browser can run per request.

### 5. No persistent profile or user data

- No `--user-data-dir` is set. Each run uses a temporary profile that is discarded when the driver quits. No cookies or persistent state are kept between runs.

### 6. Cap on headless usage

- `max-headless-pages-per-scan` (default 5) limits how many pages per scan are fetched with Chromium. The rest use Jsoup only. This bounds resource use and exposure.

### 7. Graceful degradation

- If Chromium is missing or fails (e.g. binary not found, crash), the service returns empty and the crawler falls back to Jsoup-only content. No unhandled exception or crash of the app.

## Summary

| Risk | Mitigation |
|------|------------|
| SSRF / internal network access | URL validated (public only) before any fetch; re-validated in HeadlessFetchService. |
| Path injection via CHROME_BIN | Only paths under `/usr/`, no `..`, length limit. |
| Running as root | Container runs as non-root user `spring`. |
| Large attack surface in image | Minimal install (chromium-browser + wget); apt cache removed. |
| Malicious site exploits browser | Only public URLs; timeouts; no persistent profile; no user scripts executed. |
| --no-sandbox | Accepted for Docker; isolation relies on container and URL allowlist. |

For further hardening, you could use a custom seccomp profile for Chrome (e.g. `chrome.json`) in your orchestration (e.g. `docker run --security-opt seccomp=chrome.json`) if your deployment supports it.
