# Security remediation plan 2 – remaining Trivy alerts

Addresses: logback-core, spring-webmvc, commons-lang3, reactor-netty, nimbus-jose-jwt, Tomcat (DoS, CGI), CVE-2024-38820.

---

## 1. Logback-core (Medium / Low)

| CVE | Issue | Fix version |
|-----|--------|-------------|
| CVE-2024-12798 | Expression Language Injection / Janino RCE | 1.5.13+ |
| CVE-2025-11226 | Conditional arbitrary code execution | **1.5.19** |
| Malicious logback.xml (class instantiation) | Low – config abuse | **1.5.19** (same line) |

**Action:** Set `logback.version` to **1.5.19** in `backend/pom.xml` (or add dependencyManagement for `ch.qos.logback:logback-core` and `ch.qos.logback:logback-classic` 1.5.19).

---

## 2. Spring Framework – spring-webmvc (Medium)

| CVE | Issue | Fix version |
|-----|--------|-------------|
| CVE-2025-41242 | MVC path traversal | **6.2.10+** |

**Action:** Add `dependencyManagement` for `org.springframework:spring-webmvc` at **6.2.11** (align with existing spring-core 6.2.11).

---

## 3. Commons Lang3 (Medium)

Already set `commons-lang3.version` to **3.18.0** (CVE-2025-48924). If Trivy still flags it, add an explicit dependency on `org.apache.commons:commons-lang3` with version **3.18.0** so it overrides any transitive older version.

---

## 4. Reactor Netty (Medium)

Already set `reactor-netty.version` to **1.2.8** (CVE-2025-22227). If the parent does not use this property, add `dependencyManagement` for `io.projectreactor.netty:reactor-netty-http` at **1.2.8**.

---

## 5. Nimbus JOSE+JWT (Medium)

Already in `dependencyManagement` at **9.37.4** (CVE-2025-53864). Confirm no other BOM overrides it; if still flagged, ensure the artifact is explicitly listed.

---

## 6. Tomcat (Low)

| CVE | Issue | Fix version |
|-----|--------|-------------|
| CVE-2025-61795 | DoS – multipart resource cleanup | **10.1.47** |
| CVE-2025-46701 | CGI security constraint bypass | 10.1.41+ (already on 10.1.45) |

**Action:** Bump `tomcat.version` from **10.1.45** to **10.1.47** to cover the DoS fix.

---

## 7. CVE-2024-38820 (Low) – Spring Framework DataBinder

Locale-independent lowercase for disallowedFields; fixed in Spring Framework **6.1.14** (6.1.x) and in later 6.2.x.

**Action:** Keeping **spring-core** (and spring-webmvc) at **6.2.11** is sufficient; 6.2.11 is after the 6.1.14 fix and includes DataBinder fixes.

---

## Implementation summary

| Component | Change in `backend/pom.xml` |
|-----------|-----------------------------|
| Logback | `logback.version` **1.5.19** or dependencyManagement for logback-core / logback-classic |
| Spring Web MVC | dependencyManagement **spring-webmvc 6.2.11** |
| Tomcat | `tomcat.version` **10.1.47** |
| Commons Lang3 | Keep 3.18.0; add explicit dependency if still flagged |
| Reactor Netty | Keep 1.2.8; add dependencyManagement if needed |
| Nimbus JOSE+JWT | Keep 9.37.4 in dependencyManagement |

After changes: run `mvn clean test` and re-run Trivy.
