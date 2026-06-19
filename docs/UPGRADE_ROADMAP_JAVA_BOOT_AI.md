# Upgrade Roadmap (Java, Spring Boot, Spring AI)

This document is a practical, low-risk plan to keep the repo modern while avoiding production breakage.

---

## Current Baseline (this repo)

- Java: `21`
- Spring Boot parent: `4.1.0`
- Spring AI: `2.0.0`
- Frontend stack is already on modern majors (Next 16, React 19, TypeScript 6)

---

## What "latest" means right now

- Spring Boot latest stable: `4.1.x`
- Java latest LTS: `25` (Java 21 is also LTS and lower-risk as an intermediate step)
- Spring AI compatibility:
  - `1.1.x` aligns with Boot `3.5.x`
  - `2.x` is the line for Boot `4.x`

Reference links:
- Spring Boot 4.0.5 release: https://spring.io/blog/2026/03/26/spring-boot-4-0-5-available-now
- Spring Framework 7 release notes: https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes
- Spring AI getting started: https://docs.spring.io/spring-ai/reference/getting-started.html
- Spring AI Boot 4 compatibility tracking: https://github.com/spring-projects/spring-ai/issues/3379
- OpenJDK JDK 25 GA/LTS context: https://mail.openjdk.org/archives/list/announce@openjdk.org/message/CRU3PGGC4PULAOQCGASI7PFCFP2F6CWA/

---

## Other Dependencies Likely Affected

When upgrading Java + Spring Boot major versions, these dependencies in this repo are the most likely to need changes or revalidation:

### Backend (high impact)

- `spring-session-jdbc`
  - Verify session table/schema init and JDBC driver behavior after Boot upgrade.
- `spring-boot-starter-oauth2-client`
  - Re-test OAuth login and callback flow; client/http stack changes can break auth silently.
- `spring-boot-starter-webflux` + `reactor-netty`
  - Re-check HTTP client behavior and any explicit Netty version overrides.
- `spring-dotenv`
  - Confirm environment loading still behaves as expected in local/dev startup.
- `io.rest-assured:rest-assured` (tests)
  - Historically sensitive to Jackson/Boot major changes; regression risk is high.
- `com.fasterxml.jackson` vs `tools.jackson` ecosystem
  - Boot 4 introduces Jackson 3 world; mixed classpath assumptions can break compile/tests.

### Backend (medium impact)

- `org.seleniumhq.selenium:selenium-java` (tests/crawling tools)
  - Verify headless/browser test compatibility with updated Java runtime and CI images.
- `org.testcontainers:*`
  - Re-check containerized tests on new Java/Boot combinations.
- `wiremock-jre8-standalone`
  - Validate no runtime/test conflicts on newer JDK and dependency graph.
- `jjwt-*`
  - Re-run auth/JWT tests for parser/signing compatibility with upgraded stack.
- `stripe-java`
  - Re-run subscription and webhook integration tests after framework upgrade.

### Frontend (watch list)

- `next`, `react`, `react-dom`, `typescript`, `eslint-config-next`
  - Already modern, but keep versions aligned and ensure Vercel build remains green after backend API behavior changes.
- `@types/node`
  - Keep compatible with your chosen Node runtime in Vercel/CI.

### Build plugins/tools

- `maven-surefire-plugin` and `maven-failsafe-plugin`
  - Keep on stable latest and re-tune parallel/fork config only if flaky tests appear.
- `org.owasp:dependency-check-maven`
  - Keep updated, but verify CI runtime/performance impact.

---

## Recommended Strategy

Do this in phases, not one giant jump.

### Phase 1 (safe modernization now)

Goal: stay stable in production, still modernize.

1. Keep Spring Boot on `3.5.x` for now.
2. Upgrade runtime/build JDK from `17` to `21` first.
3. Keep Spring AI on `1.1.x`.
4. Keep merging patch/minor dependency updates that pass CI.

Why:
- lowest risk
- fast rollback if needed
- avoids Boot 4 + AI + tooling migration all at once

---

### Phase 2 (planned migration branch)

Create a dedicated branch: `boot4-migration`.

Target stack:
- Java: `25` (or `21` minimum)
- Spring Boot: `4.0.x`
- Spring AI: `2.x`

Do all migration work only on this branch until green.

---

## Boot 4 Migration Checklist

Use this as a gate checklist before merging.

### A) Build and dependency alignment

- [ ] `backend/pom.xml` parent -> Spring Boot `4.0.x`
- [x] `java.version` -> `21`
- [ ] Spring AI BOM/artifacts -> compatible `2.x`
- [ ] Remove temporary dependency overrides that were only for old conflicts
- [ ] `mvn -q -DskipTests compile` passes

### B) Backend tests

- [ ] `mvn test` passes
- [ ] Integration suite passes (`failsafe` profile/tests)
- [ ] Security-focused tests still pass (auth, SSRF validation, rate limit tests)

### C) Runtime smoke tests

- [ ] Local startup with production-like profile works
- [ ] Auth (email/google) flow works end-to-end
- [ ] Onboarding + analyze + chat response works
- [ ] Embed script generation and embed chat work
- [ ] Subscription endpoints and webhook path still function

### D) Deployment checks (Render/Vercel)

- [ ] Render build succeeds with new Java target
- [ ] Render boot logs show no dependency/classpath errors
- [ ] DB migrations run cleanly
- [ ] Frontend to backend API communication works
- [ ] No new 4xx/5xx spikes in first 24h

### E) Security and quality

- [ ] Snyk/Trivy/CodeQL checks are green
- [ ] No new secret scanning issues
- [ ] Regression test for onboarding scan-audit behavior still passes

---

## Known Risks for Boot 4 Jump

1. Jackson ecosystem differences and transitive changes can break tests/export paths.
2. Spring AI major-version jump may require API/config changes.
3. Test tooling (REST-assured/Testcontainers/wiremock interactions) may need updates.
4. CI can fail quickly even if app compiles.

---

## Decision Rule (simple)

- If only patch/minor deps change and CI is green -> merge.
- If Spring Boot major changes (`3.x` -> `4.x`) -> treat as migration project, not routine dependency update.

---

## Suggested Next Step

When ready, open `boot4-migration` and do:

1. Java 21/25 update
2. Spring Boot 4.0.x
3. Spring AI 2.x
4. Fix compile/tests
5. Deploy to preview/staging
6. Merge only after all gates above are green

