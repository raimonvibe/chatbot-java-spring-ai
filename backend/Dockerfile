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

# Install only Chromium and wget (minimal); clear apt cache to reduce image and attack surface
RUN apt-get update && apt-get install -y --no-install-recommends \
    chromium-browser \
    wget \
    && rm -rf /var/lib/apt/lists/*

# Run as non-root so Chromium is not privileged
RUN groupadd -r spring && useradd -r -g spring spring
RUN mkdir -p /app/logs && chown -R spring:spring /app/logs
USER spring:spring

# Optional: CHROME_BIN must be under /usr/ (validated at runtime). e.g. /usr/bin/chromium-browser
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
