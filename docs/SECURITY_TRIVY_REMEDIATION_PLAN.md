# Plan: Resolving Trivy Security Alerts (Backend)

This document maps GitHub/Trivy code-scanning alerts to concrete remediation steps. Current backend: **Spring Boot 3.3.13**, with pinned overrides for several dependencies.

---

## Verification completed (2026-03-01)

**Dependency tree verified** with `mvn dependency:tree -DoutputFile=target/dep-tree.txt` from `backend/`. Resolved versions:

| Component | Resolved version | Required for fix | Status |
|-----------|------------------|------------------|--------|
| org.springframework (core, web, webmvc, beans, context, etc.) | **6.2.16** | ≥ 6.2.11 (#95,#97,#96) | OK |
| org.springframework.security (config, web, core, oauth2-*) | **6.3.10** | 6.4.x not used; CVE-2025-41232 affects 6.4.0–6.4.5 only | OK |
| org.apache.commons:commons-lang3 | **3.20.0** | ≥ 3.18.0 (#83) | OK |
| io.projectreactor.netty (reactor-netty-http, reactor-netty-core) | **1.3.3** | ≥ 1.2.8 (#82) | OK |
| com.nimbusds:nimbus-jose-jwt | **10.8** | ≥ 10.0.2 (#81) | OK |
| ch.qos.logback (logback-classic, logback-core) | **1.5.32** | ≥ 1.5.19 (#79) | OK |

**Suppressions:** `.trivyignore` already lists the CVEs above with expiry; Trivy will skip them. Re-run Trivy after changes: `trivy fs --severity CRITICAL,HIGH,MEDIUM .` (from repo root or backend).

---

## Alert summary

| #   | Severity | Component | CVE / Issue | Plan section |
|-----|----------|-----------|-------------|--------------|
| 92  | Critical | Spring Security | Method security bypass (private methods) | 1 |
| 95  | High    | spring-core | Annotation detection vulnerability | 2 |
| 93  | High    | spring-security-core | Authorization bypass | 3 |
| 97  | Medium  | spring-webmvc | Path traversal | 2 |
| 96  | Medium  | Spring Framework | Reflected download (non-ASCII headers) | 2 |
| 83  | Medium  | commons-lang3 | Uncontrolled recursion | 4 |
| 82  | Medium  | reactor-netty | Credential leak via redirects | 5 |
| 81  | Medium  | nimbus-jose-jwt | Uncontrolled recursion | 6 |
| 79  | Medium  | logback-core | Conditional arbitrary code execution | 7 |

---

## 1. #92 Critical – Spring Security method security bypass (private methods)

**CVE:** CVE-2025-41232 (GHSA-9pp5-9c7g-4r83)  
**Affected:** Spring Security **6.4.0–6.4.5** when using `@EnableMethodSecurity(mode=ASPECTJ)` and **spring-security-aspects** with method security annotations on **private** methods.

**Current state:**
- Spring Boot 3.3.13 pulls **Spring Security 6.3.x** (e.g. 6.3.10), **not** 6.4.x, so this CVE does not apply to the default Boot 3.3.13 stack.
- This project uses `@EnableMethodSecurity(prePostEnabled = true)` (default **proxy** mode, not ASPECTJ) and has **no** `@PreAuthorize` / `@PostAuthorize` in the codebase.

**Actions:**
1. **Confirm Trivy alert:** In the Security tab, open alert #92 and check the **exact** artifact and version Trivy reports. If it is `spring-security-aspects` or a 6.4.x artifact, note it.
2. **If version is 6.4.0–6.4.5:**  
   - **Option A:** Override to Spring Security **6.4.6+** in `dependencyManagement` (only if you move to 6.4.x for other reasons; Boot 3.3.x does not use 6.4 by default).  
   - **Option B:** Stay on Boot 3.3.13 (Security 6.3.x) and add a **Trivy/CodeQL suppression** with a short justification: “We use Spring Security 6.3.x (from Boot 3.3.13); CVE-2025-41232 affects 6.4.0–6.4.5 and requires ASPECTJ mode and private methods; we use proxy mode and have no method security on private methods.”
3. **Code hygiene:** Avoid putting `@PreAuthorize` / `@PostAuthorize` on **private** methods. Prefer public/protected beans and URL-based authorization (e.g. `SecurityFilterChain`) where possible.

---

## 2. #95 High, #97 Medium, #96 Medium – Spring Framework (core, webmvc, web)

**CVEs:**  
- **CVE-2025-41249** (annotation detection) – fixed in **6.2.11**  
- **CVE-2025-41242** (MVC path traversal) – fixed in **6.2.10**  
- **CVE-2025-41234** (reflected download, non-ASCII headers) – fixed in **6.2.8**

**Current state:**  
`pom.xml` pins **spring-framework.version=6.2.16** and forces `spring-core`, `spring-web`, `spring-webmvc`, and other Spring modules to that version. **6.2.16 > 6.2.11**, so all three CVEs are addressed by the pinned version.

**Actions:**
1. **Verify effective versions:** Run  
   `mvn dependency:tree -Dverbose -Dincludes=org.springframework:*`  
   and confirm all `org.springframework` artifacts resolve to **6.2.16** (or at least ≥ 6.2.11).
2. **If Trivy still reports old versions:**  
   - Ensure no other BOM or parent overrides Spring Framework to an older version.  
   - Add explicit `dependencyManagement` entries for the exact artifacts Trivy flags (e.g. `spring-core`, `spring-webmvc`), all with version `${spring-framework.version}`.
3. **Optional – document in repo:** In `SECURITY_REMEDIATION_PLAN.md` or this file, add a one-liner: “Spring Framework 6.2.16 addresses CVE-2025-41249, CVE-2025-41242, CVE-2025-41234.”

---

## 3. #93 High – Spring Security authorization bypass (spring-security-core)

**Context:** Often the same or related to #92 (method security). Spring Boot 3.3.13 uses Spring Security **6.3.x**.

**Actions:**
1. Open alert #93 and note the **reported CVE** and **artifact version**.
2. If it refers to **6.4.0–6.4.5**, same as section 1: we are on 6.3.x; document and optionally suppress with justification.
3. If it refers to a **6.3.x** CVE:  
   - Check [Spring Security Advisories](https://spring.io/security/cve) for the fix version.  
   - If a patch exists in a newer 6.3.x, override `spring-security-*` in `dependencyManagement` to that version.  
   - If the fix is only in 6.4.6+, evaluate upgrading to a Spring Boot version that uses a patched Security (e.g. Boot 3.4.x if it pulls 6.4.6+).

---

## 4. #83 Medium – commons-lang3 uncontrolled recursion

**CVE:** CVE-2025-48924  
**Fixed in:** **3.18.0+**

**Current state:**  
`commons-lang3.version=3.20.0` is set and the dependency is explicitly declared. **3.20.0 ≥ 3.18.0**, so the fix is in place.

**Actions:**
1. Run `mvn dependency:tree -Dincludes=org.apache.commons:commons-lang3` and confirm **3.20.0** (or higher) everywhere.
2. If Trivy still flags an older transitive: add an exclusion for `commons-lang3` from the pulling dependency and keep the explicit 3.20.0 dependency.

---

## 5. #82 Medium – reactor-netty credential leak via redirects

**CVE:** CVE-2025-22227  
**Fixed in:** **1.2.8+** (e.g. 1.3.x)

**Current state:**  
`reactor-netty.version=1.3.3` and `reactor-netty-http` / `reactor-netty-core` are in `dependencyManagement`. **1.3.3 > 1.2.8**, so the fix is in place.

**Actions:**
1. Run `mvn dependency:tree -Dincludes=io.projectreactor.netty:*` and confirm **1.3.3** (or intended patched version).
2. If Trivy reports an older reactor-netty: ensure no other BOM overrides it; keep the explicit `dependencyManagement` entries.

---

## 6. #81 Medium – nimbus-jose-jwt uncontrolled recursion

**CVE:** CVE-2025-53864  
**Fixed in:** **9.37.4** (9.x line) or **10.0.2+** (10.x line)

**Current state:**  
`dependencyManagement` has `nimbus-jose-jwt` **10.8**. **10.8 ≥ 10.0.2**, so the fix is in place.

**Actions:**
1. Run `mvn dependency:tree -Dincludes=com.nimbusds:nimbus-jose-jwt` and confirm **10.8** (or another version ≥ 10.0.2 or ≥ 9.37.4).
2. If another dependency pulls an older nimbus-jose-jwt, exclude it and rely on the managed 10.8.

---

## 7. #79 Medium – logback-core arbitrary code execution

**CVE:** CVE-2025-11226 (conditional config / Janino)  
**Fixed in:** **1.5.19+** (1.5.x line)

**Current state:**  
`logback.version=1.5.32` and `logback-core` / `logback-classic` are in `dependencyManagement`. **1.5.32 ≥ 1.5.19**, so the fix is in place.

**Actions:**
1. Run `mvn dependency:tree -Dincludes=ch.qos.logback:*` and confirm **1.5.32** (or intended patched version).
2. If Trivy still flags logback-core: ensure no transitive pulls an older logback; keep the explicit version in `dependencyManagement`.

---

## Execution order (recommended)

1. **Verify dependency tree** (once):  
   From `backend/`: `mvn dependency:tree -DoutputFile=target/dep-tree.txt` then inspect the file (or on Unix: `mvn dependency:tree -Dincludes=org.springframework:*,org.springframework.security:*,...`).  
   **Done:** Verified 2026-03-01; see table above.

2. **Address Critical/High first:**  
   - Sections 1 and 3 (Spring Security): confirm versions and add suppressions or overrides.  
   - Section 2 (Spring Framework): confirm 6.2.16 and fix any override gaps.

3. **Confirm Medium are fixed by current pins:**  
   - Sections 4–7: dependency tree + Trivy re-run. Add exclusions/overrides only if a transitive is still old.

4. **Re-run Trivy / code scanning** after changes and document any remaining suppressions (with CVE and “not applicable” or “fixed in our version” justification).

5. **Optional:** Add a CI step that fails on new Critical/High CVEs (e.g. `trivy fs --severity CRITICAL,HIGH --exit-code 1 .` or equivalent in your pipeline).

---

## References

- [Spring Security Advisories](https://spring.io/security/cve)  
- [Spring Framework 6.2.x release notes](https://github.com/spring-projects/spring-framework/releases)  
- [NVD CVE-2025-41232](https://nvd.nist.gov/vuln/detail/CVE-2025-41232) (method security bypass)  
- [NVD CVE-2025-41249](https://nvd.nist.gov/vuln/detail/CVE-2025-41249) (annotation detection)  
- [NVD CVE-2025-41242](https://nvd.nist.gov/vuln/detail/CVE-2025-41242) (path traversal)  
- [NVD CVE-2025-41234](https://nvd.nist.gov/vuln/detail/CVE-2025-41234) (reflected download)  
- Project: `backend/pom.xml` (current version pins and dependencyManagement)
