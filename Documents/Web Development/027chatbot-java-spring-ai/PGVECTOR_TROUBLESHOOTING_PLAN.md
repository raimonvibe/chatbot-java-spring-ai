# PgVector Integration Troubleshooting Plan

## Problem Summary

PgVectorStore bean is not being created despite:
- ✅ pgvector extension enabled (v0.8.1)
- ✅ AiConfiguration class loaded
- ✅ Dependencies in pom.xml
- ❌ @PostConstruct never called
- ❌ embeddingModel() bean never created
- ❌ vectorStore() bean never created
- ❌ vector_store table never created

## Root Cause Hypothesis

Spring is **partially initializing** AiConfiguration but not fully processing it:
- Constructor is called ✅
- Some @Bean methods work (embeddingImportRunner) ✅
- @PostConstruct method NOT called ❌
- Most @Bean methods NOT called ❌

**Likely causes**:
1. Circular dependency preventing full initialization
2. Exception thrown during bean creation (silently caught)
3. Profile or conditional annotation conflict
4. Spring AI auto-configuration conflict despite disabling it

---

## Phase 1: Diagnose Bean Creation Failure (15 min)

### Step 1.1: Check for Circular Dependencies

**Action**: Add dependency logging to AiConfiguration constructor

```java
public AiConfiguration() {
    System.out.println("=".repeat(60));
    System.out.println("🔧 AiConfiguration constructor called");
    System.out.println("🔧 Thread: " + Thread.currentThread().getName());
    System.out.println("🔧 ClassLoader: " + this.getClass().getClassLoader());
    System.out.println("=".repeat(60));
}
```

**Expected**: If circular dependency exists, you'll see multiple constructor calls or hanging

### Step 1.2: Add Exception Handling to @Bean Methods

**Action**: Wrap each @Bean method in try-catch to see if exceptions are being swallowed

```java
@Bean
@Primary
public EmbeddingModel embeddingModel() {
    try {
        logger.info("🔧 ========================================");
        logger.info("🔧 STARTING embeddingModel() @Bean method");
        logger.info("🔧 ========================================");

        logger.info("🔧 Cohere API Key: {}", cohereApiKey != null ? "Present" : "NULL");
        logger.info("🔧 Embedding Model: {}", embeddingModel);

        CohereEmbeddingModel model = new CohereEmbeddingModel(cohereApiKey, embeddingModel);

        logger.info("✅ CohereEmbeddingModel created successfully!");
        return model;
    } catch (Exception e) {
        logger.error("❌ FAILED to create EmbeddingModel", e);
        throw new RuntimeException("EmbeddingModel creation failed", e);
    }
}
```

### Step 1.3: Check Bean Registration in Context

**Action**: Create a @Component that prints all beans after context loads

```java
@Component
public class BeanDebugger implements ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(BeanDebugger.class);

    @Override
    public void setApplicationContext(ApplicationContext context) {
        logger.info("🔍 === BEAN DEBUGGER START ===");

        // Check if our beans exist
        try {
            EmbeddingModel em = context.getBean(EmbeddingModel.class);
            logger.info("✅ EmbeddingModel bean found: {}", em.getClass().getName());
        } catch (NoSuchBeanDefinitionException e) {
            logger.error("❌ EmbeddingModel bean NOT FOUND");
        }

        try {
            VectorStore vs = context.getBean(VectorStore.class);
            logger.info("✅ VectorStore bean found: {}", vs.getClass().getName());
        } catch (NoSuchBeanDefinitionException e) {
            logger.error("❌ VectorStore bean NOT FOUND");
        }

        // List all AI-related beans
        String[] beanNames = context.getBeanDefinitionNames();
        logger.info("🔍 Searching {} beans for AI-related beans...", beanNames.length);
        for (String name : beanNames) {
            if (name.toLowerCase().contains("vector") ||
                name.toLowerCase().contains("embedding") ||
                name.toLowerCase().contains("cohere")) {
                logger.info("🔍 Found bean: {}", name);
            }
        }

        logger.info("🔍 === BEAN DEBUGGER END ===");
    }
}
```

---

## Phase 2: Alternative Approaches (30 min)

If beans still aren't created, try these alternatives:

### Option A: Manual Instantiation via @PostConstruct

Remove @Bean annotations and manually create/register beans:

```java
@Configuration
@Profile("!test")
public class AiConfiguration {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private VectorStore vectorStore;
    private EmbeddingModel embeddingModel;

    @PostConstruct
    public void initializeBeans() {
        logger.info("🔧 Manual bean initialization starting...");

        // Create embedding model
        this.embeddingModel = new CohereEmbeddingModel(cohereApiKey, embeddingModel);
        logger.info("✅ Created EmbeddingModel manually");

        // Create vector store
        this.vectorStore = new PgVectorStore.Builder(jdbcTemplate, embeddingModel)
            .initializeSchema(true)
            .build();
        logger.info("✅ Created PgVectorStore manually");

        // Register as beans (if needed)
        DefaultListableBeanFactory beanFactory =
            (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        beanFactory.registerSingleton("embeddingModel", this.embeddingModel);
        beanFactory.registerSingleton("vectorStore", this.vectorStore);

        logger.info("✅ Manual beans registered in Spring context");
    }

    // Provide getters for services to use
    @Bean
    public EmbeddingModel embeddingModel() {
        return this.embeddingModel;
    }

    @Bean
    public VectorStore vectorStore() {
        return this.vectorStore;
    }
}
```

### Option B: Separate Configuration Class

Create a new dedicated configuration class just for VectorStore:

```java
@Configuration
@Profile("!test")
@ConditionalOnBean(JdbcTemplate.class)
public class VectorStoreConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(VectorStoreConfiguration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.ai.cohere.api-key}")
    private String cohereApiKey;

    @Bean
    @Primary
    public EmbeddingModel cohereEmbeddingModel() {
        logger.info("🔧 Creating Cohere EmbeddingModel");
        return new CohereEmbeddingModel(cohereApiKey, "embed-multilingual-v3.0");
    }

    @Bean
    @Primary
    public VectorStore pgVectorStore(EmbeddingModel embeddingModel) {
        logger.info("🔧 Creating PgVectorStore");
        return new PgVectorStore.Builder(jdbcTemplate, embeddingModel)
            .initializeSchema(true)
            .build();
    }
}
```

### Option C: Remove spring-ai-pgvector-store-spring-boot-starter

The starter might be interfering. Use just the core library:

```xml
<!-- Remove this -->
<!--
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pgvector-store-spring-boot-starter</artifactId>
</dependency>
-->

<!-- Add this instead -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pgvector-store</artifactId>
</dependency>
```

### Option D: Simplest Fallback - Use SimpleVectorStore with Persistence

As a temporary workaround while debugging:

```java
@Bean
@Primary
public VectorStore vectorStore(EmbeddingModel embeddingModel) {
    logger.info("🔧 Creating SimpleVectorStore with file persistence");

    File vectorStoreFile = new File("/app/data/vector-store.json");
    vectorStoreFile.getParentFile().mkdirs();

    SimpleVectorStore vectorStore = new SimpleVectorStore(embeddingModel);

    // Load existing data if file exists
    if (vectorStoreFile.exists()) {
        vectorStore.load(vectorStoreFile);
        logger.info("✅ Loaded {} vectors from file", vectorStore.similaritySearch("test").size());
    }

    // Save periodically (you'd need to implement this)
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        vectorStore.save(vectorStoreFile);
        logger.info("✅ Saved vectors to file");
    }));

    return vectorStore;
}
```

---

## Phase 3: Verify Database Schema (5 min)

Even if beans work, ensure schema is created:

### Manual Table Creation (Fallback)

If PgVectorStore doesn't auto-create table, do it manually:

```sql
-- Run in psql:
CREATE TABLE IF NOT EXISTS vector_store (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    content TEXT,
    metadata JSONB,
    embedding vector(1024)
);

-- Create index for fast similarity search
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
ON vector_store USING hnsw (embedding vector_cosine_ops);
```

---

## Phase 4: Test and Verify (10 min)

### Test 1: Check Bean Availability

```bash
curl http://localhost:8081/actuator/beans | jq '.contexts.application.beans |
  with_entries(select(.key | contains("vector") or contains("embedding")))'
```

### Test 2: Verify Table Creation

```sql
\dt vector_store
\d vector_store
SELECT COUNT(*) FROM vector_store;
```

### Test 3: Manual Vector Insert

```sql
INSERT INTO vector_store (content, metadata, embedding)
VALUES (
    'Test content',
    '{"test": true}'::jsonb,
    array_fill(0.1::float, ARRAY[1024])::vector
);

SELECT COUNT(*) FROM vector_store;
```

### Test 4: Trigger Website Indexing

1. Create a chatbot
2. Analyze a website
3. Check logs for "Starting content indexing for chatbot"
4. Check database: `SELECT COUNT(*) FROM vector_store;`

---

## Success Criteria

✅ Logs show: "🔧 Creating Cohere EmbeddingModel"
✅ Logs show: "🔧 Creating MANUAL PgVectorStore bean"
✅ Logs show: "✅ PgVectorStore created successfully!"
✅ Database has `vector_store` table
✅ Website content gets indexed (5 rows in vector_store)
✅ Chatbot gives accurate answers about website content

---

## Debugging Commands Quick Reference

```powershell
# Connect to database
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" "postgresql://ai_chatbot_db_z4h0_user:wsaMAZiTWb8Zyb8JFJrMtCXgdAXyg0dg@dpg-d50ioun5r7bs739fm860-a.oregon-postgres.render.com/ai_chatbot_db_z4h0?sslmode=require"

# Check tables
\dt

# Check vector_store specifically
\dt vector_store
\d vector_store
SELECT COUNT(*) FROM vector_store;

# Check website content
SELECT COUNT(*) FROM website_content;
SELECT url, is_indexed FROM website_content LIMIT 5;

# Exit
\q
```

---

## Timeline Estimate

- **Phase 1** (Diagnosis): 15 min
- **Phase 2** (Try alternatives): 30 min
- **Phase 3** (Database schema): 5 min
- **Phase 4** (Testing): 10 min

**Total**: ~1 hour

---

## Next Session Checklist

- [ ] Review logs carefully for any exceptions
- [ ] Add BeanDebugger component
- [ ] Try Option A (Manual @PostConstruct instantiation) first
- [ ] If fails, try Option B (Separate config class)
- [ ] If fails, try Option C (Remove starter, use core lib)
- [ ] Verify table creation
- [ ] Test with real chatbot
- [ ] Document solution for future reference

---

**Status**: Ready for next session
**Priority**: High - Core functionality broken
**Impact**: Chatbot gives generic answers instead of website-specific content
