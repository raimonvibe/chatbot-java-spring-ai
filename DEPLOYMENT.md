# 🚀 Deployment Guide - TjanaBot AI Chatbot System

This comprehensive guide covers deploying both the **Spring Boot backend** (on Render) and the **Next.js frontend** (on Vercel).

## 📋 Overview

Your application has two components:
- **Backend**: Spring Boot + Java (deployed to Render)
- **Frontend**: Next.js (deployed to Vercel)

## Prerequisites

Before deploying, ensure you have:

- [ ] GitHub account with your code repository
- [ ] Render.com account (free tier available)
- [ ] Vercel account (free tier available)
- [ ] Anthropic API key
- [ ] Cohere API key (for embeddings)

---

# 🔧 Part 1: Backend Deployment (Render)

Deploy your Spring Boot backend to Render.com.

## Step 1: Prepare Your Repository

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
   ├── src/main/java/com/tjanabot/chatbot/
   ├── src/main/resources/
   ├── frontend/
   └── README.md
   ```

## Step 2: Create Render Web Service

1. **Log into Render Dashboard**
   - Go to https://render.com
   - Sign in with your GitHub account

2. **Create New Web Service**
   - Click "New +" → "Web Service"
   - Connect your GitHub repository
   - Select your AI chatbot repository

3. **Configure Build Settings**
   ```
   Name: ai-chatbot-backend
   Environment: Java
   Build Command: ./mvnw clean package -DskipTests
   Start Command: java -jar target/tjanabot-ai-chatbot-*.jar
   ```

## Step 3: Set Up PostgreSQL Database

1. **Create PostgreSQL Database**
   - In Render dashboard, click "New +" → "PostgreSQL"
   - Name: `ai-chatbot-db`
   - Plan: Free (for testing) or Starter (for production)
   - Region: Choose closest to your users

2. **Get Database Connection Details**
   - Copy the `External Database URL`
   - Note the database credentials

## Step 4: Configure Environment Variables

In the Render dashboard, go to "Environment" tab and add:

### Required Variables
```bash
# Anthropic Configuration (Primary AI model)
ANTHROPIC_API_KEY=your-anthropic-api-key-here

# Cohere Configuration (For embeddings only)
COHERE_API_KEY=your-cohere-api-key-here

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

### Optional Variables
```bash
# Pinecone Configuration (for production vector storage)
PINECONE_API_KEY=your-pinecone-api-key-here
PINECONE_ENVIRONMENT=your-pinecone-environment

# Security
SPRING_SECURITY_USER_NAME=admin
SPRING_SECURITY_USER_PASSWORD=your-secure-password

# Logging
LOG_LEVEL=INFO
LOGGING_LEVEL_COM_TJANABOT=INFO
LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AI=WARN

# Performance
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=5
```

## Step 5: Deploy Backend

1. **Deploy the Application**
   - Click "Deploy" in Render dashboard
   - Wait for build to complete (5-10 minutes)
   - Check build logs for any errors

2. **Verify Deployment**
   - Your backend will be available at: `https://your-backend-name.onrender.com`
   - Test the health endpoint: `https://your-backend-name.onrender.com/actuator/health`

3. **Note Your Backend URL**
   - Copy this URL - you'll need it for frontend deployment
   - Example: `https://ai-chatbot-backend.onrender.com`

---

# 🎨 Part 2: Frontend Deployment (Vercel)

Deploy your Next.js frontend to Vercel.

## Step 1: Deploy to Vercel Dashboard

### Option A: Using Vercel Dashboard (Recommended)

1. **Go to Vercel Dashboard**
   - Visit [vercel.com](https://vercel.com)
   - Click "Add New Project"
   - Import your GitHub repository

2. **Configure Project Settings**
   - **Framework Preset**: Next.js
   - **Root Directory**: `frontend` ⚠️ **IMPORTANT**
   - **Build Command**: `npm run build` (auto-detected)
   - **Output Directory**: `.next` (auto-detected)
   - **Install Command**: `npm install` (auto-detected)

3. **Set Environment Variables**

   Add these environment variables in Vercel:

   | Name | Value | Description |
   |------|-------|-------------|
   | `NEXT_PUBLIC_API_URL` | Your Render backend URL | Example: `https://ai-chatbot-backend.onrender.com` |
   | `NEXT_PUBLIC_DEFAULT_CHATBOT_ID` | `1` | Your default chatbot ID (optional) |

4. **Deploy**
   - Click "Deploy"
   - Wait for build to complete
   - Visit your deployment URL

### Option B: Using Vercel CLI

1. **Install Vercel CLI**
   ```bash
   npm install -g vercel
   ```

2. **Login to Vercel**
   ```bash
   vercel login
   ```

3. **Deploy from frontend directory**
   ```bash
   cd frontend
   vercel
   ```

4. **Follow the prompts:**
   - Set up and deploy? `Y`
   - Which scope? Select your account
   - Link to existing project? `N` (first time) or `Y` (subsequent)
   - What's your project's name? `chatbot-frontend` (or your choice)
   - In which directory is your code located? `./`
   - Auto-detected Next.js. Continue? `Y`

5. **Set environment variables**
   ```bash
   vercel env add NEXT_PUBLIC_API_URL
   # Enter your backend URL when prompted
   ```

6. **Deploy to production**
   ```bash
   vercel --prod
   ```

## Step 2: Configure CORS on Backend

Make sure your Spring Boot backend allows requests from your Vercel domain.

Update `src/main/java/com/tjanabot/chatbot/controller/ChatController.java`:

```java
@CrossOrigin(origins = {"http://localhost:3000", "https://your-vercel-app.vercel.app"})
```

Or add this to your Render environment variables:

```bash
CORS_ALLOWED_ORIGINS=http://localhost:3000,https://your-vercel-app.vercel.app
```

Then redeploy your backend on Render.

---

# 🐛 Troubleshooting

## Backend Issues (Render)

### Build Failures
```bash
# Check Maven wrapper permissions
chmod +x mvnw

# Verify Java version in build logs (should be Java 17+)
```

### Database Connection Issues
- Verify `DATABASE_URL` is set correctly
- Check database credentials
- Ensure database is running in Render

### Memory Issues
Add JVM memory settings in Start Command:
```bash
java -Xmx512m -Xms256m -jar target/tjanabot-ai-chatbot-*.jar
```

### API Key Issues
- Verify `ANTHROPIC_API_KEY` and `COHERE_API_KEY` are set
- Check API keys have sufficient credits
- Test API keys separately

## Frontend Issues (Vercel)

### 404 Not Found Error
**Cause**: Vercel is looking in the wrong directory

**Solution**:
1. Go to Vercel Dashboard → Your Project → Settings
2. Under "Build & Development Settings"
3. Set **Root Directory** to `frontend`
4. Redeploy

### API Calls Failing (CORS Errors)
**Cause**: Backend not allowing requests from Vercel domain

**Solution**:
1. Update `@CrossOrigin` annotations in Spring Boot controllers
2. Add your Vercel domain to allowed origins
3. Redeploy backend

### Environment Variables Not Working
**Cause**: Environment variables not set in Vercel

**Solution**:
1. Go to Vercel Dashboard → Your Project → Settings → Environment Variables
2. Add `NEXT_PUBLIC_API_URL` with your backend URL
3. Redeploy (important: env vars only apply to new builds)

### Blank Page After Deploy
**Cause**: Usually JavaScript errors or wrong API URL

**Solution**:
1. Check browser console for errors
2. Verify `NEXT_PUBLIC_API_URL` is set correctly
3. Make sure backend is running and accessible
4. Check Network tab in browser DevTools

---

# ✅ Production Checklist

## Backend (Render)
- [ ] Code pushed to GitHub
- [ ] Render account created
- [ ] Web service configured
- [ ] Environment variables set (ANTHROPIC_API_KEY, COHERE_API_KEY)
- [ ] PostgreSQL database created and linked
- [ ] Application deployed successfully
- [ ] Health check passing (`/actuator/health`)
- [ ] Backend URL noted for frontend

## Frontend (Vercel)
- [ ] Vercel account created
- [ ] Root Directory set to `frontend`
- [ ] Environment variables set (`NEXT_PUBLIC_API_URL`)
- [ ] CORS configured on backend
- [ ] Frontend deployed successfully
- [ ] Can access frontend URL
- [ ] API calls working correctly

## Testing
- [ ] Dashboard accessible
- [ ] Can create chatbot
- [ ] Can analyze website
- [ ] Chat functionality working
- [ ] Responses generated correctly

---

# 🎯 Post-Deployment Steps

## 1. Create Your First Chatbot
- Access the dashboard at your Vercel URL
- Login with credentials (default: admin/admin123)
- Configure your website URL
- Analyze your website content
- Test the chatbot functionality

## 2. Custom Domains (Optional)

### Backend (Render)
1. Go to Render dashboard → Settings → Custom Domains
2. Add your domain (e.g., `api.yourdomain.com`)
3. Configure DNS records as instructed

### Frontend (Vercel)
1. Go to Vercel Dashboard → Your Project → Settings → Domains
2. Add your domain (e.g., `chat.yourdomain.com`)
3. Follow DNS configuration instructions
4. Wait for DNS propagation

## 3. Monitoring

### Backend Monitoring (Render)
- View logs: Render Dashboard → Logs tab
- Monitor health: Check `/actuator/health` endpoint
- Set up alerts for downtime

### Frontend Monitoring (Vercel)
- Vercel Analytics: Automatic page views and Web Vitals
- Vercel Logs: Real-time function logs
- Deployments: Track all deployments

---

# 💰 Pricing Overview

## Render Plans
| Plan | Use Case | Memory | CPU | Price |
|------|----------|--------|-----|-------|
| Free | Development/Testing | 512MB | 0.1 CPU | $0/month |
| Starter | Small Production | 512MB | 0.5 CPU | $7/month |
| Standard | Medium Production | 1GB | 1 CPU | $25/month |

## Vercel Plans
- **Hobby Plan**: Free for personal projects
- **Pro Plan**: $20/month for production apps
- **Enterprise**: Custom pricing

---

# 🔒 Security Best Practices

1. **Use Strong Passwords**
   - Change default admin password
   - Use environment variables for secrets

2. **API Key Security**
   - Never commit API keys to Git
   - Rotate keys regularly
   - Use environment-specific keys

3. **CORS Configuration**
   - Only allow specific origins
   - Don't use `origins = "*"` in production

4. **HTTPS Only**
   - Both Render and Vercel provide automatic HTTPS
   - Ensure all API calls use HTTPS

5. **Rate Limiting**
   - Implement rate limiting on backend
   - Monitor for abuse

---

# 📈 Scaling Considerations

## Horizontal Scaling (Backend)
- Use multiple Render instances
- Configure load balancing
- Implement session sharing

## Database Scaling
- Upgrade PostgreSQL plan
- Consider read replicas
- Implement connection pooling

## Caching
- Add Redis for session storage
- Implement response caching
- Use CDN for static assets

---

# 🎉 Success!

Your AI Chatbot System is now fully deployed!

- **Frontend**: `https://your-app.vercel.app`
- **Backend**: `https://your-backend.onrender.com`
- **API Health**: `https://your-backend.onrender.com/actuator/health`

Start creating intelligent chatbots for your website! 🤖✨

---

# 📞 Support

If you encounter issues:

1. **Check Logs**
   - Review Render build and runtime logs
   - Check Vercel deployment logs
   - Look for error messages

2. **Verify Configuration**
   - Ensure all environment variables are set
   - Check database connectivity
   - Verify API keys are valid
   - Confirm CORS settings

3. **Test Locally First**
   - Run backend locally
   - Run frontend locally
   - Verify all features work
   - Check for missing dependencies

---

# 📚 Additional Resources

- [Render Documentation](https://render.com/docs)
- [Vercel Documentation](https://vercel.com/docs)
- [Next.js Deployment](https://nextjs.org/docs/deployment)
- [Spring Boot on Render](https://render.com/docs/deploy-spring-boot)
- [Vercel Environment Variables](https://vercel.com/docs/concepts/projects/environment-variables)
