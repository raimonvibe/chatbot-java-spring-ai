# Security remediation plan 3 – Trivy alerts (Critical / High / Medium / Low)

Plan to resolve the latest Trivy findings in `backend/pom.xml`.  
**Current state:** Spring Boot 3.3.13, `tomcat.version` 10.1.47, `spring-security.version` 6.4.11, Spring Framework 6.2.11 (core/web/webmvc), logback **1.5.25** (bumped from 1.5.19 for Dependabot #11), commons-lang3 3.18.0, reactor-netty 1.2.8, nimbus-jose-jwt 9.37.4.

---

## Critical

### 1. Spring Security – method security bypass on private methods (#92)

| CVE | Issue | Fix version |
|-----|--------|-------------|
| CVE-2025-41232 | Authorization bypass for method security on private methods (with `@EnableMethodSecurity(mode=ASPECTJ)`) | **6.4.6+** |

**Status:** Already on **6.4.11** → fixed.  
**If Trivy still flags:** Add explicit `dependencyManagement` for `spring-security-core` (and optionally `spring-security-aspects`) at **6.4.11** so no transitive older version is used.

---

### 2. org.apache.tomcat/tomcat-juli – console manipulation (#90)

| CVE | Issue | Fix version |
|-----|--------|-------------|
| CVE-2025-55754 | Console manipulation via escape sequences in log messages (tomcat-juli) | **10.1.45+** |

**Status:** `tomcat.version` is **10.1.47**; Spring Boot applies it to embedded Tomcat (including tomcat-juli).  
**If Trivy still flags:** Add explicit `dependencyManagement` for `org.apache.tomcat:tomcat-juli` with version `${tomcat.version}` so the resolved version is forced.

---

## High

### 3. Spring Framework – Annotation Detection (#95)

| CVE | Issue | Fix version |
|-----|--------|-------------|
| CVE-2025-41249 | Annotation detection vulnerability (method security in type hierarchies) | **6.2.11** |

**Status:** Already using **spring-core** (and spring-web, spring-webmvc) **6.2.11** in dependencyManagement → fixed.

---

### 4. spring-security-core – authorization bypass (#93)

| CVE | Issue | Fix version |
|-----|--------|-------------|
| CVE-2025-41248 | Spring Security authorization bypass (related to method security) | **6.4.11** |

**Status:** Already on **6.4.11** → fixed.  
**If still flagged:** Add `dependencyManagement` for `org.springframework.security:spring-security-core` at **6.4.11**.

---

### 5. Tomcat – directory traversal via rewrite / possible RCE (#86)

| CVE | Issue | Fix version |
|-----|--------|-------------|
| CVE-2025-55752 | Directory traversal via Rewrite Valve; possible RCE if PUT enabled | **10.1.45+** |

**Status:** **10.1.47** → fixed.

---

### 6. Tomcat – HTTP/2 “MadeYouReset” DoS (#85)

| CVE | Issue | Fix version |
|-----|--------|-------------|
| CVE-2025-48989 | DoS via HTTP/2 control frames | **10.1.44+** |

**Status:** **10.1.47** → fixed.

---

### 7. Tomcat – DoS in multipart upload (#84)

| CVE | Issue | Fix version |
|-----|--------|-------------|
| CVE-2025-48988 | DoS in multipart upload (memory usage) | **10.1.42+** |
| CVE-2025-61795 | Delayed cleanup of multipart temp files (DoS) | **10.1.47** |

**Status:** **10.1.47** → fixed.

---

## Medium

### 8. spring-webmvc – path traversal (#97)

| CVE | Issue | Fix version |
|-----|--------|-------------|
| CVE-2025-41242 | MVC path traversal | **6.2.10+** |

**Status:** **spring-webmvc 6.2.11** in dependencyManagement → fixed.

---

### 9. Spring Framework – reflected download / non-ASCII headers (#96)

| CVE | Issue | Fix version |
|-----|--------|-------------|
| CVE-2025-41234 | Reflected file download with non-ASCII Content-Disposition | **6.2.8+** |

**Status:** **6.2.11** → fixed.

---

### 10. Tomcat – security constraint bypass pre/post-resources (#88)

| CVE | Issue | Fix version |
|-----|--------|-------------|
| CVE-2025-49125 | Bypass for PreResources/PostResources | **10.1.42+** |

**Status:** **10.1.47** → fixed.

---

### 11. Tomcat – Windows installer untrusted search path (#87)

| CVE | Issue | Fix version |
|-----|--------|-------------|
| CVE-2025-49124 | Windows installer side-loading (icacls path) | **10.1.42+** |

**Note:** Only affects the **Tomcat Windows installer**. We use **embedded Tomcat** (Spring Boot); this CVE does not apply to our runtime. Trivy may still report it for the artifact; we can document as “not applicable” or rely on 10.1.47.

---

### 12. commons-lang3 – uncontrolled recursion (#83)

| CVE | Issue | Fix version |
|-----|--------|-------------|
| CVE-2025-48924 | Uncontrolled recursion | **3.18.0** |

**Status:** `commons-lang3.version` **3.18.0** set. If still flagged, add explicit dependency on `org.apache.commons:commons-lang3` **3.18.0**.

---

### 13. reactor-netty – credential leak via redirects (#82, #23)

| CVE | Issue | Fix version |
|-----|--------|-------------|
| CVE-2025-22227 | Credential leak via redirects | **1.2.8+** |

**Status:** `reactor-netty.version` **1.2.8**. If still flagged, add `dependencyManagement` for `io.projectreactor.netty:reactor-netty-http` **1.2.8**.

---

### 14. nimbus-jose-jwt – uncontrolled recursion (#81)

| CVE | Issue | Fix version |
|-----|--------|-------------|
| CVE-2025-53864 | Uncontrolled recursion | **9.37.4+** |

**Status:** In dependencyManagement at **9.37.4** → fixed.

---

### 15. logback-core – arbitrary code execution / malicious config (#79, #98, #80, Dependabot #11)

| CVE / issue | Fix version |
|-------------|-------------|
| CVE-2025-11226, malicious logback.xml | 1.5.19 |
| **ACE in config file processing** – attacker with write access to logback config can instantiate classes on classpath (Dependabot #11) | **1.5.25** |

**Action:** Bump `logback.version` from **1.5.19** to **1.5.25**. Patched version for the configuration-file ACE is **1.5.25**; versions &lt; 1.5.25 are affected. Keep dependencyManagement for `logback-core` and `logback-classic` using `${logback.version}`.

---

## Low

### 16. CVE-2024-38820 – DataBinder locale (#94)

Fixed in Spring Framework **6.1.14+**; **6.2.11** includes the fix.

---

### 17. Tomcat – DoS (#91), CGI bypass (#89)

| CVE | Fix version |
|-----|-------------|
| DoS (e.g. multipart, CVE-2025-61795) | **10.1.47** |
| CVE-2025-46701 (CGI constraint bypass) | **10.1.41+** |

**Status:** **10.1.47** → fixed.

---

## Implementation checklist

If Trivy still reports any of the above after verifying versions, apply the following so **every** affected artifact is forced to the patched version:

| # | Component | Action in `backend/pom.xml` |
|---|-----------|-----------------------------|
| 1 | Spring Security | Ensure `spring-security.version` **6.4.11**. Add dependencyManagement for **spring-security-core** (and **spring-security-aspects** if used) at 6.4.11. |
| 2 | Tomcat | Keep `tomcat.version` **10.1.47**. Add dependencyManagement for **tomcat-juli** and **tomcat-catalina** (or **tomcat-embed-core** if present) with `${tomcat.version}` so all Tomcat modules align. |
| 3 | Spring Framework | Keep dependencyManagement for **spring-core**, **spring-web**, **spring-webmvc** at **6.2.11**. |
| 4 | Commons Lang3 | Keep `commons-lang3.version` **3.18.0**. Add explicit dependency `org.apache.commons:commons-lang3` with version **3.18.0** if Trivy still flags. |
| 5 | Reactor Netty | Keep `reactor-netty.version` **1.2.8**. Add dependencyManagement for **reactor-netty-http** (and **reactor-netty-core** if needed) at 1.2.8. |
| 6 | Nimbus JOSE+JWT | Keep in dependencyManagement at **9.37.4**. |
| 7 | Logback | Set `logback.version` to **1.5.25** (fixes ACE in config file processing; Dependabot #11). Keep dependencyManagement for **logback-core** and **logback-classic** using `${logback.version}`. |

**Verification**

1. Run `mvn dependency:tree -Dverbose` and confirm no older versions of the above artifacts.
2. Run `mvn clean test`.
3. Re-run Trivy (e.g. `trivy fs backend` or CI security scan).
4. For CVE-2025-49124 (Windows installer): treat as N/A for embedded Tomcat or add a Trivy ignore with justification.

---

## Summary

Most listed issues are **already addressed** by current versions (Spring Security 6.4.11, Tomcat 10.1.47, Spring 6.2.11, logback 1.5.19, etc.). If Trivy still shows findings:

- **Force all Tomcat modules:** Add dependencyManagement for `tomcat-juli`, `tomcat-catalina`, and any other `org.apache.tomcat:*` / `tomcat-embed-*` that appear in the tree with an older version.
- **Force Spring Security modules:** Add dependencyManagement for `spring-security-core` (and related) at 6.4.11.
- **Force commons-lang3:** Add explicit dependency 3.18.0.
- **Reactor Netty:** Add dependencyManagement for reactor-netty 1.2.8 if the BOM does not honor `reactor-netty.version`.

After implementation, re-run Trivy and `mvn clean test` to confirm.
