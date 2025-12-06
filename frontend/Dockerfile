# ─────────────────────────────────────────────────────────────
# Next.js 14+ Production Dockerfile (standalone mode – < 100 MB)
# ─────────────────────────────────────────────────────────────

# Stage 1 – Install only production dependencies (best layer caching)
FROM node:20-bookworm-slim AS deps
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci --only=production --ignore-scripts

# Stage 2 – Build the application
FROM node:20-bookworm-slim AS builder
WORKDIR /app

# Install ALL dependencies (dev deps needed for build)
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci

# Copy source code
COPY frontend/ .

# Disable Next.js telemetry
ENV NEXT_TELEMETRY_DISABLED=1

# Build
RUN npm run build

# Stage 3 – Final minimal production image
FROM node:20-bookworm-slim AS runner
WORKDIR /app

ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME="0.0.0.0"

# Non-root user (security)
RUN addgroup --system --gid 1001 nodejs \
    && adduser --system --uid 1001 nextjs

# Copy only what is strictly needed for standalone mode
COPY --from=builder --chown=nextjs:nodejs /app/next.config.js ./
COPY --from=builder --chown=nextjs:nodejs /app/public ./public

# Standalone includes server.js + node_modules + .next
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs

EXPOSE 3000

# Simple & reliable health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:3000 || exit 1

# Start the app
CMD ["node", "server.js"]
