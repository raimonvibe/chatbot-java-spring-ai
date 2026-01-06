# Implementation Plan: "What Jesus Would Say" Feature
## Using New Testament Embeddings for Christlike Responses

---

## 📋 EXECUTIVE SUMMARY

**Goal**: Leverage the 7,953 New Testament verse embeddings to provide chatbot responses that reflect what Jesus might say about the visitor's website, business, or questions.

**Approach**: Use semantic similarity to find Jesus's teachings (Gospels: Matthew, Mark, Luke, John) that relate to the website's values and user's questions, then generate AI responses inspired by those teachings.

**Value Proposition**:
- Unique spiritual perspective on any business/website
- Connects secular topics to Jesus's wisdom
- Differentiates the chatbot with authentic Christian insights
- Educational - helps visitors see their work through Jesus's eyes

---

## 🎯 FEATURE OVERVIEW

### What It Does

**For any website/business**, the chatbot can provide:

1. **Jesus's Perspective on the Business**
   - Example for Raimonvibe: "Jesus taught about letting our light shine (Matthew 5:16). Your mission of spreading positive vibes aligns with His call to be salt and light in the world."

2. **Jesus's Wisdom on User Questions**
   - User: "How should I price my services?"
   - Response: "Jesus taught about honest measures and treating others as you'd want to be treated (Matthew 7:12). Consider pricing that reflects fair value while serving your customers with integrity."

3. **Dynamic Teaching Integration**
   - Automatically finds relevant teachings from Jesus
   - Explains how they apply to the specific context
   - Natural, conversational tone (not preachy)

---

## 🔍 CURRENT STATE ANALYSIS

### What We Have

✅ **Bible Verse Embeddings Loaded**
- 7,953 verses from New Testament
- 1024-dimensional embeddings (Cohere embed-multilingual-v3.0)
- Stored in PostgreSQL database
- Books include: Matthew, Mark, Luke, John, Acts, Romans, etc.

✅ **Existing Infrastructure**
- `BibleVerseRepository.findVersesWithEmbeddings()` - Gets all verses
- `AiChatbotService.cosineSimilarity()` - Calculates relevance
- `AiChatbotService.findRelevantBibleVerse()` - Semantic search
- Website content embeddings and RAG system

✅ **Current Bible Verse Feature**
- Shows 1 verse per response (if relevant)
- Smart threshold logic (0.5-0.7 similarity)
- Works for first message + subsequent messages

### What We Need to Add

❌ **Filter for Jesus's Direct Teachings**
- Focus on Gospels: Matthew, Mark, Luke, John
- Identify verses where Jesus is speaking
- Create "Jesus Teachings" subset

❌ **"What Jesus Would Say" Response Mode**
- New chatbot setting: `jesusTeachingsEnabled` (boolean)
- Generate responses inspired by Jesus's teachings
- Different from just citing a verse - this interprets and applies

❌ **Teaching Context Builder**
- Combine multiple related teachings
- Provide interpretation/application
- Connect to website's specific context

---

## 🏗️ IMPLEMENTATION PLAN

### Phase 1: Database & Model Updates (30 minutes)

#### 1.1 Identify Jesus's Direct Teachings

**Goal**: Filter New Testament verses to only Jesus's words

**Options:**

**Option A: Manual Tagging (Recommended for MVP)**
- Add `speaker` column to `BibleVerse` table
- Values: "Jesus", "Apostle", "Narrator", "Other"
- Manually tag Gospel verses (or use pre-tagged dataset)
- Query: `SELECT * FROM bible_verses WHERE speaker = 'Jesus'`

**Option B: Pattern Matching**
- Use verse references to identify Jesus's words
- Gospels typically have red-letter editions marking Jesus's words
- Import red-letter verse list from public domain source

**Option C: Semantic Clustering**
- Use embeddings to cluster verses by "speaker style"
- Less accurate, but automated

**Recommendation**: Option A with a JSON mapping file
- Create `data/jesus_verses.json` with list of references
- Import on startup to tag verses
- ~1000-1500 verses are Jesus's direct words

#### 1.2 Add Chatbot Setting

**File**: `backend/src/main/java/com/prayer_chat/chatbot/model/Chatbot.java`

```java
@Column(name = "jesus_teachings_enabled")
private Boolean jesusTeachingsEnabled = false;
```

**Migration SQL**:
```sql
ALTER TABLE chatbots
ADD COLUMN jesus_teachings_enabled BOOLEAN DEFAULT FALSE;
```

### Phase 2: Jesus Teachings Service (1 hour)

#### 2.1 Create New Service Class

**File**: `backend/src/main/java/com/prayer_chat/chatbot/service/JesusTeachingsService.java`

**Key Methods**:

```java
@Service
public class JesusTeachingsService {

    /**
     * Find Jesus's teachings relevant to a topic
     *
     * @param topic User's question or website theme
     * @param maxTeachings Number of teachings to return (default 3)
     * @return List of relevant teachings with similarity scores
     */
    public List<JesusTeaching> findRelevantTeachings(String topic, int maxTeachings) {
        // 1. Generate embedding for topic
        // 2. Search verses WHERE speaker = 'Jesus'
        // 3. Calculate cosine similarity
        // 4. Return top N teachings
    }

    /**
     * Build "What Jesus Would Say" context for AI
     *
     * @param websiteContent Website description/content
     * @param userQuestion User's question
     * @return Formatted teaching context for system prompt
     */
    public String buildJesusTeachingContext(String websiteContent, String userQuestion) {
        // 1. Find 2-3 relevant teachings
        // 2. Format them with references
        // 3. Add interpretation guidance
        // 4. Return as prompt addition
    }

    /**
     * Get thematic teachings (for first message)
     *
     * @param websiteTheme Main theme of website (e.g., "business", "health", "education")
     * @return Top 3 teachings related to theme
     */
    public List<JesusTeaching> getThematicTeachings(String websiteTheme) {
        // Pre-defined mappings + semantic search
    }
}
```

**DTO Class**:

```java
public class JesusTeaching {
    private String reference;      // "Matthew 5:16"
    private String text;           // Verse text
    private double similarity;     // 0.0 to 1.0
    private String context;        // Context of the teaching
    private String application;    // How it applies (AI-generated)
}
```

### Phase 3: Integration with Chatbot (1 hour)

#### 3.1 Update AiChatbotService

**File**: `backend/src/main/java/com/prayer_chat/chatbot/service/AiChatbotService.java`

**Changes to `generateResponse()` method**:

```java
private String generateResponse(Chatbot chatbot, Conversation conversation,
                                String userMessage, String userLanguage) {

    // Existing code...
    List<Document> relevantDocs = retrieveRelevantContext(chatbot, userMessage);
    boolean isFirstMessage = recentMessages.isEmpty();

    // Existing: Find single Bible verse
    BibleVerse relevantVerse = null;
    if (chatbot.getChristianMessagingEnabled()) {
        relevantVerse = findRelevantBibleVerse(userMessage, relevantDocs, isFirstMessage);
    }

    // NEW: Find Jesus's teachings if enabled
    String jesusTeachingContext = null;
    if (chatbot.getJesusTeachingsEnabled() != null && chatbot.getJesusTeachingsEnabled()) {
        String websiteContent = buildWebsiteContext(relevantDocs);
        jesusTeachingContext = jesusTeachingsService.buildJesusTeachingContext(
            websiteContent,
            userMessage
        );
    }

    // Updated: Pass Jesus teachings to prompt builder
    String systemPrompt = buildSystemPrompt(
        chatbot,
        relevantDocs,
        userLanguage,
        relevantVerse,
        jesusTeachingContext,  // NEW
        isFirstMessage
    );

    // Rest of method...
}
```

#### 3.2 Update System Prompt Builder

**Add Jesus Teachings Section**:

```java
private String buildSystemPrompt(Chatbot chatbot, List<Document> relevantDocs,
                                 String userLanguage, BibleVerse relevantVerse,
                                 String jesusTeachingContext, boolean isFirstMessage) {

    StringBuilder prompt = new StringBuilder();

    // Existing base prompt...

    // NEW: Add Jesus's teachings section
    if (jesusTeachingContext != null) {
        prompt.append("\n").append("=".repeat(50)).append("\n");
        prompt.append("📖 WHAT JESUS WOULD SAY:\n");
        prompt.append(jesusTeachingContext).append("\n");
        prompt.append("=".repeat(50)).append("\n");

        prompt.append("\nInstructions for using Jesus's teachings:\n");
        prompt.append("- Draw inspiration from the teachings above\n");
        prompt.append("- Explain how Jesus's wisdom applies to this situation\n");
        prompt.append("- Use conversational language (not preachy)\n");
        prompt.append("- Connect the teachings to the specific question or context\n");
        prompt.append("- Be authentic and respectful\n");

        if (isFirstMessage) {
            prompt.append("- For the first message, include a brief 'Jesus's Perspective' on this business/website\n");
        } else {
            prompt.append("- Only include Jesus's perspective if it naturally fits the question\n");
        }
    }

    // Existing code...

    return prompt.toString();
}
```

### Phase 4: API Endpoints (30 minutes)

#### 4.1 Preview Jesus's Teachings

**Endpoint**: `POST /api/chatbots/{id}/preview-jesus-teachings`

**Purpose**: Let users see what teachings would be relevant to their website

**Response**:
```json
{
  "websiteUrl": "https://raimonvibe.com",
  "topTeachings": [
    {
      "reference": "Matthew 5:16",
      "text": "Let your light shine before others...",
      "similarity": 0.82,
      "application": "This teaching relates to your mission of spreading positive energy and inspiration."
    },
    {
      "reference": "Matthew 22:39",
      "text": "Love your neighbor as yourself...",
      "similarity": 0.76,
      "application": "Your focus on community and connection reflects this core teaching of Jesus."
    }
  ],
  "totalJesusVerses": 1247
}
```

#### 4.2 Toggle Feature

**Endpoint**: `PATCH /api/chatbots/{id}/settings`

**Body**:
```json
{
  "jesusTeachingsEnabled": true
}
```

### Phase 5: Frontend Integration (1 hour)

#### 5.1 Settings Toggle

**Location**: Dashboard → Chatbot Settings → Christian Messaging

**UI Element**:
```
☑️ Enable Christian Messaging
   └─ ☑️ Show Bible Verses
   └─ ☑️ Include "What Jesus Would Say" (NEW)
        └─ [Preview Teachings] button
```

#### 5.2 Preview Modal

**Shows**:
- Top 3-5 teachings relevant to the website
- Similarity scores
- Example of how it would appear in responses

---

## 📊 EXPECTED RESULTS

### Example Conversation (Raimonvibe.com)

#### First Message:

**User**: "Tell me about Raimonvibe"

**Bot Response**:

```
Welcome! God's blessings to you!

Raimonvibe is a platform dedicated to spreading positivity,
inspiration, and personal growth through faith-based content
and community engagement.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📖 JESUS'S PERSPECTIVE:

Jesus taught, "You are the light of the world. A city on a
hill cannot be hidden... let your light shine before others,
that they may see your good deeds and glorify your Father in
heaven." (Matthew 5:14-16)

Your mission to spread positive vibrations and inspiration
beautifully reflects this teaching. Just as Jesus called His
followers to be light in darkness, Raimonvibe illuminates
paths toward hope, growth, and faith.

Jesus also said, "Love your neighbor as yourself"
(Matthew 22:39). Your focus on community building and
uplifting others embodies this fundamental principle.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

How can I help you today?

God bless you!
```

#### Subsequent Message (Relevant Question):

**User**: "How should I handle criticism of my work?"

**Bot Response**:

```
Great question. Handling criticism gracefully is important
for any mission-driven work.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📖 JESUS'S WISDOM ON THIS:

Jesus faced tremendous criticism during His ministry, yet He
taught, "Blessed are you when people insult you, persecute you
and falsely say all kinds of evil against you because of me.
Rejoice and be glad..." (Matthew 5:11-12)

He also said, "Love your enemies and pray for those who
persecute you" (Matthew 5:44).

Applied to your situation: Not everyone will understand or
appreciate your message of faith and positivity. Some may
criticize. Jesus's approach was to:

1. Stay true to His mission regardless of opposition
2. Respond with love, not defensiveness
3. Pray for critics rather than resent them
4. Find joy in doing what's right, even when misunderstood

Continue your work with confidence, respond to critics with
grace, and let your positive impact speak for itself.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

May you be blessed!
```

#### Subsequent Message (Less Relevant Question):

**User**: "What are your pricing plans?"

**Bot Response**:

```
Here are Raimonvibe's current offerings:

[Pricing details from website content]

If you have questions about which plan is right for you,
feel free to ask!

God bless you!
```

*(Note: No Jesus teaching included because pricing isn't
spiritually relevant - smart threshold prevents forcing it)*

---

## 🔧 TECHNICAL ARCHITECTURE

### Data Flow Diagram

```
User Question
    ↓
Website Content (from Vector Store)
    ↓
Combine: Question + Website Context
    ↓
Generate Embedding (Cohere)
    ↓
Search Jesus's Verses (WHERE speaker='Jesus')
    ↓
Calculate Cosine Similarity
    ↓
Filter by Threshold (0.6+ for relevance)
    ↓
Select Top 2-3 Teachings
    ↓
Build "Jesus Teaching Context"
    ↓
Add to System Prompt
    ↓
AI Generates Response (Claude)
    ↓
Response includes Jesus's perspective + application
```

### Database Schema Updates

```sql
-- Add speaker column to identify Jesus's words
ALTER TABLE bible_verses
ADD COLUMN speaker VARCHAR(50) DEFAULT 'Unknown';

-- Index for performance
CREATE INDEX idx_bible_verses_speaker ON bible_verses(speaker);

-- Sample data
UPDATE bible_verses
SET speaker = 'Jesus'
WHERE reference IN (
    'Matthew 5:3', 'Matthew 5:4', ... -- List of Jesus's verses
);

-- Add feature toggle to chatbots
ALTER TABLE chatbots
ADD COLUMN jesus_teachings_enabled BOOLEAN DEFAULT FALSE;
```

---

## 📈 PERFORMANCE CONSIDERATIONS

### Optimization Strategies

1. **Caching**
   - Cache Jesus verse embeddings separately in memory
   - Pre-compute common theme mappings
   - Cache website theme analysis

2. **Query Optimization**
   - Add database index on `speaker` column
   - Limit search to ~1200 Jesus verses instead of all 7,953
   - Use parallel processing for similarity calculations

3. **Smart Loading**
   - Load Jesus verses into memory on startup
   - Async teaching search (don't block response)
   - Fallback to regular verse if teaching search fails

### Expected Performance

- **First Load**: ~1 second (load Jesus verses)
- **Per Message**: +200-300ms (teaching search)
- **Memory**: +50MB (cache Jesus verses)

---

## 🧪 TESTING STRATEGY

### Unit Tests

```java
@Test
public void testFindRelevantJesusTeachings() {
    String topic = "How to handle money honestly";
    List<JesusTeaching> teachings = jesusTeachingsService
        .findRelevantTeachings(topic, 3);

    assertTrue(teachings.size() <= 3);
    assertTrue(teachings.get(0).getReference().contains("Matthew"));
    assertTrue(teachings.get(0).getSimilarity() >= 0.5);
}

@Test
public void testJesusTeachingContextBuilder() {
    String context = jesusTeachingsService.buildJesusTeachingContext(
        "Business consulting website",
        "How should I treat my employees?"
    );

    assertTrue(context.contains("Jesus"));
    assertTrue(context.contains("Matthew") || context.contains("Luke"));
}
```

### Integration Tests

1. **Test with Various Website Types**:
   - E-commerce site → Teachings about honesty, fairness
   - Healthcare → Teachings about healing, compassion
   - Education → Teachings about wisdom, learning
   - Ministry → Direct Gospel teachings

2. **Test Question Relevance**:
   - Spiritual questions → High relevance
   - Business questions → Moderate relevance
   - Technical questions → Low/no relevance

3. **Test Edge Cases**:
   - No website content available
   - Very short user questions
   - Non-English content
   - Disabled feature

---

## 🚀 DEPLOYMENT PLAN

### Step 1: Database Migration
```bash
# Run migration to add columns
./mvnw flyway:migrate

# Load Jesus verse mapping
curl -X POST http://localhost:8081/api/admin/bible/tag-jesus-verses
```

### Step 2: Backend Deployment
```bash
# Build and test
./mvnw clean test
./mvnw package

# Deploy to Render
git push origin main
```

### Step 3: Feature Rollout

**Phase A: Beta Test (Invite Only)**
- Enable for 5-10 test chatbots
- Gather feedback
- Tune thresholds and prompts

**Phase B: Opt-In (All Users)**
- Add toggle in settings
- Default: OFF
- Users can enable if desired

**Phase C: Recommended Default**
- Default: ON for new chatbots
- Show preview during onboarding
- Users can disable if preferred

---

## 📋 ACCEPTANCE CRITERIA

### Must Have ✅

- [ ] Can filter verses to Jesus's direct teachings only
- [ ] Semantic search finds relevant teachings (similarity ≥ 0.6)
- [ ] First message includes Jesus's perspective on website
- [ ] Subsequent messages only include teachings if relevant
- [ ] Maximum 2-3 teachings per response
- [ ] AI explains how teachings apply to context
- [ ] Works for any website type (business, ministry, personal, etc.)
- [ ] Feature can be enabled/disabled per chatbot
- [ ] Performance: Response time < 2 seconds

### Nice to Have 🎁

- [ ] Preview teachings before enabling feature
- [ ] Teaching categories (love, wisdom, business, healing, etc.)
- [ ] Analytics: Track which teachings resonate most
- [ ] Multi-verse thematic groupings (Sermon on the Mount, Parables, etc.)
- [ ] User feedback: "Was this teaching helpful?"
- [ ] Export: Download all relevant teachings for a website

---

## 🎯 SUCCESS METRICS

### Technical Metrics

- **Accuracy**: 80%+ of teachings rated as "relevant" by users
- **Performance**: <2 second response time
- **Coverage**: Teachings appear in 60-80% of first messages
- **Coverage**: Teachings appear in 30-50% of subsequent messages
- **Error Rate**: <1% teaching search failures

### Business Metrics

- **Adoption**: 40%+ of chatbots enable feature
- **Engagement**: 15%+ increase in conversation length
- **Satisfaction**: 4.5+ star rating for teaching quality
- **Differentiation**: "Jesus's Perspective" cited as unique value in 50%+ of feedback

---

## ⚠️ RISKS & MITIGATION

### Risk 1: Theological Accuracy

**Risk**: AI might misinterpret or misapply Jesus's teachings

**Mitigation**:
- Review and curate teaching applications
- Add disclaimer: "AI-generated interpretation"
- Provide feedback mechanism
- Consider theological review board

### Risk 2: Performance Impact

**Risk**: Searching 1200+ verses per message could slow responses

**Mitigation**:
- Cache Jesus verses in memory
- Async search (don't block response)
- Pre-compute theme mappings
- Fallback to regular verse if timeout

### Risk 3: User Confusion

**Risk**: Users might think Jesus literally wrote the response

**Mitigation**:
- Clear labeling: "Jesus's Teachings Applied"
- Include actual Bible references
- Disclaimer in UI
- Educational content about the feature

### Risk 4: Inappropriate Context

**Risk**: Jesus teachings shown for inappropriate questions

**Mitigation**:
- Content filtering
- Smart relevance threshold (≥0.6)
- Blacklist certain topics
- Admin review queue

---

## 📚 RESOURCES NEEDED

### Data Sources

1. **Jesus Verse Mapping**
   - Use public domain red-letter Bible data
   - Or manually tag Gospel verses
   - Sources: Open Bible API, Bible Gateway, ESV API

2. **Thematic Categories**
   - Pre-define teaching themes
   - Map common website topics to themes

### Team Resources

- **Backend Developer**: 4-6 hours implementation
- **Frontend Developer**: 2-3 hours UI integration
- **QA Tester**: 2 hours testing
- **Theological Reviewer** (optional): 3-4 hours review

### Third-Party Services

- Existing: Cohere API (embeddings)
- Existing: Anthropic Claude API (AI responses)
- No new costs

---

## 🗓️ TIMELINE ESTIMATE

### Sprint 1 (Week 1)
- **Day 1-2**: Tag Jesus verses in database (speaker column)
- **Day 3-4**: Build JesusTeachingsService
- **Day 5**: Integration with AiChatbotService

### Sprint 2 (Week 2)
- **Day 1-2**: API endpoints + testing
- **Day 3-4**: Frontend UI + preview modal
- **Day 5**: Beta testing + feedback

### Sprint 3 (Week 3)
- **Day 1-2**: Refinement based on feedback
- **Day 3**: Performance optimization
- **Day 4**: Documentation + training
- **Day 5**: Public release

**Total Time**: ~3 weeks for full implementation and testing

---

## 🎓 NEXT SESSION CHECKLIST

### Before Starting Implementation

- [ ] Review this plan
- [ ] Decide on Jesus verse tagging approach (manual vs. automated)
- [ ] Determine similarity threshold (0.6 recommended)
- [ ] Choose UI placement for feature toggle
- [ ] Set up test chatbot for development

### Session 1 Goals

1. Add `speaker` column to `bible_verses` table
2. Import Jesus verse mapping (or create tagging script)
3. Create `JesusTeachingsService` class
4. Implement `findRelevantTeachings()` method
5. Write unit tests

### Session 2 Goals

1. Integrate with `AiChatbotService`
2. Update system prompt builder
3. Add chatbot setting `jesusTeachingsEnabled`
4. Test end-to-end with sample website
5. Commit and deploy

---

## 📝 NOTES & CONSIDERATIONS

### Theological Considerations

- Focus on direct quotes from Jesus (red letters)
- Avoid controversial interpretations
- Stick to widely accepted Gospel teachings
- Provide references for verification
- Consider multiple translations (ESV, NIV, NKJV)

### User Experience

- Don't overwhelm with too many teachings
- Make it optional (not forced)
- Allow users to see preview before enabling
- Provide examples in onboarding
- Clear value proposition

### Technical Debt

- Consider moving Bible verse logic to separate microservice
- Plan for eventual PostgreSQL pgvector extension (faster similarity search)
- Cache strategy for high-traffic chatbots
- API rate limiting for embedding generation

---

## ✅ CONCLUSION

This feature will differentiate the chatbot platform by providing authentic, contextual Christian wisdom inspired by Jesus's teachings. It goes beyond simply citing verses - it interprets and applies timeless principles to modern business and life situations.

**Key Differentiators**:
- First chatbot to use Jesus's teachings dynamically
- Context-aware spiritual insights
- Works for any website type
- Respectful, non-preachy tone
- Backed by semantic AI technology

**Next Steps**: Review plan → Approve → Schedule implementation sessions

---

**Plan Created**: 2026-01-06
**Estimated Effort**: 15-20 hours development
**Dependencies**: Bible embeddings (✅ complete), Vector search (✅ complete)
**Priority**: High (unique feature, leverages existing embeddings)

---

*Ready to build something amazing! 🚀*
