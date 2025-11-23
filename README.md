# 🤖 TjanaBot AI Chatbot System

An open-source AI-powered chatbot platform built with Java Spring AI that analyzes websites and creates intelligent conversational agents automatically. Built using modern RAG (Retrieval Augmented Generation) architecture with Spring AI, Anthropic Claude 3 Haiku, and vector embeddings.

> **Disclaimer**: This is an independent open-source project and is not affiliated with, endorsed by, or associated with Noupe, JotForm, or any other commercial chatbot service.

## ✨ Features

### 🚀 TjanaBot Exclusive Features
- **⚡ Webhook Integration**: Send real-time conversation events to external systems (CRM, Slack, Discord, custom webhooks)
- **📊 Conversation Export**: Export chat history in JSON or CSV formats for analytics and reporting
- **💬 Quick Replies**: Configure suggested response buttons for common questions to improve UX

### 🧠 AI-Powered Intelligence
- **Automatic Website Analysis**: Crawls and analyzes website content to build knowledge base
- **Vector Embeddings**: Uses advanced AI embeddings for semantic search and context retrieval
- **Retrieval Augmented Generation (RAG)**: Combines website content with AI for accurate responses
- **Multi-Language Support**: Supports 12+ languages with automatic detection

### 🎨 Customization & Branding
- **Custom Branding**: Match your brand with custom colors, fonts, and styling
- **Flexible Theming**: Multiple theme options and customizable appearance
- **Embeddable Widget**: Easy-to-integrate JavaScript widget for any website
- **Responsive Design**: Works perfectly on desktop and mobile devices

### 📊 Analytics & Monitoring
- **Conversation Tracking**: Track all user interactions and conversations
- **Performance Analytics**: Monitor response times and user engagement
- **Language Analytics**: Understand which languages your users prefer
- **Real-time Dashboard**: Comprehensive analytics dashboard

### 🔧 Advanced Features
- **Session Management**: Persistent conversations across page loads
- **Custom Prompts**: Add specific instructions for chatbot behavior
- **Website Crawling**: Intelligent web scraping with content filtering
- **Vector Store Integration**: Scalable vector database for content storage

## 🚀 Quick Start

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- Anthropic API key (for Claude AI chat)
- Cohere API key (for embeddings)
- Optional: Pinecone API key (for vector storage)

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd ai-chatbot-system
   ```

2. **Configure environment variables**
   ```bash
   export ANTHROPIC_API_KEY=your-anthropic-api-key-here
   export COHERE_API_KEY=your-cohere-api-key-here  # For embeddings
   export PINECONE_API_KEY=your-pinecone-api-key-here  # Optional
   export PINECONE_ENVIRONMENT=your-pinecone-environment  # Optional
   ```

3. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

4. **Access the dashboard**
   - Open http://localhost:8080 in your browser
   - Default admin credentials: admin/admin123

## 📖 Usage Guide

### Creating Your First Chatbot

1. **Access the Dashboard**
   - Navigate to http://localhost:8080
   - Click "Create New Chatbot"

2. **Configure Basic Settings**
   - Enter chatbot name and description
   - Provide your website URL for analysis
   - Select primary language and supported languages

3. **Analyze Your Website**
   - Click "Analyze Website" to crawl and extract content
   - The system will automatically process your website pages
   - Content is indexed for AI training

4. **Test Your Chatbot**
   - Use the built-in testing interface
   - Try different questions to see how it responds
   - Adjust settings as needed

5. **Embed on Your Website**
   - Copy the generated embed code
   - Add it to your website's HTML
   - The chatbot widget will appear automatically

### API Endpoints

#### Chatbot Management
```bash
# Get all chatbots
GET /api/chatbots

# Create new chatbot
POST /api/chatbots
{
  "name": "My AI Assistant",
  "websiteUrl": "https://example.com",
  "description": "AI assistant for my website",
  "primaryLanguage": "en",
  "supportedLanguages": ["en", "es", "fr"]
}

# Update chatbot
PUT /api/chatbots/{id}

# Delete chatbot
DELETE /api/chatbots/{id}

# Analyze website
POST /api/chatbots/{id}/analyze

# Index content
POST /api/chatbots/{id}/index

# Get analytics
GET /api/chatbots/{id}/analytics
```

#### Chat API
```bash
# Send message to chatbot
POST /api/chat/{chatbotId}
{
  "message": "What services do you offer?",
  "sessionId": "session_123",
  "language": "en"
}

# Get chatbot by embed code
GET /api/chat/embed/{embedCode}
```

#### 🚀 NEW: Conversation Export API
```bash
# Export single conversation to JSON
GET /api/chatbots/conversations/{conversationId}/export/json

# Export single conversation to CSV
GET /api/chatbots/conversations/{conversationId}/export/csv

# Export all chatbot conversations to JSON
GET /api/chatbots/{id}/export/json

# Export all chatbot conversations to CSV
GET /api/chatbots/{id}/export/csv
```

#### 🚀 NEW: Quick Replies API
```bash
# Get quick replies for a chatbot
GET /api/chatbots/{id}/quick-replies

# Update chatbot with quick replies (in update request)
PUT /api/chatbots/{id}
{
  "quickReplies": "[{\"text\": \"What are your hours?\", \"value\": \"hours\"}, {\"text\": \"Pricing info\", \"value\": \"pricing\"}]"
}
```

#### 🚀 NEW: Webhook Configuration
```bash
# Configure webhook in chatbot update
PUT /api/chatbots/{id}
{
  "webhookUrl": "https://your-app.com/webhook",
  "webhookEvents": ["conversation_started", "message_sent", "conversation_ended"]
}

# Webhook payload structure (sent to your URL):
{
  "event": "conversation_started",
  "chatbot_id": 1,
  "chatbot_name": "My Chatbot",
  "timestamp": 1698765432000,
  "data": {
    "conversation_id": 123,
    "user_ip": "192.168.1.1",
    "language": "en",
    "created_at": "2024-01-01T12:00:00"
  }
}
```

## 🛠️ Configuration

### Application Properties

```yaml
# AI Configuration
spring:
  ai:
    # Anthropic Claude for chat
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

# Website Analysis Settings
app:
  website-analysis:
    max-pages: 50
    max-depth: 3
    timeout-seconds: 30
    user-agent: "AI-Chatbot-Crawler/1.0"

# Chatbot Settings
app:
  chatbot:
    max-conversation-history: 10
    default-language: en
    supported-languages: en,es,fr,de,it,pt,ru,zh,ja,ko
```

### Database Configuration

The application uses H2 database by default for development. For production, configure PostgreSQL:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/chatbot_db
    username: your-username
    password: your-password
    driver-class-name: org.postgresql.Driver
```

## 🎨 Customization

### Branding Configuration

Customize your chatbot's appearance using JSON configuration:

```json
{
  "primaryColor": "#007bff",
  "secondaryColor": "#6c757d",
  "fontFamily": "Arial, sans-serif",
  "borderRadius": "8px",
  "buttonStyle": "rounded"
}
```

### Custom Prompts

Add specific instructions for your chatbot:

```
You are a helpful AI assistant for [Your Company Name].
- Always be friendly and professional
- Focus on helping customers with product information
- If you don't know something, suggest contacting support
- Use a conversational tone
```

## 📊 Analytics Dashboard

The system provides comprehensive analytics:

- **Conversation Metrics**: Total conversations, active sessions
- **Performance Stats**: Average response time, message counts
- **Language Distribution**: Which languages users prefer
- **Website Analysis**: Content extraction statistics
- **User Engagement**: Conversation duration and patterns

## 🔧 Advanced Features

### Vector Store Integration

For production deployments, configure Pinecone for scalable vector storage:

```yaml
spring:
  ai:
    vectorstore:
      pinecone:
        api-key: ${PINECONE_API_KEY}
        environment: ${PINECONE_ENVIRONMENT}
        index-name: chatbot-vectors
        namespace: default
```

### Multi-Language Support

The system automatically detects user language and responds appropriately:

- **Automatic Detection**: Uses browser language settings
- **Manual Override**: Users can select preferred language
- **Fallback Support**: Defaults to English if language not supported

### Website Analysis

Advanced web crawling features:

- **Smart Content Extraction**: Removes navigation, ads, and irrelevant content
- **Depth Control**: Configurable crawling depth
- **Content Filtering**: Skips binary files and irrelevant pages
- **Language Detection**: Automatically detects page language

## 🚀 Deployment

### Docker Deployment

```dockerfile
FROM openjdk:17-jdk-slim
COPY target/ai-chatbot-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Production Considerations

1. **Database**: Use PostgreSQL for production
2. **Vector Store**: Configure Pinecone for scalability
3. **Security**: Implement proper authentication and authorization
4. **Monitoring**: Add application monitoring and logging
5. **Scaling**: Use load balancers for high availability

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🆘 Support

For support and questions:
- Create an issue in the repository
- Check the documentation
- Review the API documentation

## 🎯 Roadmap

- [x] Advanced AI models (Claude 3 Haiku implemented)
- [ ] Support for other Claude models (Sonnet, Opus)
- [ ] Voice chat integration
- [ ] Mobile app
- [ ] Advanced analytics
- [ ] Multi-tenant support
- [ ] API rate limiting
- [ ] Webhook integrations

---

**Built with ❤️ using Spring AI and Java**
