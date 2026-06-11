package com.prayer_chat.chatbot.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.lettuce.core.RedisClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Per-key burst rate limiter for embed/preview chat traffic.
 * <p>
 * When {@code REDIS_URL} is configured (e.g. Render Key Value internal URL), bucket state is stored in Redis via
 * Bucket4j's distributed proxy so limits hold across all backend instances. Without Redis, falls back to in-memory
 * buckets (correct for single-instance deployments and local development).
 * <p>
 * Fail-open on Redis errors: a Redis outage must not take chat down, so errors fall back to the local bucket for
 * that request (still throttled per instance).
 */
@Service
public class EmbedRateLimiterService {

    private static final Logger logger = LoggerFactory.getLogger(EmbedRateLimiterService.class);

    /** Redis keys expire after the refill period has long passed, so idle IP buckets don't accumulate. */
    private static final Duration REDIS_KEY_TTL = Duration.ofHours(2);

    /** Hard cap for the in-memory fallback map; buckets refill within minutes so a reset is harmless. */
    private static final int MAX_LOCAL_BUCKETS = 50_000;

    private final ProxyManager<byte[]> proxyManager;
    private final RedisClient redisClient;
    private final Map<String, Bucket> localBuckets = new ConcurrentHashMap<>();

    public EmbedRateLimiterService(@Value("${app.rate-limit.redis-url:${REDIS_URL:}}") String redisUrl) {
        RedisClient client = null;
        ProxyManager<byte[]> pm = null;
        if (redisUrl != null && !redisUrl.isBlank()) {
            try {
                client = RedisClient.create(redisUrl.trim());
                pm = LettuceBasedProxyManager.builderFor(client)
                    .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(REDIS_KEY_TTL))
                    .build();
                logger.info("Embed rate limiting backed by Redis (shared across instances)");
            } catch (Exception e) {
                logger.warn("Could not initialize Redis rate limiter, falling back to in-memory: {}", e.getMessage());
                if (client != null) {
                    try { client.shutdown(); } catch (Exception ignored) { /* best effort */ }
                    client = null;
                }
                pm = null;
            }
        } else {
            logger.info("REDIS_URL not set; embed rate limiting is in-memory (per instance)");
        }
        this.redisClient = client;
        this.proxyManager = pm;
    }

    /**
     * Try to consume one token from the bucket identified by {@code key}.
     *
     * @param key          stable bucket key (e.g. {@code embedId:<ip>:<chatbotId>})
     * @param limit        max tokens per refill period
     * @param refillPeriod period after which the bucket refills to {@code limit}
     * @return true if the request is allowed, false if the limit is exhausted
     */
    public boolean tryConsume(String key, long limit, Duration refillPeriod) {
        if (proxyManager != null) {
            try {
                Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.classic(limit, Refill.intervally(limit, refillPeriod)))
                    .build();
                Bucket bucket = proxyManager.builder()
                    .build(key.getBytes(StandardCharsets.UTF_8), configSupplier);
                return bucket.tryConsume(1);
            } catch (Exception e) {
                logger.warn("Redis rate limiter error for key prefix '{}', using local fallback: {}",
                    key.length() > 8 ? key.substring(0, 8) : key, e.getMessage());
            }
        }
        if (localBuckets.size() > MAX_LOCAL_BUCKETS) {
            logger.warn("Local rate-limit bucket map exceeded {} entries; resetting", MAX_LOCAL_BUCKETS);
            localBuckets.clear();
        }
        Bucket bucket = localBuckets.computeIfAbsent(key, k -> Bucket.builder()
            .addLimit(Bandwidth.classic(limit, Refill.intervally(limit, refillPeriod)))
            .build());
        return bucket.tryConsume(1);
    }

    @PreDestroy
    void shutdown() {
        if (redisClient != null) {
            try {
                redisClient.shutdown();
            } catch (Exception e) {
                logger.debug("Redis client shutdown: {}", e.getMessage());
            }
        }
    }
}
