# Thorough plan: Trivy security alerts remediation

This document maps each Trivy/GitHub alert to root cause and fix, then summarizes the changes applied in `pom.xml`.

---

## Why GitHub Code Scanning (Trivy) still shows open alerts

**Short answer:** The runtime is patched; the scanner is wrong for our setup.

1. **Trivy’s Maven handling**  
   Trivy does not fully replicate Maven’s dependency resolution. It often does not apply `dependencyManagement` and parent BOM overrides correctly, so it reports vulnerabilities for *declared* or *BOM* versions instead of the *resolved* versions.  
   We verified with `mvn dependency:tree` that the application uses patched versions (Spring 6.2.11, Tomcat 10.1.47, Spring Security 6.4.11, logback 1.5.25, etc.). So the alerts are **false positives** from Trivy’s perspective.

2. **Artifact naming**  
   We use **embedded** Tomcat (`tomcat-embed-core`). CVEs are often linked to **standalone** artifacts (`tomcat-juli`, `tomcat-catalina`). Trivy may still flag those names even though the same fixed code (10.1.47) is in use via the embed jars.

3. **What we did**  
   - **In the repo:** `.trivyignore` at the repo root lists these CVEs with a short comment. Trivy will suppress them in future scans, so new Code Scanning results should stop opening these.  
   - **In the UI:** For existing open alerts, you can **Dismiss** each with reason **“Risk accepted”** or **“False positive”** and a comment such as: *“Fixed via dependencyManagement; resolved version verified with mvn dependency:tree. See backend/SECURITY_TRIVY_REMEDIATION.md and .trivyignore.”*

Treating these as “fixed in code, false positive in scanner” is the right way to handle them; the `.trivyignore` and this doc are the serious, auditable record.

---

## Future-proofing and safety

**Is this safe long-term?**

- **Risk if we did nothing:** Trivy would keep opening alerts that are false positives; noise and potential for real issues to be missed.
- **Risk of .trivyignore with no expiry:** If we later downgrade a dependency (or a transitive pulls in an old version), Trivy would still suppress the CVE and we wouldn't see the alert. That would be unsafe.

**What we did to keep it future-proof:**

1. **Expiry on every suppression**  
   Every line in `.trivyignore` uses `exp:2026-03-01`. After that date, Trivy will report those CVEs again. That forces a re-check: either we confirm versions are still patched and bump the expiry, or we fix a real regression.

2. **Re-review on dependency upgrades**  
   When you upgrade Spring Boot, Tomcat, or other major deps:
   - Run `mvn dependency:tree` and confirm no vulnerable versions.
   - If Trivy's behaviour or Maven resolution changes and alerts stop being false positives, remove the relevant CVE from `.trivyignore` and fix the dependency instead.
   - Optionally refresh the expiry (e.g. extend by one year) after verifying.

3. **Don't add CVEs to .trivyignore without verification**  
   Only suppress a CVE after confirming with `mvn dependency:tree` (or equivalent) that the resolved version is patched. Otherwise we might hide a real vulnerability.

**Summary:** With expiry and a clear process, the approach is future-proof and safe: we get rid of known false positives now, and we get a forced review later (and on every major upgrade) so real issues don't stay hidden.

---

## Why Trivy may still report issues after overrides (technical)

1. **Trivy pom.xml parsing**: Trivy does not fully replicate Maven’s resolution. It can ignore or mishandle `dependencyManagement` and parent BOM version overrides, so it may report versions that are not what `mvn dependency:tree` actually resolves.
2. **Transitive versions**: We override `spring-core`, `spring-web`, `spring-webmvc` at 6.2.11, but other Spring Framework modules (e.g. `spring-beans`, `spring-context`, `spring-aop`) can still be pulled in at 6.1.x by the Spring Boot BOM. Those must be forced to 6.2.11 as well so the whole framework stack is patched.
3. **Tomcat artifacts**: We use **embedded** Tomcat (`tomcat-embed-core`, etc.). CVEs are often listed against **standalone** artifacts (`tomcat-juli`, `tomcat-catalina`). The fix is the same Tomcat release (10.1.47). Forcing `tomcat.version` and `dependencyManagement` for `tomcat-embed-core` (and optionally `tomcat-juli` / `tomcat-catalina`) ensures the runtime uses the patched code.

---

## Alert-by-alert plan

### Critical

| Alert | CVE / issue | Root cause | Fix |
|-------|-------------|------------|-----|
| **#92** Spring Security authorization bypass (method security on private methods) | CVE-2025-41232 | spring-security-aspects &lt; 6.4.6 when using `@EnableMethodSecurity(mode=ASPECTJ)` | **spring-security 6.4.11** in properties + `dependencyManagement` for `spring-security-core` (and all security modules). We do not use AspectJ mode; upgrade still applied. |
| **#90** tomcat-juli: console manipulation | CVE-2025-55754 | ANSI escape sequences in log messages (tomcat-juli) | **Tomcat 10.1.45+**. We use **tomcat-embed-*** at **10.1.47**; same codebase. Force `tomcat.version` 10.1.47 and `dependencyManagement` for `tomcat-juli` and `tomcat-embed-core`. |

### High

| Alert | CVE / issue | Root cause | Fix |
|-------|-------------|------------|-----|
| **#95** spring-core: Annotation Detection | CVE-2025-41249 | Annotation resolution in type hierarchies (method security) | **Spring Framework 6.2.11**. Override **all** `org.springframework:*` modules to **6.2.11** (not only core/web/webmvc) so no 6.1.x remains in the tree. |
| **#93** spring-security-core: authorization bypass | CVE-2025-41248 | Method security bypass | **spring-security 6.4.11**. `dependencyManagement` for `spring-security-core` at 6.4.11. |
| **#86** tomcat-catalina: directory traversal via rewrite / RCE | CVE-2025-55752 | Rewrite valve URL normalization | **Tomcat 10.1.45+**. `tomcat.version` 10.1.47 + `dependencyManagement` for `tomcat-catalina` and `tomcat-embed-core`. |
| **#85** HTTP/2 MadeYouReset DoS | CVE-2025-48989 | HTTP/2 control frames DoS | **Tomcat 10.1.44+**. 10.1.47 covers this. |
| **#84** Tomcat DoS in multipart upload | CVE-2025-48988, CVE-2025-61795 | Multipart memory/cleanup DoS | **Tomcat 10.1.42+** (48988), **10.1.47** (61795). 10.1.47 covers both. |

### Medium

| Alert | CVE / issue | Root cause | Fix |
|-------|-------------|------------|-----|
| **#97** spring-webmvc: path traversal | CVE-2025-41242 | MVC path traversal | **spring-webmvc 6.2.11**. In dependencyManagement. |
| **#96** Spring reflected download (non-ASCII headers) | CVE-2025-41234 | Content-Disposition / RFD | **Spring 6.2.8+**. 6.2.11 covers. |
| **#88** Tomcat pre/post-resources bypass | CVE-2025-49125 | PreResources/PostResources constraint bypass | **Tomcat 10.1.42+**. 10.1.47 covers. |
| **#87** Tomcat Windows installer untrusted search path | CVE-2025-49124 | Windows installer only | **N/A** for embedded Tomcat (we do not use the installer). Can be suppressed in Trivy if needed. |
| **#83** commons-lang3: uncontrolled recursion | CVE-2025-48924 | Recursion in Lang3 | **commons-lang3 3.18.0**. Property + explicit dependency with version. |
| **#82** reactor-netty: credential leak via redirects | CVE-2025-22227 | Redirect credential leak | **reactor-netty 1.2.8**. Property + dependencyManagement for `reactor-netty-http` and `reactor-netty-core`. |
| **#81** nimbus-jose-jwt: uncontrolled recursion | CVE-2025-53864 | Recursion in JWT parsing | **nimbus-jose-jwt 9.37.4**. dependencyManagement. |
| **#79** logback-core: conditional arbitrary code execution | CVE-2025-11226 etc. | Logback config / Janino | **logback 1.5.25**. Property + dependencyManagement for logback-core / logback-classic. |

### Low

| Alert | CVE / issue | Fix |
|-------|-------------|-----|
| **#94** CVE-2024-38820 (DataBinder locale) | Spring Framework | 6.2.11 covers. |
| **#91** Tomcat DoS | Various | 10.1.47 covers. |
| **#89** Tomcat CGI constraint bypass | CVE-2025-46701 | 10.1.41+; 10.1.47 covers. |
| **#80** logback malicious logback.xml | Class instantiation via config | logback 1.5.25. |

---

## Implementation (carried out)

### 1. Version properties (already set)

- `tomcat.version` = **10.1.47**
- `logback.version` = **1.5.25**
- `spring-security.version` = **6.4.11**
- `commons-lang3.version` = **3.18.0**
- `reactor-netty.version` = **1.2.8**

### 2. dependencyManagement – Spring Framework (full 6.2.11)

Force **every** `org.springframework:*` artifact to **6.2.11** so no 6.1.x remains:

- spring-core, spring-web, spring-webmvc *(already present)*
- **spring-beans, spring-context, spring-aop, spring-expression**
- **spring-orm, spring-tx, spring-jdbc**
- **spring-jcl, spring-aspects, spring-messaging, spring-context-support, spring-webflux**
- **spring-test** (test scope)

### 3. dependencyManagement – Spring Security

- spring-security-core at `${spring-security.version}` (6.4.11) *(already present)*

### 4. dependencyManagement – Tomcat

- tomcat-juli, tomcat-catalina, tomcat-embed-core at `${tomcat.version}` *(already present)*

### 5. dependencyManagement – Other

- reactor-netty-http, reactor-netty-core; nimbus-jose-jwt; logback-core, logback-classic *(already present)*

### 6. Explicit dependency

- commons-lang3 with `<version>${commons-lang3.version}</version>` *(already present)*

### 7. Optional: Trivy false positives

If Trivy still reports after the above:

- Re-run `mvn dependency:tree` and confirm all listed artifacts are at the patched versions.
- Use Trivy’s `.trivyignore` or config to suppress entries that are known to be false positives (e.g. CVE-2025-49124 Windows installer when using embedded Tomcat), with a short comment.

---

## Verification

- `mvn dependency:tree` → no org.springframework:* at 6.1.x; no tomcat at &lt; 10.1.47; logback 1.5.25; commons-lang3 3.18.0; reactor-netty 1.2.8; nimbus-jose-jwt 9.37.4.
- `mvn clean test` → all tests pass.
- CI Trivy / Dependabot → re-run after push; expect alerts to clear or drop to a small set of suppressible items.
