# 🚀 Getting Started with Prayer-Chat AI Chatbot System

Welcome to **Prayer-Chat** - an open-source AI-powered chatbot platform that automatically analyzes websites and creates intelligent conversational agents using modern RAG architecture.

> **Note**: This is an independent open-source project not affiliated with any commercial chatbot service.

## 🎯 What You Get

### ✨ Core Features
- **🧠 AI-Powered Intelligence**: Uses Anthropic Claude 3 Haiku for natural conversations
- **🌐 Website Analysis**: Automatically crawls and analyzes website content
- **📚 Vector Embeddings**: Advanced semantic search using Cohere multilingual embeddings
- **🌍 Multi-Language Support**: 12+ languages with automatic detection
- **🎨 Custom Branding**: Match your brand with custom styling
- **📊 Analytics Dashboard**: Comprehensive conversation and performance analytics
- **🔧 Easy Integration**: Simple embeddable widget for any website

### 🏗️ Architecture
- **Backend**: Java Spring Boot with Spring AI
- **Database**: H2 (development) / PostgreSQL (production)
- **Vector Store**: Pinecone for scalable embeddings
- **Frontend**: Thymeleaf templates with Bootstrap
- **AI**: Anthropic Claude 3 Haiku for chat, Cohere for embeddings

## 🚀 Quick Start

### Prerequisites
- **Java 17+** installed
- **Maven 3.6+** installed
- **Anthropic API Key** (required for chat functionality)
- **Cohere API Key** (required for embeddings)

### 1. Setup Environment

```bash
# Clone or download the project
cd ai-chatbot-system

# Set your Anthropic API key for Claude
export ANTHROPIC_API_KEY=your-anthropic-api-key-here

# Set your Cohere API key for embeddings
export COHERE_API_KEY=your-cohere-api-key-here

# Optional: Set Pinecone credentials for production
export PINECONE_API_KEY=your-pinecone-api-key-here
export PINECONE_ENVIRONMENT=your-pinecone-environment
```

### 2. Run the Application

```bash
# Option 1: Using Maven
cd backend
mvn spring-boot:run

# Option 2: Using the startup script (Linux/Mac)
cd backend
chmod +x start.sh
./start.sh

# Option 3: Build and run JAR
cd backend
mvn clean package
java -jar target/prayer-chat-ai-chatbot-*.jar
```

### 3. Access the Dashboard

Open your browser and navigate to:
- **Dashboard**: http://localhost:8081
- **H2 Console**: http://localhost:8081/h2-console
- **Default Login**: admin/admin123

## 📖 Step-by-Step Guide

### Step 1: Create Your First Chatbot

1. **Navigate to Dashboard**
   - Go to http://localhost:8081
   - Click "Create New Chatbot"

2. **Configure Basic Settings**
   ```
   Name: My AI Assistant
   Website URL: https://your-website.com
   Description: AI assistant for customer support
   Primary Language: English
   ```

3. **Add Supported Languages**
   - Select additional languages your users might use
   - The system will automatically detect user language

4. **Customize AI Behavior**
   ```
   Custom Prompt: You are a helpful AI assistant for [Company Name].
   Always be friendly and professional. Focus on helping customers
   with product information and support.
   ```

5. **Configure Branding**
   ```json
   {
     "primaryColor": "#007bff",
     "secondaryColor": "#6c757d",
     "fontFamily": "Arial, sans-serif",
     "borderRadius": "8px"
   }
   ```

### Step 2: Analyze Your Website

1. **Start Website Analysis**
   - Click "Analyze Website" on your chatbot
   - The system will crawl your website automatically
   - This may take a few minutes depending on site size

2. **Index Content**
   - Click "Index Content" to process the crawled content
   - This creates vector embeddings for AI training
   - Your chatbot is now ready to answer questions!

### Step 3: Test Your Chatbot

1. **Use the Test Interface**
   - Click "Test Chatbot" on your chatbot
   - Try different questions to see how it responds
   - Test in different languages

2. **Sample Test Questions**
   - "What services do you offer?"
   - "How can I contact you?"
   - "What are your business hours?"
   - "Tell me about your company"

### Step 4: Embed on Your Website

1. **Get Embed Code**
   - Click "Get Embed Code" on your chatbot
   - Copy the generated HTML code

2. **Add to Your Website**
   ```html
   <!-- Add this to your website's HTML -->
   <div id="prayer-chat-chatbot-1" data-chatbot-id="1"></div>
   <script>
       (function() {
           var script = document.createElement('script');
           script.src = 'http://localhost:8081/js/chatbot-widget.js';
           script.async = true;
           script.onload = function() {
               PrayerChat.init({
                   chatbotId: 1,
                   apiUrl: 'http://localhost:8081/api',
                   theme: 'default'
               });
           };
           document.head.appendChild(script);
       })();
   </script>
   ```

## 🔧 Configuration

### Environment Variables

Create a `.env` file or set environment variables:

```bash
# Required
COHERE_API_KEY=your-cohere-api-key-here

# Optional - for production vector storage
PINECONE_API_KEY=your-pinecone-api-key-here
PINECONE_ENVIRONMENT=your-pinecone-environment

# Database (for production)
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/chatbot_db
SPRING_DATASOURCE_USERNAME=chatbot_user
SPRING_DATASOURCE_PASSWORD=chatbot_password
```

### Application Properties

Edit `backend/src/main/resources/application.yml`:

```yaml
# Website Analysis Settings
app:
  website-analysis:
    max-pages: 50          # Maximum pages to crawl
    max-depth: 3           # Maximum crawl depth
    timeout-seconds: 30    # Request timeout

# Chatbot Settings  
app:
  chatbot:
    max-conversation-history: 10  # Messages to remember
    default-language: en          # Default language
    supported-languages: en,es,fr,de,it,pt,ru,zh,ja,ko
```

## 📊 Analytics & Monitoring

### Dashboard Features
- **Chatbot Overview**: See all your chatbots at a glance
- **Conversation Analytics**: Track user interactions and engagement
- **Performance Metrics**: Monitor response times and success rates
- **Language Distribution**: See which languages your users prefer
- **Website Analysis Stats**: Content extraction and indexing statistics

### Key Metrics
- Total conversations
- Active sessions
- Average response time
- Message counts
- Language distribution
- User engagement patterns

## 🚀 Production Deployment

### Docker Deployment

```bash
# Build and run with Docker Compose
docker-compose up -d

# Or build individual image
docker build -t ai-chatbot .
docker run -p 8081:8081 -e COHERE_API_KEY=your-key ai-chatbot
```

### Production Considerations

1. **Database**: Use PostgreSQL instead of H2
2. **Vector Store**: Configure Pinecone for scalability
3. **Security**: Implement proper authentication
4. **Monitoring**: Add application monitoring
5. **Scaling**: Use load balancers for high availability

## 🛠️ API Reference

### Chatbot Management

```bash
# Get all chatbots
GET /api/chatbots

# Create chatbot
POST /api/chatbots
{
  "name": "My Assistant",
  "websiteUrl": "https://example.com",
  "primaryLanguage": "en"
}

# Update chatbot
PUT /api/chatbots/{id}

# Delete chatbot
DELETE /api/chatbots/{id}

# Analyze website
POST /api/chatbots/{id}/analyze

# Get analytics
GET /api/chatbots/{id}/analytics
```

### Chat API

```bash
# Send message
POST /api/chat/{chatbotId}
{
  "message": "Hello!",
  "sessionId": "session_123",
  "language": "en"
}

# Get chatbot config
GET /api/chat/embed/{embedCode}
```

## 🎨 Customization

### Widget Styling

Customize the chatbot widget appearance:

```javascript
PrayerChat.init({
    chatbotId: 1,
    apiUrl: 'http://localhost:8081/api',
    theme: 'custom',
    primaryColor: '#007bff',
    secondaryColor: '#6c757d',
    fontFamily: 'Arial, sans-serif',
    borderRadius: '12px'
});
```

### Custom Prompts

Add specific instructions for your chatbot:

```
You are a helpful AI assistant for [Your Company Name].
- Always be friendly and professional
- Focus on helping customers with product information
- If you don't know something, suggest contacting support
- Use a conversational tone
- Provide specific examples when possible
```

## 🔍 Troubleshooting

### Common Issues

1. **"Cohere API Key not found"**
   - Set the COHERE_API_KEY environment variable
   - Check your API key is valid and has credits

2. **"Website analysis failed"**
   - Check the website URL is accessible
   - Verify the website allows crawling
   - Check network connectivity

3. **"Chatbot not responding"**
   - Ensure content has been indexed
   - Check Cohere API key and credits
   - Verify chatbot is active

4. **"Widget not loading"**
   - Check the embed code is correct
   - Verify the API URL is accessible
   - Check browser console for errors

### Debug Mode

Enable debug logging:

```yaml
logging:
  level:
    com.prayer_chat: DEBUG
    org.springframework.ai: DEBUG
```

## 📚 Additional Resources

- **Documentation**: See README.md for detailed documentation
- **API Reference**: Check the controller classes for API details
- **Examples**: Look at the test templates for usage examples
- **Configuration**: Review application.yml for all settings

## 🤝 Support

If you encounter any issues:

1. Check the troubleshooting section above
2. Review the application logs
3. Verify your configuration
4. Check the GitHub issues (if applicable)

## 🎉 You're Ready!

Your AI chatbot system is now ready to use! Create your first chatbot, analyze your website, and start engaging with your customers through intelligent AI conversations.

**Happy Chatbot Building! 🤖✨**
