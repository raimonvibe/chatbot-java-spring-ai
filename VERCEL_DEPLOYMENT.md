# 🚀 Deploying to Vercel

This guide will help you deploy the Next.js frontend to Vercel.

## 📋 Prerequisites

- A Vercel account ([sign up here](https://vercel.com/signup))
- Your Spring Boot backend deployed somewhere (Render, Railway, AWS, etc.)
- GitHub repository connected to Vercel

## 🎯 Deployment Steps

### Option 1: Using Vercel Dashboard (Recommended)

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
   | `NEXT_PUBLIC_API_URL` | Your backend URL | Example: `https://your-backend.onrender.com` |
   | `NEXT_PUBLIC_DEFAULT_CHATBOT_ID` | `1` | Your default chatbot ID (optional) |

4. **Deploy**
   - Click "Deploy"
   - Wait for build to complete
   - Visit your deployment URL

### Option 2: Using Vercel CLI

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
   - Override settings? `N` (unless you need to)

5. **Set environment variables**
   ```bash
   vercel env add NEXT_PUBLIC_API_URL
   # Enter your backend URL when prompted
   ```

6. **Deploy to production**
   ```bash
   vercel --prod
   ```

## 🔧 Configuration Files

### vercel.json (Root Directory)

The repository includes a `vercel.json` file in the root that configures the build for the monorepo structure:

```json
{
  "buildCommand": "cd frontend && npm run build",
  "devCommand": "cd frontend && npm run dev",
  "installCommand": "cd frontend && npm install",
  "outputDirectory": "frontend/.next"
}
```

**Note**: This is only needed if you're deploying from the root directory. If you set Root Directory to `frontend` in Vercel settings, this file is not needed.

## 🌐 Backend Deployment

Your Spring Boot backend needs to be deployed separately. Here are some options:

### Option A: Render.com (Free Tier Available)
1. Sign up at [render.com](https://render.com)
2. Create a new Web Service
3. Connect your GitHub repository
4. Configure:
   - **Build Command**: `mvn clean install`
   - **Start Command**: `java -jar target/chatweave-ai-chatbot-*.jar`
   - **Environment Variables**: Add `ANTHROPIC_API_KEY` and `OPENAI_API_KEY`

### Option B: Railway.app
1. Sign up at [railway.app](https://railway.app)
2. New Project → Deploy from GitHub
3. Select your repository
4. Add environment variables
5. Deploy

### Option C: Heroku
1. Install Heroku CLI
2. Create a new Heroku app
3. Add buildpack: `heroku/java`
4. Set environment variables
5. Deploy with Git

### Option D: AWS/GCP/Azure
- Deploy as a containerized application using Docker
- Or use managed services like Elastic Beanstalk, Cloud Run, or App Service

## 🔐 CORS Configuration

Make sure your Spring Boot backend allows requests from your Vercel domain:

Update `src/main/java/com/chatweave/chatbot/controller/ChatController.java`:

```java
@CrossOrigin(origins = {"http://localhost:3000", "https://your-vercel-app.vercel.app"})
```

Or for all origins during development (not recommended for production):

```java
@CrossOrigin(origins = "*")
```

## 🐛 Troubleshooting

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
1. Update `@CrossOrigin` annotations in your Spring Boot controllers
2. Add your Vercel domain to allowed origins
3. Redeploy backend

### Environment Variables Not Working

**Cause**: Environment variables not set in Vercel

**Solution**:
1. Go to Vercel Dashboard → Your Project → Settings → Environment Variables
2. Add `NEXT_PUBLIC_API_URL` with your backend URL
3. Redeploy (important: env vars only apply to new builds)

### Build Fails - Module Not Found

**Cause**: Dependencies not installed correctly

**Solution**:
1. Make sure `Root Directory` is set to `frontend`
2. Check that `package.json` is in the frontend directory
3. Redeploy

### Blank Page After Deploy

**Cause**: Usually JavaScript errors or wrong API URL

**Solution**:
1. Check browser console for errors
2. Verify `NEXT_PUBLIC_API_URL` is set correctly
3. Make sure backend is running and accessible
4. Check Network tab in browser DevTools

## 📊 Production Checklist

Before going live:

- [ ] Backend is deployed and running
- [ ] CORS is configured correctly
- [ ] Environment variables are set in Vercel
- [ ] API keys are secured (not in code)
- [ ] Root Directory is set to `frontend` in Vercel
- [ ] Custom domain configured (optional)
- [ ] HTTPS is enabled (automatic with Vercel)
- [ ] Error tracking configured (optional: Sentry, LogRocket)
- [ ] Analytics configured (optional: Vercel Analytics, Google Analytics)

## 🎨 Custom Domain

To add a custom domain:

1. Go to Vercel Dashboard → Your Project → Settings → Domains
2. Add your domain
3. Follow DNS configuration instructions
4. Wait for DNS propagation (usually a few minutes)

## 📈 Monitoring

Vercel provides built-in analytics:

- **Vercel Analytics**: Automatic page views and Web Vitals
- **Vercel Logs**: Real-time function logs
- **Deployments**: Track all deployments and rollback if needed

## 🔄 Automatic Deployments

Vercel automatically deploys:

- **Production**: When you push to `main` branch
- **Preview**: For every pull request
- **Development**: Can be configured for other branches

## 💰 Pricing

- **Hobby Plan**: Free for personal projects
- **Pro Plan**: $20/month for production apps
- **Enterprise**: Custom pricing

The frontend should work fine on the Free Hobby plan for most use cases.

## 📚 Additional Resources

- [Vercel Documentation](https://vercel.com/docs)
- [Next.js Deployment](https://nextjs.org/docs/deployment)
- [Vercel Environment Variables](https://vercel.com/docs/concepts/projects/environment-variables)
- [Monorepo Support](https://vercel.com/docs/monorepos)

## 🆘 Still Getting 404?

If you're still getting 404 after following this guide:

1. **Double-check Root Directory setting in Vercel** (most common issue)
2. Ensure `frontend/` directory exists in your repository
3. Verify `frontend/package.json` exists
4. Check Vercel build logs for specific errors
5. Try deploying from the Vercel CLI with `--debug` flag

---

**Need help?** Open an issue in the GitHub repository with:
- Link to your Vercel deployment
- Build logs from Vercel
- Error messages you're seeing
