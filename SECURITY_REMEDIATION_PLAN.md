# Security remediation plan (Trivy / Code scanning)

Plan to resolve the security issues reported by Trivy in [GitHub Code scanning](https://github.com/raimonvibe/chatbot-java-spring-ai/security/code-scanning). Each section lists the alert, fix version, and concrete steps.

---

## Summary

| Category | Count | Approach |
|----------|--------|----------|
| **Backend – dependency upgrades** | 12+ alerts | Override versions in `backend/pom.xml` |
| **Frontend – JWT “secrets”** | 7 alerts | Use placeholders + Trivy ignore or env-based test tokens |
| **N/A / false positive** | 1 | Document (Tomcat Windows installer) |

---

## 1. Backend: dependency version overrides (`backend/pom.xml`)

### 1.1 Spring Framework (spring-core, spring-webmvc) – High / Medium

| Alert | CVE | Fix version | Notes |
|-------|-----|-------------|--------|
| Spring Framework Annotation Detection | CVE-2025-41249 | **6.2.11** | Already overridden in `dependencyManagement` for `spring-core`. Ensure all `spring-*` modules resolve to 6.2.x line. |
| Spring MVC path traversal | CVE-2025-41242 | **6.2.10** | Covered by 6.2.11. |
| Reflected download (non-ASCII headers) | CVE-2025-41234 | **6.2.8** | Covered by 6.2.11. |

**Action:** Keep `spring-core` at **6.2.11** in `dependencyManagement`. If Trivy still flags `spring-webmvc` or other `spring-*` artifacts, add explicit overrides for them at 6.2.11 in `dependencyManagement`.

**References:** [CVE-2025-41249](https://spring.io/security/cve-2025-41249), [CVE-2025-41242](https://spring.io/security/cve-2025-41242), [CVE-2025-41234](https://spring.io/security/cve-2025-41234).

---

### 1.2 Spring Security (spring-security-core) – High

| Alert | CVE | Fix version | Notes |
|-------|-----|-------------|--------|
| Spring Security authorization bypass | CVE-2025-41248 / CVE-2025-22223 | **6.4.11** | Annotation detection on parameterized types; affects `@EnableMethodSecurity`. |

**Action:** In `pom.xml` `<properties>`, set:

```xml
<spring-security.version>6.4.11</spring-security.version>
```

Current value is 6.4.6; 6.4.11 includes the authorization-bypass fix.

**References:** [CVE-2025-41248](https://spring.io/security/cve-2025-41248), [CVE-2025-22223](https://spring.io/security/cve-2025-22223).

---

### 1.3 Apache Tomcat (tomcat-catalina, embedded) – High / Medium

| Alert | CVE | Fix version | Notes |
|-------|-----|-------------|--------|
| Directory traversal via rewrite / RCE | CVE-2025-55752 | **10.1.45** | Already set. |
| HTTP/2 “MadeYouReset” DoS | CVE-2025-53506 | **10.1.43** | Covered by 10.1.45. |
| DoS in multipart upload | CVE-2025-52520 / CVE-2025-48988 | **10.1.43** | Covered by 10.1.45. |
| Security constraint bypass (pre/post-resources) | CVE-2025-49125 | **10.1.42** | Covered by 10.1.45. |
| Console manipulation (tomcat-juli) | CVE-2025-55754 | **10.1.45** | Already set. |
| Tomcat Windows installer untrusted search path | (installer CVE) | N/A | **False positive**: we use **embedded** Tomcat, not the Windows installer. No action; can be documented as N/A in Code scanning. |

**Action:** Keep `tomcat.version` at **10.1.45** (or upgrade to latest 10.1.x if newer). Confirm with `mvn dependency:tree | findstr tomcat` that all Tomcat modules use 10.1.45. If any Trivy alert still points to an older Tomcat, ensure no other BOM/dependency forces an older version.

**References:** [Apache Tomcat 10.1 security](https://tomcat.apache.org/security-10.html), CVE-2025-55752, CVE-2025-53506, CVE-2025-52520, CVE-2025-49125, CVE-2025-55754.

---

### 1.4 Apache Commons Lang3 – Medium

| Alert | CVE | Fix version | Notes |
|-------|-----|-------------|--------|
| Uncontrolled recursion (ClassUtils) | CVE-2025-48924 | **3.18.0** | Affects commons-lang3 &lt; 3.18.0. |

**Action:** In `pom.xml` `<properties>`, add (or update):

```xml
<commons-lang3.version>3.18.0</commons-lang3.version>
```

If the parent does not use this property, add an explicit dependency or `dependencyManagement` entry for `org.apache.commons:commons-lang3` at **3.18.0**.

**Reference:** [CVE-2025-48924](https://nvd.nist.gov/vuln/detail/CVE-2025-48924).

---

### 1.5 Reactor Netty – Medium

| Alert | CVE | Fix version | Notes |
|-------|-----|-------------|--------|
| Credential leak via redirects | CVE-2025-22227 | **1.2.8** (or 1.1.32 / 1.0.49) | Depends on Reactor BOM used by Spring Boot. |

**Action:** Check which `reactor-netty-http` version is pulled in:

```bash
mvn dependency:tree -DincludeArtifactIds=reactor-netty-http
```

Then in `pom.xml` add a property override if the parent defines it (e.g. `reactor-netty.version`), or add a `dependencyManagement` override for `io.projectreactor.netty:reactor-netty-http` at a fixed version (e.g. **1.2.8**). Spring Boot 3.3.x may use a Reactor BOM that already includes a fix; if not, override explicitly.

**Reference:** [CVE-2025-22227](https://spring.io/security/cve-2025-22227).

---

### 1.6 Nimbus JOSE + JWT – Medium

| Alert | CVE | Fix version | Notes |
|-------|-----|-------------|--------|
| Uncontrolled recursion (deeply nested JSON) | CVE-2025-53864 | **9.37.4** or **10.0.2** | Transitive (e.g. via jjwt or Spring Security). |

**Action:** Add in `dependencyManagement` (or an explicit dependency if direct):

```xml
<dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>nimbus-jose-jwt</artifactId>
    <version>9.37.4</version>
</dependency>
```

Prefer 9.37.4 if the rest of the stack expects 9.x; otherwise 10.0.2. Run `mvn dependency:tree` to see which version is currently resolved.

**Reference:** [CVE-2025-53864](https://nvd.nist.gov/vuln/detail/CVE-2025-53864).

---

## 2. Frontend: JWT token alerts (Trivy “secret” detection) – Medium

Alerts #99–105: Trivy flags JWT-looking strings in E2E test and mock files as “secrets”.

**Affected files (from alerts):**

- `frontend/e2e/pages/login.spec.ts` (e.g. line 77)
- `frontend/e2e/helpers/auth.ts` (e.g. lines 36, 121, 182)
- `frontend/e2e/helpers/api-mock.ts` (e.g. lines 57, 79, 101)

**Options (choose one or combine):**

1. **Use a clear placeholder**  
   Replace the current mock JWT with a single constant, e.g. `MOCK_JWT_E2E_TEST` or `process.env.E2E_MOCK_JWT || 'test-token-not-a-real-jwt'`, and use that in all E2E/mock code. Trivy may still flag it; then use option 2 or 3.

2. **Exclude from Trivy**  
   Add a [Trivy config](https://docs.trivy.io/latest/docs/configuration/secret/) (e.g. `.trivy.yaml` or in CI) to exclude these paths for secret scanning:
   - `frontend/e2e/**`
   - `frontend/**/api-mock.ts`  
   Optionally restrict to `secret` scan type only for these paths.

3. **Generate a minimal “test” JWT at runtime**  
   In E2E/mock helpers, build a short-lived token in code (e.g. with a test-only secret and library) instead of storing a string. Reduces chance of false positives; slightly more setup.

**Recommended:** Use a constant like `E2E_MOCK_AUTH_TOKEN` with a short comment that it is not a real secret, and add Trivy exclusions for `frontend/e2e` and test/mock helpers so production secrets are still scanned.

---

## 3. Implementation order

1. **Backend `pom.xml`**
   - Bump **Spring Security** to **6.4.11**.
   - Set **commons-lang3** to **3.18.0** (property or dependencyManagement).
   - Add **reactor-netty** override if needed after checking `dependency:tree`.
   - Add **nimbus-jose-jwt** **9.37.4** (or 10.0.2) in dependencyManagement.
   - Keep **Tomcat** at **10.1.45** and **spring-core** at **6.2.11**; add other `spring-*` at 6.2.11 only if Trivy still reports them.

2. **Verify**
   - `mvn clean test` and fix any compatibility issues.
   - `mvn dependency:tree` and confirm versions of tomcat, spring-*, reactor-netty, nimbus-jose-jwt, commons-lang3.
   - Re-run Trivy / Code scanning and confirm backend alerts are gone or documented (e.g. Tomcat installer = N/A).

3. **Frontend**
   - Replace hardcoded JWT strings in E2E/mocks with a named constant and comment.
   - Add Trivy secret-scan exclusions for E2E and mock files.
   - Re-run Trivy and confirm JWT alerts are resolved or accepted as false positives.

4. **Docs**
   - Update `backend/SECURITY_ALERTS.md` with the new CVEs and versions.
   - In Code scanning, if the Tomcat “Windows installer” finding remains, add a short comment that the project uses embedded Tomcat only (N/A).

---

## 4. Quick reference – versions to set

| Dependency | Property / artifact | Version |
|------------|---------------------|---------|
| Tomcat | `tomcat.version` | 10.1.45 (keep) |
| Spring Security | `spring-security.version` | **6.4.11** |
| Spring Core (and Framework) | `dependencyManagement` spring-core | 6.2.11 (keep) |
| Commons Lang3 | `commons-lang3.version` or explicit dep | **3.18.0** |
| Reactor Netty | `reactor-netty.version` or dependencyManagement | **1.2.8** (or per BOM) |
| Nimbus JOSE JWT | dependencyManagement | **9.37.4** |

---

*This plan was created from Trivy/Code scanning alerts and public CVE/advisory information. Re-run scanning after changes and update the plan if new CVEs appear.*
