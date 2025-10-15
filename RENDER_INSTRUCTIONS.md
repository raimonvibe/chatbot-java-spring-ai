# 🚀 Deploy AI Chatbot System on Render

This guide will help you deploy the Noupe AI Chatbot System on Render.com, a modern cloud platform for hosting applications.

## 📋 Prerequisites

Before deploying, ensure you have:

- [ ] Render.com account (free tier available)
- [ ] OpenAI API key
- [ ] GitHub repository with your code
- [ ] Basic understanding of environment variables

## 🏗️ Deployment Steps

### Step 1: Prepare Your Repository

1. **Push your code to GitHub**
   ```bash
   git init
   git add .
   git commit -m "Initial commit: AI Chatbot System"
   git branch -M main
   git remote add origin https://github.com/yourusername/ai-chatbot-system.git
   git push -u origin main
   ```

2. **Verify your project structure**
   ```
   ├── pom.xml
   ├── src/main/java/com/noupe/chatbot/
   ├── src/main/resources/
   ├── Dockerfile (optional)
   └── README.md
   ```

### Step 2: Create Render Web Service

1. **Log into Render Dashboard**
   - Go to https://render.com
   - Sign in with your GitHub account

2. **Create New Web Service**
   - Click "New +" → "Web Service"
   - Connect your GitHub repository
   - Select your AI chatbot repository

3. **Configure Build Settings**
   ```
   Name: ai-chatbot-system
   Environment: Java
   Build Command: ./mvnw clean package -DskipTests
   Start Command: java -jar target/ai-chatbot-*.jar
   ```

### Step 3: Configure Environment Variables

In the Render dashboard, go to "Environment" tab and add:

#### Required Variables
```bash
# OpenAI Configuration
OPENAI_API_KEY=your-openai-api-key-here

# Application Configuration
SPRING_PROFILES_ACTIVE=production
SERVER_PORT=10000

# Database Configuration (Render PostgreSQL)
SPRING_DATASOURCE_URL=${DATABASE_URL}
SPRING_DATASOURCE_USERNAME=${DB_USERNAME}
SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver

# JPA Configuration
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_JPA_SHOW_SQL=false
SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
```

#### Optional Variables
```bash
# Pinecone Configuration (for production vector storage)
PINECONE_API_KEY=your-pinecone-api-key-here
PINECONE_ENVIRONMENT=your-pinecone-environment

# Security
JWT_SECRET=your-jwt-secret-key-here
JWT_EXPIRATION=86400000

# Logging
LOG_LEVEL=INFO
```

### Step 4: Set Up PostgreSQL Database

1. **Create PostgreSQL Database**
   - In Render dashboard, click "New +" → "PostgreSQL"
   - Name: `ai-chatbot-db`
   - Plan: Free (for testing) or Starter (for production)
   - Region: Choose closest to your users

2. **Get Database Connection Details**
   - Copy the `External Database URL`
   - Note the database credentials

3. **Update Environment Variables**
   - The `DATABASE_URL` will be automatically set by Render
   - Verify other database variables are correct

### Step 5: Configure Build Settings

#### Maven Wrapper (Recommended)
If you don't have `mvnw`, create it:

```bash
# Generate Maven wrapper
mvn wrapper:wrapper

# Commit the wrapper files
git add mvnw mvnw.cmd .mvn/
git commit -m "Add Maven wrapper"
git push
```

#### Build Configuration
```
Build Command: ./mvnw clean package -DskipTests
Start Command: java -jar target/ai-chatbot-*.jar
```

### Step 6: Deploy and Test

1. **Deploy the Application**
   - Click "Deploy" in Render dashboard
   - Wait for build to complete (5-10 minutes)
   - Check build logs for any errors

2. **Verify Deployment**
   - Your app will be available at: `https://your-app-name.onrender.com`
   - Test the health endpoint: `https://your-app-name.onrender.com/actuator/health`

3. **Access the Dashboard**
   - Go to your app URL
   - Default login: admin/admin123
   - Create your first chatbot

## 🔧 Advanced Configuration

### Custom Domain (Optional)

1. **Add Custom Domain**
   - In Render dashboard, go to "Settings" → "Custom Domains"
   - Add your domain (e.g., `chatbot.yourdomain.com`)
   - Configure DNS records as instructed

2. **SSL Certificate**
   - Render automatically provides SSL certificates
   - Your app will be available via HTTPS

### Production Optimizations

#### Environment Variables for Production
```bash
# Performance
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=5

# Security
SPRING_SECURITY_USER_NAME=admin
SPRING_SECURITY_USER_PASSWORD=your-secure-password

# Logging
LOGGING_LEVEL_COM_NOUPE=INFO
LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AI=WARN
```

#### Database Optimizations
```bash
# Connection Pool
SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=20000
SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT=300000
SPRING_DATASOURCE_HIKARI_MAX_LIFETIME=1200000
```

### Monitoring and Logs

1. **View Application Logs**
   - Go to "Logs" tab in Render dashboard
   - Monitor startup and runtime logs
   - Check for any errors or warnings

2. **Health Monitoring**
   - Render provides built-in health checks
   - Monitor response times and uptime
   - Set up alerts for downtime

## 🚨 Troubleshooting

### Common Issues

#### Build Failures
```bash
# Check Maven wrapper permissions
chmod +x mvnw

# Verify Java version in build logs
# Should be Java 17 or higher
```

#### Database Connection Issues
```bash
# Verify DATABASE_URL is set correctly
# Check database credentials
# Ensure database is running
```

#### Memory Issues
```bash
# Add JVM memory settings
JAVA_OPTS=-Xmx512m -Xms256m

# Or in start command:
java -Xmx512m -Xms256m -jar target/ai-chatbot-*.jar
```

#### OpenAI API Issues
```bash
# Verify OPENAI_API_KEY is set
# Check API key has sufficient credits
# Test API key separately
```

### Debug Mode

Enable debug logging for troubleshooting:

```bash
LOG_LEVEL=DEBUG
LOGGING_LEVEL_COM_NOUPE=DEBUG
LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AI=DEBUG
```

## 📊 Performance Optimization

### Render Plan Recommendations

| Plan | Use Case | Memory | CPU | Price |
|------|----------|--------|-----|-------|
| Free | Development/Testing | 512MB | 0.1 CPU | $0/month |
| Starter | Small Production | 512MB | 0.5 CPU | $7/month |
| Standard | Medium Production | 1GB | 1 CPU | $25/month |
| Pro | Large Production | 2GB+ | 2+ CPU | $85+/month |

### Scaling Considerations

1. **Horizontal Scaling**
   - Use multiple instances for high availability
   - Configure load balancing
   - Implement session sharing

2. **Database Scaling**
   - Upgrade to higher PostgreSQL plan
   - Consider read replicas for analytics
   - Implement connection pooling

3. **Caching**
   - Add Redis for session storage
   - Implement response caching
   - Use CDN for static assets

## 🔒 Security Best Practices

### Environment Security
```bash
# Use strong passwords
SPRING_SECURITY_USER_PASSWORD=your-very-secure-password

# Rotate API keys regularly
OPENAI_API_KEY=your-new-api-key

# Use environment-specific configurations
SPRING_PROFILES_ACTIVE=production
```

### Application Security
- Enable HTTPS only
- Implement rate limiting
- Add input validation
- Use secure headers
- Regular security updates

## 📈 Monitoring and Analytics

### Built-in Monitoring
- Render provides basic metrics
- Monitor CPU, memory, and response times
- Set up alerts for critical issues

### Application Analytics
- Use the built-in analytics dashboard
- Monitor chatbot performance
- Track user engagement
- Analyze conversation patterns

## 🚀 Deployment Checklist

- [ ] Code pushed to GitHub
- [ ] Render account created
- [ ] Web service configured
- [ ] Environment variables set
- [ ] PostgreSQL database created
- [ ] OpenAI API key configured
- [ ] Application deployed successfully
- [ ] Health check passing
- [ ] Dashboard accessible
- [ ] First chatbot created and tested

## 🎯 Next Steps

After successful deployment:

1. **Create Your First Chatbot**
   - Access the dashboard
   - Configure your website URL
   - Analyze your website content
   - Test the chatbot functionality

2. **Customize and Brand**
   - Set up custom branding
   - Configure AI prompts
   - Test multi-language support

3. **Monitor and Optimize**
   - Monitor performance metrics
   - Analyze user interactions
   - Optimize response times

4. **Scale as Needed**
   - Upgrade Render plan if needed
   - Add more chatbots
   - Implement advanced features

## 📞 Support

If you encounter issues:

1. **Check Render Logs**
   - Review build and runtime logs
   - Look for error messages
   - Check environment variables

2. **Verify Configuration**
   - Ensure all required variables are set
   - Check database connectivity
   - Verify API keys are valid

3. **Test Locally**
   - Run the application locally first
   - Verify all features work
   - Check for any missing dependencies

## 🎉 Success!

Your AI Chatbot System is now deployed on Render! 

- **Dashboard**: `https://your-app-name.onrender.com`
- **API**: `https://your-app-name.onrender.com/api`
- **Health**: `https://your-app-name.onrender.com/actuator/health`

Start creating intelligent chatbots for your website! 🤖✨
