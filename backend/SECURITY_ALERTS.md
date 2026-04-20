# Security alerts (Code scanning / Trivy)

Summary of dependency CVEs flagged by GitHub Code scanning / Trivy and how this project addresses them.

## Applied fixes (in `pom.xml`)

| Alert | CVE | Severity | Fix |
|-------|-----|----------|-----|
| **Apache Tomcat** (console manipulation, directory traversal, DoS, HTTP/2, constraint bypass) | CVE-2025-55754, CVE-2025-55752, CVE-2025-53506, CVE-2025-52520, CVE-2025-49125 | Critical/High | `tomcat.version` **10.1.45** |
| **Spring Security** (authorization bypass, method security) | CVE-2025-41248, CVE-2025-22223, CVE-2025-41232 | Critical/High | `spring-security.version` **6.4.11** |
| **Spring Framework** (annotation detection, path traversal, RFD) | CVE-2025-41249, CVE-2025-41242, CVE-2025-41234 | High | `dependencyManagement` **spring-core 6.2.11** |
| **Commons Lang3** (uncontrolled recursion) | CVE-2025-48924 | Medium | `commons-lang3.version` **3.18.0** |
| **Reactor Netty** (credential leak via redirects) | CVE-2025-22227 | Medium | `reactor-netty.version` **1.2.8** |
| **Nimbus JOSE+JWT** (uncontrolled recursion) | CVE-2025-53864 | Medium | `dependencyManagement` **nimbus-jose-jwt 9.37.4** |

## Frontend: E2E mock token (no secrets in code)

- E2E tests use a **mock** auth token (valid JWT shape for app validation only); it is **not** a real credential.
- The value lives in `frontend/e2e/helpers/test-auth-constants.ts` and is referenced via `E2E_MOCK_AUTH_TOKEN` / `E2E_MOCK_TOKEN_MARKER`.
- **Trivy secret scan:** `trivy-secret.yaml` in the repo root excludes `frontend/e2e/` and related helper paths so this mock is not reported as a secret finding. No production secrets are stored in the repo.

## Verification

- Run `mvn clean test` in `backend` to confirm build and tests pass.
- Re-run Code scanning / Trivy; dependency alerts should clear for the versions above.
- If any override causes compatibility issues, see `SECURITY_REMEDIATION_PLAN.md` for alternatives.

## References

- [CVE-2025-55754](https://nvd.nist.gov/vuln/detail/CVE-2025-55754), [CVE-2025-55752](https://nvd.nist.gov/vuln/detail/CVE-2025-55752), [CVE-2025-49125](https://nvd.nist.gov/vuln/detail/CVE-2025-49125) – Tomcat  
- [CVE-2025-41248](https://spring.io/security/cve-2025-41248), [CVE-2025-22223](https://spring.io/security/cve-2025-22223) – Spring Security  
- [CVE-2025-41249](https://spring.io/security/cve-2025-41249), [CVE-2025-41242](https://spring.io/security/cve-2025-41242), [CVE-2025-41234](https://spring.io/security/cve-2025-41234) – Spring Framework  
- [CVE-2025-48924](https://nvd.nist.gov/vuln/detail/CVE-2025-48924) – Commons Lang3  
- [CVE-2025-22227](https://spring.io/security/cve-2025-22227) – Reactor Netty  
- [CVE-2025-53864](https://nvd.nist.gov/vuln/detail/CVE-2025-53864) – Nimbus JOSE+JWT
