# Memory Optimization Plan - Prayer Chat Backend

## 🚨 Critical Problem Statement

**Current Issue:** Backend crashes with `OutOfMemoryError` (exceeds 512MB Render limit) when processing chat requests, causing 502 Bad Gateway errors.

**Date Identified:** January 11, 2026
**Current Memory Limit:** 512MB (Render free tier)
**Observed Behavior:**
- Service crashes repeatedly at 9:53 AM, 9:55 AM, 9:58 AM
- Chat endpoint `/api/chat/1` returns 502 Bad Gateway
- OAuth login works fine (no memory issues)
- Error message: "Ran out of memory (used over 512MB)"

---

## 🔍 Root Causes Identified

### 1. Bible Data Auto-Loading (HIGH IMPACT - ~100MB)
**Location:** `application.yml` - `app.bible.auto-load: true`
**Issue:** Loads entire Bible dataset (Old + New Testament) into memory on startup
**Estimated Memory:** 50-100MB depending on translations
**Impact:** Always resident in memory, even when not actively used

### 2. Vector Store / Embeddings Cache (HIGH IMPACT - ~150MB)
**Location:** `AiChatbotService` uses Spring AI `VectorStore` and `EmbeddingModel`
**Issue:** Spring AI's vector store keeps embeddings in memory without size limits
**Estimated Memory:** 50-200MB depending on cached embeddings
**Impact:** Grows with each unique query, no eviction strategy

### 3. Conversation History Storage (MEDIUM IMPACT - ~30MB)
**Location:** `max-conversation-history: 10` messages per session
**Issue:** Each conversation keeps 10 messages in memory per active session
**Estimated Memory:** 1-5MB per active conversation × concurrent users
**Impact:** Multiplied by number of concurrent users (10 users = 50MB)

### 4. Database Connection Pool (LOW-MEDIUM IMPACT - ~10MB)
**Location:** `hikari.maximum-pool-size: 10`
**Issue:** 10 database connections maintained in pool
**Estimated Memory:** 5-10MB for connection pool overhead
**Impact:** Constant overhead, always allocated

### 5. Spring AI Model Loading (HIGH IMPACT - ~50MB)
**Location:** Anthropic Claude + Cohere embedding models
**Issue:** Spring AI framework overhead + model metadata
**Estimated Memory:** 30-50MB for framework initialization
**Impact:** Always resident, loaded on startup

### 6. In-Memory Caches (MEDIUM IMPACT - ~30MB)
**Location:** Multiple services use `ConcurrentHashMap` for caching
**Examples:**
- `RateLimitingFilter` - Rate limit buckets per IP
- `JwtAuthenticationFilter` - Token validation cache
- `WebsiteContentRepository` - Cached website content
**Estimated Memory:** 10-30MB total
**Impact:** Grows with usage, no cleanup strategy

**TOTAL ESTIMATED:** 370-500MB baseline + 50-100MB spikes = **420-600MB**
**PROBLEM:** Exceeds 512MB Render limit, causing crashes

---

## ⚡ Phase 1: Immediate Quick Wins (0-2 hours)

**GOAL:** Reduce memory to <400MB and prevent crashes
**PRIORITY:** 🔴 CRITICAL - Deploy immediately

### 1.1 Disable Bible Auto-Loading
**Impact:** -50-100MB
**Effort:** 5 minutes
**Risk:** Low

**Action - Add Render Environment Variable:**
```bash
BIBLE_AUTO_LOAD=false
```

**Implementation Steps:**
1. Go to Render Dashboard → Your service → Environment
2. Add variable: `BIBLE_AUTO_LOAD` = `false`
3. Click "Save"
4. Service will auto-deploy

**Trade-off:**
- Bible verses load on-demand only (first query slower by 1-2s)
- Subsequent queries cached in database
- Acceptable for current use case

**Testing:**
```bash
# After deploy, check logs for:
"Bible auto-load disabled" (should appear)
# NOT: "Loading Old Testament" (should NOT appear)
```

---

### 1.2 Reduce Database Connection Pool
**Impact:** -5-8MB
**Effort:** 10 minutes
**Risk:** Low

**Action - Update application.yml:**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: ${DATABASE_MAX_POOL_SIZE:5}  # Reduced from 10
      minimum-idle: 2  # Reduced from 5
      connection-timeout: 30000
```

**Action - Add Render Environment Variable:**
```bash
DATABASE_MAX_POOL_SIZE=5
```

**Trade-off:**
- Slightly reduced concurrent request capacity
- Acceptable for current traffic (<10 concurrent users)
- Can increase later if needed

---

### 1.3 Reduce Conversation History Limit
**Impact:** -15-25MB (5 concurrent conversations)
**Effort:** 10 minutes
**Risk:** Medium

**Action - Update application.yml:**
```yaml
app:
  chatbot:
    max-conversation-history: ${MAX_CONVERSATION_HISTORY:5}  # Reduced from 10
```

**Action - Add Render Environment Variable:**
```bash
MAX_CONVERSATION_HISTORY=5
```

**Trade-off:**
- Chatbot has shorter context window (5 messages vs 10)
- May slightly affect conversation coherence
- Monitor user feedback

---

### 1.4 Reduce Embedding Batch Size
**Impact:** -15-30MB during batch operations
**Effort:** 10 minutes
**Risk:** Low

**Action - Update application.yml:**
```yaml
app:
  embedding:
    batch-size: ${EMBEDDING_BATCH_SIZE:25}  # Reduced from 100
```

**Action - Add Render Environment Variable:**
```bash
EMBEDDING_BATCH_SIZE=25
```

**Trade-off:**
- Slower embedding generation for admin operations
- Doesn't affect chat performance
- Acceptable

---

### 1.5 Configure JVM Heap Limits
**Impact:** Better memory utilization, prevents overshoot
**Effort:** 5 minutes
**Risk:** Low

**Action - Add Render Environment Variable:**
```bash
JAVA_OPTS=-Xms256m -Xmx450m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication
```

**Explanation:**
- `-Xms256m`: Start with 256MB heap
- `-Xmx450m`: Max 450MB heap (leave 62MB for non-heap)
- `UseG1GC`: Better pause times than default GC
- `UseStringDeduplication`: Reduce duplicate string memory

**Testing:**
```bash
# Check if JVM args applied (in logs):
java -XX:+PrintFlagsFinal -version | grep -i heap
```

---

**Phase 1 Summary:**
- **Expected Memory Reduction:** 85-163MB
- **New Baseline:** 260-400MB (safe zone)
- **Time to Implement:** 30-60 minutes
- **Deploy Order:** All at once, then monitor

---

## 🛠️ Phase 2: Short-Term Optimizations (2-8 hours)

**GOAL:** Optimize data structures and caching strategies
**PRIORITY:** 🟡 HIGH - Implement within 1 week

### 2.1 Implement LRU Cache for Embeddings
**Impact:** -30-80MB
**Effort:** 2-3 hours
**Risk:** Low

**Location:** `AiChatbotService.java`

**Implementation:**
```java
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

@Service
public class AiChatbotService {

    // Replace unbounded cache with size-limited LRU cache
    private final Cache<String, List<Document>> embeddingCache = CacheBuilder.newBuilder()
        .maximumSize(50)  // Limit to 50 cached query embeddings
        .expireAfterAccess(30, TimeUnit.MINUTES)  // Evict after 30 min idle
        .recordStats()  // Monitor hit rate
        .build();

    public List<Document> searchSimilarContent(String query) {
        return embeddingCache.get(query, () -> {
            // Generate embedding and search
            return vectorStore.similaritySearch(query);
        });
    }
}
```

**Dependencies - Add to pom.xml:**
```xml
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>32.1.3-jre</version>
</dependency>
```

**Trade-off:**
- Older/less-used embeddings evicted
- May need regeneration (acceptable, queries are fast)
- Better memory predictability

**Testing:**
```java
// Monitor cache stats
logger.info("Embedding cache stats: {}", embeddingCache.stats());
```

---

### 2.2 Lazy-Load Bible Data with Weak References
**Impact:** -20-40MB
**Effort:** 3-4 hours
**Risk:** Medium

**Location:** `BibleDataLoaderService.java`

**Implementation:**
```java
@Service
public class BibleDataLoaderService {

    // Use WeakReference - GC can reclaim when memory pressure
    private WeakReference<List<BibleVerse>> oldTestamentCache;
    private WeakReference<List<BibleVerse>> newTestamentCache;

    public List<BibleVerse> getNewTestament() {
        List<BibleVerse> verses = newTestamentCache != null ?
            newTestamentCache.get() : null;

        if (verses == null) {
            // Reload from database if GC reclaimed
            verses = bibleVerseRepository.findByTestament("NEW");
            newTestamentCache = new WeakReference<>(verses);
            logger.info("Reloaded New Testament from database ({} verses)", verses.size());
        }

        return verses;
    }
}
```

**Trade-off:**
- Data may need reloading if GC reclaims it
- Small performance penalty on reload (1-2s)
- Better memory management under pressure

---

### 2.3 Implement Pagination for Large Queries
**Impact:** -10-30MB per query
**Effort:** 2-3 hours
**Risk:** Low

**Location:** `WebsiteContentRepository`, `ConversationRepository`

**Implementation:**
```java
// Add pagination to large dataset queries
@Repository
public interface WebsiteContentRepository extends JpaRepository<WebsiteContent, Long> {

    @Query("SELECT wc FROM WebsiteContent wc WHERE wc.chatbot.id = :chatbotId")
    Page<WebsiteContent> findByChatbotIdPaginated(
        @Param("chatbotId") Long chatbotId,
        Pageable pageable
    );
}

// Usage in service
public List<WebsiteContent> getRelevantContent(Long chatbotId) {
    Pageable pageable = PageRequest.of(0, 50, Sort.by("createdAt").descending());
    Page<WebsiteContent> page = repository.findByChatbotIdPaginated(chatbotId, pageable);
    return page.getContent();  // Only 50 items loaded, not all
}
```

**Trade-off:**
- May need multiple queries for very large datasets
- Acceptable - most chatbots have <1000 pages
- Better scalability

---

### 2.4 Add Memory Monitoring Endpoint
**Impact:** 0MB (diagnostic only)
**Effort:** 1 hour
**Risk:** None

**Location:** New file `MemoryMonitorController.java`

**Implementation:**
```java
@RestController
@RequestMapping("/actuator/memory")
public class MemoryMonitorController {

    @GetMapping
    public Map<String, Object> getMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();

        return Map.of(
            "totalMemoryMB", runtime.totalMemory() / 1024 / 1024,
            "freeMemoryMB", runtime.freeMemory() / 1024 / 1024,
            "usedMemoryMB", usedMemory / 1024 / 1024,
            "maxMemoryMB", runtime.maxMemory() / 1024 / 1024,
            "usagePercent", (usedMemory * 100) / runtime.maxMemory(),
            "timestamp", LocalDateTime.now()
        );
    }

    @GetMapping("/gc")
    public Map<String, String> triggerGC() {
        System.gc();
        return Map.of("status", "GC triggered", "note", "Suggestion only, JVM decides");
    }
}
```

**Benefit:**
- Monitor memory usage in production
- Alert when approaching 450MB
- Proactive monitoring

**Usage:**
```bash
# Check current memory
curl https://chatbot-backend-4mp4.onrender.com/actuator/memory

# Example response:
# {"usedMemoryMB": 320, "maxMemoryMB": 450, "usagePercent": 71}
```

---

**Phase 2 Summary:**
- **Expected Memory Reduction:** 60-150MB
- **New Baseline:** 200-250MB (very safe)
- **Time to Implement:** 8-11 hours
- **Deploy Order:** One task per day, monitor each

---

## 🔧 Phase 3: Medium-Term Architecture (8-16 hours)

**GOAL:** Architectural improvements for scalability
**PRIORITY:** 🟢 MEDIUM - Implement within 2-4 weeks

### 3.1 Implement Redis for Distributed Caching
**Impact:** -50-100MB
**Effort:** 4-6 hours
**Cost:** Render Redis addon ($10/month for 25MB)
**Risk:** Medium

**Benefits:**
- Move embeddings cache out of application memory
- Share cache across multiple instances (horizontal scaling)
- Persistent cache survives restarts
- Better cache eviction strategies

**Implementation:**

**1. Add Dependencies (pom.xml):**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

**2. Configure Redis (application.yml):**
```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 1800000  # 30 minutes
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
```

**3. Enable Caching:**
```java
@EnableCaching
@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .disableCachingNullValues();
    }
}
```

**4. Update Service:**
```java
@Service
public class AiChatbotService {

    @Cacheable(value = "embeddings", key = "#query")
    public List<Document> searchSimilarContent(String query) {
        return vectorStore.similaritySearch(query);
    }
}
```

**Render Setup:**
1. Add Redis addon in Render dashboard
2. Copy Redis connection URL
3. Add environment variables: `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`

---

### 3.2 Implement Conversation Cleanup Job
**Impact:** -5-15MB over time
**Effort:** 2 hours
**Risk:** Low

**Location:** New `ConversationCleanupService.java`

**Implementation:**
```java
@Service
public class ConversationCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationCleanupService.class);

    @Autowired
    private ConversationRepository conversationRepository;

    @Scheduled(cron = "0 0 */6 * * *")  // Every 6 hours
    public void cleanupOldConversations() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        int deleted = conversationRepository.deleteByLastMessageTimeBefore(cutoff);
        logger.info("Cleaned up {} conversations older than 24 hours", deleted);
    }

    @Scheduled(cron = "0 0 2 * * *")  // Daily at 2 AM
    public void cleanupOrphanedMessages() {
        int deleted = messageRepository.deleteOrphanedMessages();
        logger.info("Cleaned up {} orphaned messages", deleted);
    }
}
```

**Add to ConversationRepository:**
```java
@Modifying
@Query("DELETE FROM Conversation c WHERE c.lastMessageTime < :cutoff")
int deleteByLastMessageTimeBefore(@Param("cutoff") LocalDateTime cutoff);
```

**Benefit:**
- Prevent conversation table growth
- Reduce database query overhead
- Better memory management

---

### 3.3 Implement Circuit Breaker for AI Calls
**Impact:** Prevents cascading failures
**Effort:** 3-4 hours
**Risk:** Low

**Location:** `AiChatbotService.java`

**Dependencies:**
```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.1.0</version>
</dependency>
```

**Configuration:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      anthropic:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
```

**Implementation:**
```java
@Service
public class AiChatbotService {

    @CircuitBreaker(name = "anthropic", fallbackMethod = "fallbackChat")
    public String generateResponse(String prompt) {
        return chatClient.prompt(prompt).call().content();
    }

    public String fallbackChat(String prompt, Exception e) {
        logger.error("AI service unavailable, using fallback", e);
        return "I'm temporarily unavailable due to high demand. Please try again in a moment. 🙏";
    }
}
```

**Benefit:**
- Graceful degradation under load
- Prevents memory exhaustion from retries
- Better user experience

---

**Phase 3 Summary:**
- **Expected Memory Reduction:** 55-115MB
- **New Baseline:** 150-200MB (optimal)
- **Time to Implement:** 9-12 hours
- **Cost:** $10/month for Redis

---

## 📊 Implementation Timeline

### Week 1: Emergency Stabilization 🚨
**Days 1-2:** Phase 1 (Quick Wins)
- Deploy all Phase 1 optimizations
- Monitor Render logs for 48 hours
- **Expected Result:** Memory usage <400MB, no crashes

**Days 3-5:** Phase 2.1-2.2 (LRU Cache + Lazy Loading)
- Implement embedding LRU cache
- Add lazy Bible data loading
- **Expected Result:** Memory usage <350MB

**Days 6-7:** Phase 2.3-2.4 (Pagination + Monitoring)
- Add pagination to large queries
- Deploy memory monitoring endpoint
- **Expected Result:** Production visibility

### Week 2-3: Optimization 🛠️
**Days 8-12:** Phase 3.1 (Redis)
- Set up Render Redis addon
- Migrate caches to Redis
- **Expected Result:** Distributed caching, <300MB

**Days 13-14:** Phase 3.2-3.3 (Cleanup + Circuit Breaker)
- Implement cleanup jobs
- Add circuit breaker
- **Expected Result:** Robust production system

---

## 🧪 Testing Strategy

### Before Each Deployment

**1. Local Load Testing:**
```bash
# Install Apache Bench
apt-get install apache2-utils

# Create test payload
cat > message.json <<EOF
{"message":"Hello","sessionId":"test123","language":"en"}
EOF

# Simulate 20 concurrent users sending messages
ab -n 100 -c 20 -T 'application/json' -p message.json \
  http://localhost:8081/api/chat/1
```

**2. Memory Profiling:**
```bash
# Start with heap dump on OOM
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/tmp/heapdump.hprof \
     -Xmx450m \
     -jar target/chatbot-0.0.1-SNAPSHOT.jar
```

**3. Monitor Metrics:**
```bash
# Check memory before/after test
curl http://localhost:8081/actuator/memory
```

### After Deployment

**1. Smoke Test (5 minutes):**
- Send 5 chat messages
- Check OAuth login
- Verify no 502 errors
- Check Render logs

**2. Load Test (30 minutes):**
```bash
# Monitor memory endpoint
watch -n 10 'curl -s https://chatbot-backend-4mp4.onrender.com/actuator/memory | jq'

# Send continuous requests
for i in {1..50}; do
  curl -X POST https://chatbot-backend-4mp4.onrender.com/api/chat/1 \
    -H "Content-Type: application/json" \
    -d '{"message":"test","sessionId":"test'$i'","language":"en"}'
  sleep 2
done
```

**3. Long-Running Test (2 hours):**
- Monitor memory growth
- Check for leaks
- Verify GC is working

---

## 🚨 Rollback Plan

### If Phase 1 Causes Issues:

**1. Immediate Rollback (Render Environment Variables):**
```bash
BIBLE_AUTO_LOAD=true
MAX_CONVERSATION_HISTORY=10
DATABASE_MAX_POOL_SIZE=10
EMBEDDING_BATCH_SIZE=100
JAVA_OPTS=  # Remove custom JVM args
```

**2. Alternative: Upgrade Render Plan**
- Render Standard: $25/month → 2GB RAM
- Quick fix if optimizations don't work

### If Phase 2/3 Causes Issues:

**1. Git Revert:**
```bash
git log --oneline  # Find commit hash
git revert <commit-hash>
git push origin main
```

**2. Disable Feature Flags:**
```bash
REDIS_ENABLED=false
CLEANUP_JOB_ENABLED=false
```

---

## ✅ Success Criteria

### Phase 1 Success:
- ✅ No OutOfMemoryError for 48 hours
- ✅ Memory usage <400MB steady state
- ✅ Chat responses return 200 OK (not 502)
- ✅ Response times <5 seconds

### Phase 2 Success:
- ✅ Memory usage <350MB steady state
- ✅ No 502 errors under normal load (5 concurrent users)
- ✅ Response times <3 seconds P95
- ✅ Cache hit rate >50%

### Phase 3 Success:
- ✅ Memory usage <300MB steady state
- ✅ Handles 20 concurrent users without issues
- ✅ Graceful degradation under extreme load
- ✅ Zero downtime deployments

---

## 📝 Monitoring & Alerts

### Key Metrics Dashboard

**Memory Metrics:**
```bash
# Add to monitoring dashboard
curl https://chatbot-backend-4mp4.onrender.com/actuator/memory

# Alert thresholds:
- Warning: >400MB (85%)
- Critical: >450MB (95%)
- Emergency: >480MB (98%)
```

**Application Metrics:**
```bash
curl https://chatbot-backend-4mp4.onrender.com/actuator/health
curl https://chatbot-backend-4mp4.onrender.com/actuator/metrics/jvm.memory.used
```

### Render Log Monitoring

**Search for these patterns:**
```bash
# Out of memory errors
render logs --tail | grep "OutOfMemoryError"

# GC activity
render logs --tail | grep "GC"

# Memory warnings
render logs --tail | grep "memory"
```

---

## 💰 Cost Analysis

### Current (Free Tier):
- **Render:** $0/month
- **Memory:** 512MB
- **Status:** Crashing frequently ❌

### Option A: Optimize Code (Recommended):
- **Render:** $0/month
- **Memory:** <400MB after Phase 1
- **Time Investment:** 2-3 days
- **Long-term:** Scalable, maintainable ✅

### Option B: Upgrade Render:
- **Render Standard:** $25/month
- **Memory:** 2GB
- **Time Investment:** 5 minutes
- **Long-term:** Band-aid, doesn't fix root cause ⚠️

### Option C: Hybrid Approach:
- **Phase 1 optimizations:** Free
- **Render Starter:** $7/month (512MB, but guaranteed)
- **Redis addon:** $10/month
- **Total:** $17/month
- **Benefit:** Stable + optimized ✅✅

---

## 🎯 Recommended Action Plan

### Today (Saturday):
1. ✅ **Deploy Phase 1** (30 minutes)
   - Add all environment variables
   - Deploy and monitor for 2 hours

2. ✅ **Verify Stability** (2 hours)
   - Test chat endpoint
   - Monitor memory via logs
   - Confirm no 502 errors

### Tomorrow (Sunday):
3. ✅ **Implement Phase 2.1** (3 hours)
   - Add Guava dependency
   - Implement LRU cache
   - Deploy and test

### Next Week:
4. ✅ **Phases 2.2-2.4** (1 hour per day)
   - One optimization per day
   - Monitor each change

5. ✅ **Phase 3** (Based on need)
   - Only if traffic grows
   - Or if Phase 1+2 not sufficient

---

## 📚 Resources

### Documentation:
- [Spring Boot Memory](https://docs.spring.io/spring-boot/docs/current/reference/html/deployment.html)
- [JVM Memory Management](https://docs.oracle.com/en/java/javase/17/gctuning/)
- [Render Scaling](https://render.com/docs/scaling)
- [Guava Cache](https://github.com/google/guava/wiki/CachesExplained)

### Tools:
- **VisualVM** - Free JVM profiler
- **Eclipse MAT** - Heap dump analyzer
- **JProfiler** - Commercial profiler

---

## 📞 Support & Next Steps

### If Issues Persist:
1. Check this document's rollback plan
2. Review Render logs for specific errors
3. Contact Render support if infrastructure issue
4. Create GitHub issue with logs + memory stats

### Future Optimizations:
- **Phase 4:** Separate worker service for background jobs
- **Phase 5:** Implement read replicas for database
- **Phase 6:** Migrate to microservices architecture

---

**Document Version:** 1.0
**Created:** January 11, 2026
**Author:** Claude Sonnet 4.5
**Status:** Ready for Implementation
**Priority:** 🔴 CRITICAL
