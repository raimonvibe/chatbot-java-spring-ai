# Multi-stage build for Spring Boot application
# Runtime includes Chromium so headless crawl works for Vercel/SPA sites.

# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# Copy pom.xml and download dependencies (cached layer)
COPY backend/pom.xml ./pom.xml
RUN mvn validate dependency:resolve -B

# Copy source code and build
COPY backend/src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime with Chromium for headless website crawl (Vercel, SPAs)
# Security: see CHROMIUM_DOCKER_SECURITY.md in repo root.
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Install Chromium from Debian (Jammy's chromium-browser is snap-only and does not work in Docker)
# Add Debian Bullseye archive signing keys so apt trusts the repo
RUN apt-get update && apt-get install -y --no-install-recommends wget ca-certificates gnupg \
    && mkdir -p /usr/share/keyrings \
    && gpg --batch --keyserver keyserver.ubuntu.com --recv-keys 0E98404D386FA1D9 6ED0E7B82643E131 605C66F00D6C9793 \
    && gpg --batch --export 0E98404D386FA1D9 6ED0E7B82643E131 605C66F00D6C9793 | gpg --batch --dearmor -o /usr/share/keyrings/debian-bullseye-archive.gpg \
    && echo 'deb [signed-by=/usr/share/keyrings/debian-bullseye-archive.gpg] https://deb.debian.org/debian bullseye main' > /etc/apt/sources.list.d/debian-bullseye.list \
    && echo 'Package: *' > /etc/apt/preferences.d/99-debian-bullseye \
    && echo 'Pin: release n=bullseye' >> /etc/apt/preferences.d/99-debian-bullseye \
    && echo 'Pin-Priority: 100' >> /etc/apt/preferences.d/99-debian-bullseye \
    && apt-get update && apt-get install -y --no-install-recommends chromium \
    && rm -rf /var/lib/apt/lists/*

# Run as non-root so Chromium is not privileged
RUN groupadd -r spring && useradd -r -g spring spring
RUN mkdir -p /app/logs && chown -R spring:spring /app/logs
USER spring:spring

# Debian chromium installs to /usr/bin/chromium (backend also tries chromium-browser)
ENV CHROME_BIN=/usr/bin/chromium
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# MaxRAMPercentage=50 leaves headroom for Chromium (~200–400MB) and native memory on small instances (e.g. Render)
# Override with JAVA_OPTS or JAVA_TOOL_OPTIONS if needed
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=50.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", \
  "app.jar"]
