# 🤖 Noupe AI Chatbot System

A comprehensive AI-powered chatbot system built with Java Spring AI that analyzes websites and creates intelligent chatbots automatically. This is a complete clone of the Noupe AI chatbot service with advanced features.

## ✨ Features

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
- OpenAI API key (for AI functionality)
- Optional: Pinecone API key (for vector storage)

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd ai-chatbot-system
   ```

2. **Configure environment variables**
   ```bash
   export OPENAI_API_KEY=your-openai-api-key-here
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

## 🛠️ Configuration

### Application Properties

```yaml
# OpenAI Configuration
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-3.5-turbo
          temperature: 0.7
          max-tokens: 1000

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

- [ ] Advanced AI models (GPT-4, Claude)
- [ ] Voice chat integration
- [ ] Mobile app
- [ ] Advanced analytics
- [ ] Multi-tenant support
- [ ] API rate limiting
- [ ] Webhook integrations

---

**Built with ❤️ using Spring AI and Java**
