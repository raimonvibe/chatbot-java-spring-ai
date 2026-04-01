# Cost and risk notes when offering Prayer-Chat for free

Publishing the app **without subscription revenue** shifts spend and risk to **you** (infrastructure, APIs, and abuse). Below is a concise checklist of where costs and exposure typically grow.

## Variable costs (scale with usage)

| Area | What drives cost | Mitigation ideas |
|------|------------------|------------------|
| **LLM inference** | Every chat message (tokens in + out). | Rate limits per user/IP; cap message length; cache common answers where safe; choose smaller/faster models for free tier. |
| **Embeddings** | Indexing website pages and re-scans. | Hard cap pages per scan (e.g. 500); limit rescans per month; debounce “analyze again”. |
| **Crawling / headless** | CPU, memory, bandwidth, time per crawl. | Timeouts, max depth, max pages; block or throttle heavy domains; queue crawls. |
| **Vector store** | Stored vectors and query load (Pinecone or self-hosted). | Retention policy; per-chatbot caps; prune old versions on rescrape. |
| **Email / notifications** | If you add transactional email. | Use only where necessary; rate-limit triggers. |

## Fixed and semi-fixed costs

- **Hosting:** App server (e.g. Render/Railway/Fly), database (Postgres), optional Redis, object storage for assets.
- **Domains and TLS:** Usually small but recurring.
- **Observability:** Log retention, APM, error tracking (can grow with traffic).
- **Support:** Time cost for user questions and incident response.

## Abuse and security (can become a cost)

- **Credential stuffing / bots** on login or chat endpoints → compute and provider bills.
- **Scraping arbitrary URLs** → SSRF risk, legal/reputation risk, and crawler load. Keep URL validation, allowlists where possible, and strict timeouts.
- **Embed script misuse** → monitor origins and chatbot IDs; rate limit public embed traffic.
- **Storage or quota exhaustion** → per-user and global caps on chatbots, scans, and stored content.

## Compliance and legal (non-dollar costs)

- Privacy policy and data retention for chat logs and indexed page content.
- Terms that clarify crawling only sites the user is allowed to index.
- If you serve minors or religious audiences, consider content and safety policies appropriate to your jurisdiction.

## Practical posture for a free launch

1. Keep **server-enforced** limits (the UI only explains them; it does not secure the system).
2. Monitor **LLM and embedding** usage dashboards weekly at first.
3. Set **billing alerts** on cloud and API provider accounts.
4. Document your **max pages per scan** and **message quotas** publicly to set expectations (already surfaced via `/api/plans/limits` and the create-chatbot form).

When you introduce paid plans, map each cost driver to a plan limit so margin stays predictable.
