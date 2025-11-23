# 🔄 Migration Guide: OpenAI → Anthropic Claude + Cohere

This guide explains the migration from OpenAI to Anthropic Claude 3 Haiku for chat functionality and Cohere for embeddings.

## 📋 What Changed

### Before (OpenAI)
- **Chat Model**: GPT-3.5-turbo
- **Embeddings**: text-embedding-ada-002 (1536 dimensions)
- **Provider**: OpenAI for both

### After (Claude + Cohere)
- **Chat Model**: Claude 3 Haiku (Anthropic)
- **Embeddings**: embed-multilingual-v3.0 (Cohere, 1024 dimensions)
- **Providers**: Claude for chat, Cohere for multilingual embeddings

## 🎯 Why Claude 3 Haiku?

- **Cost Effective**: More affordable than GPT-3.5-turbo
- **Fast Response**: Optimized for quick interactions
- **High Quality**: Excellent reasoning and conversational abilities
- **Long Context**: 200K token context window
- **Safety**: Built-in constitutional AI safety features

## 🚀 Migration Steps

### 1. Get Anthropic API Key

1. Visit [Anthropic Console](https://console.anthropic.com/)
2. Sign up or log in
3. Navigate to API Keys section
4. Create a new API key
5. Copy your key

### 2. Update Environment Variables

```bash
# Add Anthropic API key
export ANTHROPIC_API_KEY=your-anthropic-api-key-here

# Add Cohere API key for embeddings
export COHERE_API_KEY=your-cohere-api-key-here
```

### 3. Update Dependencies (Already Done)

The `backend/pom.xml` has been updated to include:
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-anthropic-spring-boot-starter</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-cohere-spring-boot-starter</artifactId>
</dependency>
```

### 4. Update Configuration (Already Done)

The `application.yml` now includes:
```yaml
spring:
  ai:
    # Claude for chat
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-3-haiku-20240307
          temperature: 0.7
          max-tokens: 1000

    # Cohere for embeddings
    cohere:
      api-key: ${COHERE_API_KEY}
      embedding:
        options:
          model: embed-multilingual-v3.0
```

### 5. Build and Run

```bash
# Clean and rebuild
cd backend
mvn clean install

# Run the application
mvn spring-boot:run
```

## 🔍 Verification

Test that everything works:

1. **Start the application**
   ```bash
   ./start.sh
   ```

2. **Check the logs** for any errors related to API keys

3. **Test a chat message**
   - Navigate to http://localhost:8080
   - Send a test message
   - Verify you receive a response from Claude

4. **Check embeddings** (if using vector store)
   - Trigger a website analysis
   - Verify content is being indexed

## 📊 Comparison

| Feature | OpenAI GPT-3.5 | Claude 3 Haiku |
|---------|----------------|----------------|
| Cost (per 1M tokens input) | $0.50 | $0.25 |
| Cost (per 1M tokens output) | $1.50 | $1.25 |
| Context Window | 16K tokens | 200K tokens |
| Response Speed | Fast | Very Fast |
| Reasoning Quality | Excellent | Excellent |

## 🎛️ Configuration Options

### Using Different Claude Models

You can switch to other Claude models by changing the model in `application.yml`:

```yaml
spring:
  ai:
    anthropic:
      chat:
        options:
          # Claude 3 Haiku (fastest, cheapest)
          model: claude-3-haiku-20240307

          # Claude 3.5 Sonnet (balanced, recommended for complex tasks)
          # model: claude-3-5-sonnet-20241022

          # Claude 3 Opus (most capable, most expensive)
          # model: claude-3-opus-20240229
```

### Adjusting Temperature

Control response randomness (0.0 = focused, 1.0 = creative):

```yaml
temperature: 0.7  # Default balanced setting
```

### Adjusting Max Tokens

Control response length:

```yaml
max-tokens: 1000  # Default
# max-tokens: 4096  # For longer responses
```

## 🐛 Troubleshooting

### Error: "Anthropic API key not found"
**Solution**: Ensure `ANTHROPIC_API_KEY` is set in your environment

### Error: "Cohere API key not found"
**Solution**: Ensure `COHERE_API_KEY` is set (needed for embeddings)

### Error: "ChatClient bean not found"
**Solution**: Check that `AiConfiguration.java` exists and is properly configured

### Error: "Rate limit exceeded"
**Solution**: Claude has rate limits. Check your usage at console.anthropic.com

## 📚 Additional Resources

- [Anthropic Documentation](https://docs.anthropic.com/)
- [Claude Models Overview](https://docs.anthropic.com/claude/docs/models-overview)
- [Spring AI Anthropic](https://docs.spring.io/spring-ai/reference/api/clients/anthropic-chat.html)
- [Anthropic API Pricing](https://www.anthropic.com/pricing)

## ✅ Benefits of This Migration

1. **Cost Savings**: ~50% cheaper than GPT-3.5-turbo
2. **Better Context**: 200K vs 16K token context window
3. **Faster Responses**: Optimized for speed
4. **Safety Features**: Built-in safety guardrails
5. **Future Ready**: Easy to upgrade to Sonnet or Opus

## 🔙 Reverting (If Needed)

If you need to revert back to OpenAI:

1. Update `application.yml` to use OpenAI for chat
2. Update `AiConfiguration.java` to use OpenAI ChatModel
3. Rebuild and restart

## 💡 Next Steps

- Monitor your API usage in Anthropic Console
- Experiment with different Claude models
- Fine-tune temperature and max tokens for your use case
- Consider upgrading to Claude 3.5 Sonnet for more complex tasks

---

**Note**: Both API keys (Anthropic and Cohere) are required for the system to work properly - Anthropic for chat and Cohere for multilingual embeddings.
