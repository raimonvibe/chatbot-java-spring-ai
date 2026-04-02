# Next.js security (Trivy alerts #114–#117)

The frontend uses **Next.js 16.1.7** so the following issues are addressed:

| Alert | Issue | Fix in 16.1.7 |
|-------|--------|----------------|
| #117 | HTTP request smuggling in rewrites | CVE-2026-29057 – patch to http-proxy in rewrites |
| #116 | Unbounded next/image disk cache growth | CVE-2026-27980 – LRU disk cache + `images.maximumDiskCacheSize` |
| #115 | Unbounded postponed resume buffering (DoS) | CVE-2026-27979 – `maxPostponedStateSize` always respected |
| #114 | null origin can bypass Server Actions CSRF | CVE-2026-27978 – Server Action submissions from privacy-sensitive contexts disallowed by default |

See [Next.js v16.1.7 release notes](https://github.com/vercel/next.js/releases/tag/v16.1.7). Keep `next` and `eslint-config-next` on `^16.1.7` (or a later patched 16.x).
