# Dependabot alert #21 – what it is and how to fix it

GitHub’s Dependabot reported **1 high severity** vulnerability. The exact alert is only visible when you’re **logged in** to GitHub.

## How to see the exact alert

1. Open: **https://github.com/raimonvibe/chatbot-java-spring-ai/security/dependabot/21**  
   (You must be logged in; otherwise you may get 404.)
2. Or: repo → **Security** → **Dependabot alerts** → open the **High** alert.

There you’ll see:

- **Package** (e.g. `org.yaml:snakeyaml`, `com.fasterxml.jackson.core:jackson-databind`, etc.)
- **CVE** (e.g. CVE-2025-xxxxx)
- **Fixed in version** (e.g. “Upgrade to 2.x.y”)

## How to fix it

### Option A: Use Dependabot’s PR (recommended)

1. In the same Dependabot alert page, click **Create Dependabot security update** (or open the PR if one exists).
2. Review the PR (it will bump the vulnerable dependency).
3. Merge after your tests pass.

### Option B: Fix it yourself in `backend/pom.xml`

1. Note the **package name** and **fixed version** from the alert.
2. In `backend/pom.xml`:
   - If the package already has a version in `<properties>` (e.g. `snakeyaml`, `json-smart`), set that property to the fixed version.
   - If it’s a direct dependency, set its `<version>` to the fixed version.
   - If it’s **transitive** (comes from Spring Boot or another lib), add an **override** in `<dependencyManagement>` or an explicit dependency with the fixed version.
3. Run:

   ```bash
   cd backend && mvn dependency:tree
   ```

   and confirm the vulnerable artifact is now at the fixed version.
4. Run tests, then commit and push.

## What this project already overrides (for security)

These are already pinned in `backend/pom.xml` to address known CVEs or alignment:

- **Tomcat** 10.1.49 (CVE-2025-66614, etc.)
- **Logback** 1.5.32 (CVE-2024-12798, etc.)
- **Commons Lang3** 3.20.0 (CVE-2025-48924)
- **Reactor Netty** 1.3.3 (CVE-2025-22227)
- **Spring Framework** 6.2.16 (CVE-2025-41249, etc.)
- **PostgreSQL** 42.7.8
- **json-smart** 2.6.0 (CVE-2023-1370 fixed in 2.4.9+)
- **Netty** 4.2.9.Final

So the high alert is likely one of:

- A **transitive** dependency not yet overridden (e.g. `snakeyaml` brought in by Spring Boot).
- A **new CVE** for a package we already override (then bump to the version Dependabot suggests).
- A **frontend** dependency (if the alert is for the same repo and Dependabot scans both backend and frontend).

## If the alert is for SnakeYAML

Spring Boot 3.3.13 pulls in `org.yaml:snakeyaml` **2.2**. If the alert says to upgrade snakeyaml:

1. In `backend/pom.xml` `<properties>`, add (or update):

   ```xml
   <snakeyaml.version>2.5</snakeyaml.version>
   ```

2. In `<dependencyManagement>` (or in the same place you override other BOM versions), add:

   ```xml
   <dependency>
       <groupId>org.yaml</groupId>
       <artifactId>snakeyaml</artifactId>
       <version>${snakeyaml.version}</version>
   </dependency>
   ```

3. Run `mvn dependency:tree -Dverbose=false | findstr snakeyaml` (Windows) or `... | grep snakeyaml` (Mac/Linux) to confirm the version.

## Run a full dependency check (optional)

To list known vulnerabilities yourself:

```bash
cd backend
mvn org.owasp:dependency-check-maven:check -Ddependency-check.skip=false
```

(Requires the OWASP dependency-check plugin in the pom; the project may have it disabled by default with `dependency-check.skip=true`.)

---

**Summary:** Log in to GitHub → open the Dependabot alert #21 → note package and fixed version → either merge Dependabot’s PR or add an override in `backend/pom.xml` (and optionally frontend) as above.
