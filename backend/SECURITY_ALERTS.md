# Security alerts (Code scanning / Trivy)

Summary of dependency CVEs flagged by GitHub Code scanning / Trivy and how this project addresses them.

## Applied fixes (in `pom.xml`)

| Alert | CVE | Severity | Fix |
|-------|-----|----------|-----|
| **Apache Tomcat: console manipulation** (tomcat-juli) | CVE-2025-55754 | Critical | `tomcat.version` set to **10.1.45** in `pom.xml`. Affected: 10.1.0–10.1.44. |
| **Spring Security: method security on private methods** | CVE-2025-41232 | Critical | `spring-security.version` set to **6.4.6**. This app uses `@EnableMethodSecurity(prePostEnabled = true)` (default proxy mode), not `mode=ASPECTJ`, so the bypass only applies to ASPECTJ + private methods; upgrade satisfies scanners. |
| **Spring Framework: annotation detection** | CVE-2025-41249 | High | `dependencyManagement` override for **spring-core 6.2.11** in `pom.xml`. Affects apps using `@EnableMethodSecurity` with security annotations on methods in generic type hierarchies. |

## Verification

After updating `pom.xml`:

- Run `mvn clean compile` (and tests) to ensure the build still works.
- Re-run Code scanning / Trivy; the above alerts should clear once the new versions are used.
- If Spring Framework 6.2.11 causes compatibility issues, remove the `spring-core` override and plan an upgrade to a Spring Boot release that includes Spring Framework 6.2.11+.

## References

- [CVE-2025-55754](https://nvd.nist.gov/vuln/detail/CVE-2025-55754) – Tomcat console manipulation  
- [CVE-2025-41232](https://nvd.nist.gov/vuln/detail/CVE-2025-41232) – Spring Security private method bypass  
- [CVE-2025-41249](https://nvd.nist.gov/vuln/detail/CVE-2025-41249) – Spring Framework annotation detection  
- [Spring Security advisories](https://spring.io/security/cve-2025-22223)
