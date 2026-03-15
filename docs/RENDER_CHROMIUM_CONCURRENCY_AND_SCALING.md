# Chromium, Multiple Users, and Scaling on Render (2GB / 1 CPU + 256MB DB)

Research and recommendations for running the backend (with headless Chromium) and PostgreSQL on Render when serving multiple simultaneous users.

---

## Your current specs

| Component | Plan | RAM | CPU | Storage |
|-----------|------|-----|-----|---------|
| **Backend (Web Service)** | Standard | 2 GB | 1 | — |
| **PostgreSQL** | Basic | 256 MB | 0.1 | 15 GB |

---

## 1. Chromium memory and concurrency reality

### How much RAM does Chromium use?

- **Default / unoptimized:** A single headless Chrome/Chromium process can use **~1–2 GB** RAM (browser + renderer + GPU process, DOM, JS heap).
- **With optimizations (flags, no images, small window):** A single instance can be brought down to roughly **200–500 MB** in practice.
- **Your code already:** Uses a **semaphore with 1 permit** (`HeadlessFetchService`), so **only one headless browser runs at a time**. That’s the right choice for 2 GB RAM.

### What “multiple users at the same time” means

- **Chat and normal API:** Light on memory (JVM, DB, a bit of CPU). Many concurrent users can be served as long as you’re not doing many heavy operations at once.
- **Website analysis (crawl + Chromium):** Heavy. Each analysis uses:
  - The **crawl thread pool** (fixed size **3** in `WebsiteAnalysisService`),
  - **At most one Chromium** at a time (semaphore),
  - Then **indexing** (embeddings, DB writes).

So:

- **Simultaneous analyses:** They queue on the 3 crawl threads and on the single Chromium permit. The second user’s “Analyze website” may wait until the first finishes (or at least frees Chromium and crawl threads). That’s **by design** to avoid OOM on 2 GB.
- **Simultaneous chat/users (no analysis):** Limited mainly by CPU, DB connections, and JVM heap—typically **fine** for dozens of concurrent requests on 2 GB / 1 CPU if DB and pool are sized correctly.

### When you might run into issues

| Scenario | Risk | Symptom |
|----------|------|--------|
| **2+ users run “Analyze website” at once** | Chromium + crawl threads saturated | Second analysis waits; first may take 1–2 minutes. No crash if semaphore stays at 1. |
| **Many concurrent chat requests** | CPU + DB + JVM | Slower response times; under heavy load, timeouts or 503 if the app is overwhelmed. |
| **Very heavy page (SPA, many assets)** | Single Chromium spike | One analysis can spike RAM (e.g. 500–800 MB for that process). With JVM + OS + DB connections, 2 GB can get tight. |
| **DB connection exhaustion** | Too many connections from app(s) | “Too many connections” from PostgreSQL. |

---

## 2. Database (256 MB, 0.1 CPU)

- **Render PostgreSQL &lt; 8 GB RAM:** **97 concurrent connections** max (all clients combined).
- **Your app:** Hikari `maximum-pool-size: 10`, `minimum-idle: 5` per instance.
- **Implication:** One backend instance uses at most 10 connections. You’re well under 97. If you later run **multiple backend instances**, total connections = `instances × 10`; stay under 97 or use **PgBouncer** (Render supports it) to pool connections.

**Low-cost tip:** For a single instance, 10 is fine. If you scale to 2+ instances, consider lowering to **5** per instance or using Render’s **connection pooling** so you don’t exceed 97.

---

## 3. Low-cost options (stay on current plans)

### A. Keep current design (recommended baseline)

- **One Chromium at a time** (already in place).
- **Crawl pool size 3** (already in place).
- **Result:** Multiple users can use the app; analyses are serialized through Chromium. No extra cost.

### B. Tighten Chromium memory (optional)

Add flags to reduce RAM/CPU per run (in `HeadlessFetchService` or equivalent):

- `--disable-images` (if you don’t need images for content extraction).
- `--blink-settings=imagesEnabled=false`.
- `--window-size=800,600` (smaller than 1280x720).

Trade-off: Some sites may look or behave differently; test that your content extraction still works.

### C. DB pool for 256 MB DB

- Keep **maximum-pool-size: 10** (or reduce to **5** if you see connection warnings).
- **minimum-idle: 2** is enough for low traffic and saves a bit of DB memory.
- If Render offers **PgBouncer** in front of your Postgres, use it when you have more than one service (or many connections) talking to the same DB.

### D. Scan limits and quotas (already in place)

- **Daily / monthly scan limits** and **cost caps** (in your app) limit how often each user can run “Analyze website.” That naturally limits how many simultaneous analyses you get and protects cost.

---

## 4. When would you “need” Redis?

**Short answer:** You don’t need Redis to serve multiple users on a **single** 2 GB instance. Redis becomes useful when you **scale out** (multiple instances) or want **shared** state across instances.

### What Redis is good for

| Use case | Single instance (current) | Multiple instances |
|----------|---------------------------|---------------------|
| **Rate limiting** | In-memory (e.g. Bucket4j `ConcurrentHashMap`) is enough. | Redis (or similar) gives one shared rate limit across instances. |
| **Caching (e.g. API responses, robots.txt)** | In-memory cache is fine. | Redis (or Memcached) gives shared cache so all instances benefit. |
| **Sessions** | In-memory or DB is OK. | Redis (or DB) for sticky/shared sessions if you don’t use sticky routing. |
| **Job queue (e.g. “run analysis later”)** | DB or in-memory queue can work. | Redis (e.g. Bull/BullMQ) or a dedicated queue (Render background workers, etc.) for distributed workers. |

### If you’re new to Redis

- **You don’t need it yet** for “multiple users” on one instance. Your bottlenecks are:
  - **RAM/CPU** (Chromium + JVM),
  - **DB connections** (only if you add more instances).
- **Add Redis when:** You run 2+ backend instances and want shared rate limiting, cache, or queues. Render has a **Redis** add-on; you can start with a small plan.
- **Alternatives:** For pure caching, **Memcached** is simpler and often cheaper; for queues, **Render background workers** or a **DB-backed queue** can avoid introducing Redis until you need it.

---

## 5. Render options to serve more simultaneous users

### Option A: Vertical scaling (same single instance, more resources)

- **Upgrade the Web Service** to more RAM (e.g. 4 GB) and/or more CPU.
- **Effect:** Same one Chromium at a time, but more headroom for JVM + OS + concurrent chat and one heavy analysis. Fewer OOM risks.
- **Cost:** Higher monthly price for that service.

### Option B: Horizontal scaling (more instances)

- **Manual scaling:** Run **2 or more** instances of the same backend (e.g. 2 × 2 GB).
- **Load balancing:** Render distributes traffic across instances.
- **Effect:** More total capacity for **chat and API**. For **Chromium**, each instance still runs at most one browser; so you get **more concurrent analyses** (one per instance), but each instance still needs enough RAM for JVM + one Chromium.
- **Then consider:**  
  - **Redis** (or similar) for shared rate limiting and cache.  
  - **PgBouncer** (or Render pooling) so connection count stays under 97.  
  - Lower Hikari `maximum-pool-size` per instance (e.g. 5).

### Option C: Autoscaling (Render Pro)

- **Autoscaling** (Pro plan) scales instance count by CPU and/or memory.
- **Use case:** Traffic spikes; scale out when load is high, scale in when low.
- **Same as B:** Once you have multiple instances, shared state (rate limit, cache) and DB connection pooling matter.

### Option D: Offload heavy work (advanced, higher cost)

- **Background workers:** Put “Analyze website” in a **queue**; one or more **worker** services (e.g. Render background workers) run Chromium and crawl. Web instances only enqueue jobs and return status.
- **Effect:** Web instances stay light; Chromium runs only on worker(s). You can size workers for Chromium (e.g. 2 GB each) and scale them separately.
- **Cost:** More services and more complexity; Redis or a queue (e.g. Redis, or DB-backed) usually required.

---

## 6. “Prestige” vs “low-cost” summary

### Low-cost (current or small tweaks)

- Keep **1 Chromium at a time**, **crawl pool 3**.
- Optional: **More Chromium flags** to reduce RAM; **smaller Hikari pool** (e.g. 5) if you want to be gentle on 256 MB DB.
- **Result:** Multiple users OK; simultaneous analyses queue; no Redis needed. May see **slower or queued** analyses when several users hit “Analyze” at once.

### Optimal / “prestige” (if you want to invest)

- **Backend:** 4 GB RAM (or 2 × 2 GB instances) so Chromium + JVM have headroom.
- **DB:** Upgrade if you need more connections or performance (e.g. next tier up from 256 MB when you have 2+ instances or higher traffic).
- **Multi-instance:** Add **Redis** (or similar) for rate limit + cache; use **PgBouncer** (or Render pooling); consider **background workers** for analyses so web tier stays responsive.
- **Result:** More simultaneous users and analyses, fewer timeouts, more predictable behavior under load.

---

## 7. Practical recommendations

1. **Keep current architecture** (semaphore = 1, pool = 3). It’s correct for 2 GB and avoids OOM.
2. **Monitor:** On Render, watch **memory** and **CPU** during a few “Analyze website” runs and during peak chat. If you’re near 2 GB or high CPU, consider vertical or horizontal scaling.
3. **Add Redis only when** you run 2+ backend instances or need shared rate limit/cache/queue.
4. **Database:** Stay at 10 (or 5) connections per instance; use connection pooling (PgBouncer) when you have multiple instances.
5. **Set expectations:** In UI or docs, you can say that “Analyze website” may take 1–2 minutes and that only one analysis runs at a time (or one per instance) to keep the service stable.

---

## References (summary)

- Chromium: [Headless Chrome memory optimization](https://webscraping.ai/faq/headless-chromium/how-can-i-make-headless-chromium-use-less-cpu-and-memory), [Stack Overflow – Selenium Chrome memory](https://stackoverflow.com/questions/59268492/decreasing-selenium-standalone-chrome-memory-usage).
- Render: [Scaling](https://docs.render.com/scaling), [PostgreSQL connection pooling](https://render.com/docs/postgresql-connection-pooling), [Postgres limits](https://feedback.render.com/features/p/increase-postgresql-concurrent-connection-limit-for-larger-plans).
- Hikari: Keep pool small (e.g. 5–10) for small DB; avoid oversizing.
- Redis: Use when scaling to multiple instances for shared state; not required for a single 2 GB instance.
