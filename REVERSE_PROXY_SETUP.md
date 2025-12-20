# Reverse Proxy Setup Guide

This guide explains how to deploy the TjanaBot AI Chatbot with an nginx reverse proxy for enhanced security and better architecture.

## 🎯 Architecture Overview

```
                                    ┌─────────────────┐
                                    │   Internet      │
                                    └────────┬────────┘
                                             │
                                    ┌────────▼────────┐
                                    │  Nginx Proxy    │
                                    │   (Port 80)     │
                                    └────────┬────────┘
                                             │
                        ┌────────────────────┼────────────────────┐
                        │                    │                    │
                ┌───────▼────────┐  ┌────────▼────────┐  ┌──────▼──────┐
                │   Frontend     │  │    Backend      │  │  Database   │
                │  Next.js:3000  │  │  Spring:8081    │  │ Postgres    │
                │                │  │  (8080 in Docker)│ │             │
                └────────────────┘  └─────────────────┘  └─────────────┘
```

### Key Benefits

✅ **Single Entry Point**: All traffic goes through nginx on port 80
✅ **Security**: Backend and frontend not directly exposed
✅ **Rate Limiting**: Built-in protection against abuse
✅ **Static Caching**: Improved performance for static assets
✅ **Flexible Routing**: Easy to add/modify routes without changing code
✅ **Thymeleaf Protection**: Server-rendered pages blocked from public access

### Traffic Flow

1. **Frontend Routes** (`/`, `/login`, `/dashboard`, etc.)
   - Proxied to Next.js frontend (port 3000)

2. **API Routes** (`/api/*`)
   - Proxied to Spring Boot backend (port 8081 for local dev, 8080 for Docker)
   - Rate limited: 10 requests/second with burst of 20

3. **Authentication** (`/login`, `/oauth2`, `/logout`)
   - Proxied to Spring Boot backend

4. **Chatbot Widget** (`/chatbot-widget.js`, `/css/chatbot-widget.css`)
   - Proxied to Spring Boot backend
   - Cached for 1 hour

5. **Thymeleaf Pages** (BLOCKED)
   - Direct access to `/chatbots`, `/analytics`, `/settings` returns 404
   - Available only for development/internal testing

---

## 🚀 Deployment Options

### Option 1: Docker Compose (Recommended for Development & Small Production)

#### Prerequisites
- Docker and Docker Compose installed
- `.env` file configured with API keys

#### Step 1: Configure Environment

Ensure your `.env` file has all required variables:

```bash
# AI API Keys
ANTHROPIC_API_KEY=your_anthropic_key
COHERE_API_KEY=your_cohere_key

# Security
JWT_SECRET=your_jwt_secret_min_32_chars
JWT_EXPIRATION=86400000

# CORS (for nginx setup, use your domain)
CORS_ALLOWED_ORIGINS=http://localhost,https://yourdomain.com

# Database (for production, use PostgreSQL)
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/chatbot
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_secure_password

# Optional: OAuth & Stripe
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
STRIPE_SECRET_KEY=your_stripe_key
STRIPE_WEBHOOK_SECRET=your_webhook_secret
```

#### Step 2: Start Services

```bash
# Start all services (postgres, backend, frontend, nginx)
docker-compose up -d

# View logs
docker-compose logs -f

# Check service status
docker-compose ps
```

#### Step 3: Access Application

- **Main Application**: http://localhost
- **Health Check**: http://localhost/health
- **API Endpoint Example**: http://localhost/api/chatbots

#### Step 4: Stop Services

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

---

### Option 2: Production Deployment with Cloud Provider

For production deployment on cloud platforms (AWS, DigitalOcean, Azure, etc.), you have two approaches:

#### Approach A: Deploy Entire Stack with Docker Compose

This is the simplest approach for smaller deployments.

**Step 1: Provision a Server**
```bash
# Minimum requirements:
# - 2 CPU cores
# - 4GB RAM
# - 20GB storage
# - Ubuntu 22.04 LTS or similar
```

**Step 2: Install Docker**
```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Install Docker Compose
sudo apt install docker-compose-plugin -y

# Add user to docker group
sudo usermod -aG docker $USER
newgrp docker
```

**Step 3: Clone and Configure**
```bash
# Clone your repository
git clone https://github.com/yourusername/chatbot-java-spring-ai.git
cd chatbot-java-spring-ai

# Create and configure .env file
nano .env
# Add all required environment variables

# Update CORS_ALLOWED_ORIGINS for your domain
# CORS_ALLOWED_ORIGINS=https://yourdomain.com
```

**Step 4: Deploy**
```bash
# Start services
docker-compose up -d

# Monitor logs
docker-compose logs -f
```

**Step 5: Configure Firewall**
```bash
# Allow HTTP traffic
sudo ufw allow 80/tcp

# Allow HTTPS (if configuring SSL)
sudo ufw allow 443/tcp

# Enable firewall
sudo ufw enable
```

**Step 6: Set Up Domain & SSL (Optional but Recommended)**

See "SSL/HTTPS Configuration" section below.

---

#### Approach B: Separate Service Deployment (Scalable)

For larger deployments, deploy each service separately:

**Backend**: Deploy to Render, Railway, or AWS Elastic Beanstalk
**Frontend**: Deploy to Vercel, Netlify, or AWS Amplify
**Database**: Use managed PostgreSQL (AWS RDS, DigitalOcean Managed DB)
**Nginx**: Deploy on separate server or use cloud load balancer

This approach requires:
1. Deploy backend and note its URL (e.g., `https://api.yourdomain.com`)
2. Deploy frontend with `NEXT_PUBLIC_API_URL=https://api.yourdomain.com`
3. Configure nginx to proxy to these separate services

---

## 🔒 SSL/HTTPS Configuration

### Using Let's Encrypt with Certbot

**Step 1: Install Certbot**
```bash
sudo apt install certbot python3-certbot-nginx -y
```

**Step 2: Stop nginx (if running in Docker)**
```bash
docker-compose stop nginx
```

**Step 3: Obtain Certificate**
```bash
# Replace yourdomain.com with your actual domain
sudo certbot certonly --standalone -d yourdomain.com -d www.yourdomain.com
```

**Step 4: Update nginx.conf**

Uncomment the HTTPS section in `nginx.conf` and update:

```nginx
server {
    listen 443 ssl http2;
    server_name yourdomain.com www.yourdomain.com;

    # SSL Certificates
    ssl_certificate /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;

    # SSL Configuration
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;

    # Security Headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    # Include all location blocks here
    # ... (copy location blocks from HTTP server)
}

# Redirect HTTP to HTTPS
server {
    listen 80;
    server_name yourdomain.com www.yourdomain.com;
    return 301 https://$server_name$request_uri;
}
```

**Step 5: Update docker-compose.yml**

Add SSL certificate volumes to nginx service:

```yaml
nginx:
  image: nginx:alpine
  container_name: chatbot-nginx
  ports:
    - "80:80"
    - "443:443"
  volumes:
    - ./nginx.conf:/etc/nginx/nginx.conf:ro
    - /etc/letsencrypt:/etc/letsencrypt:ro
  # ... rest of config
```

**Step 6: Restart nginx**
```bash
docker-compose up -d nginx
```

**Step 7: Set Up Auto-Renewal**
```bash
# Test renewal
sudo certbot renew --dry-run

# Certbot automatically sets up a cron job for renewal
# Verify it's scheduled
sudo systemctl status certbot.timer
```

---

## 🔧 Configuration Reference

### Nginx Configuration (nginx.conf)

Key configuration sections:

**Rate Limiting**
```nginx
limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;
limit_req_zone $binary_remote_addr zone=general_limit:10m rate=30r/s;
```

**Upstream Servers**
```nginx
upstream backend {
    server backend:8080;
    keepalive 32;
}

upstream frontend {
    server frontend:3000;
    keepalive 32;
}
```

**Location Routing**
- `/api/*` → Backend (rate limited: 10 req/s)
- `/` → Frontend (rate limited: 30 req/s)
- `/chatbot-widget.js` → Backend (cached: 1 hour)
- `/chatbots`, `/analytics`, `/settings` → Blocked (404)

### Docker Compose Configuration

**Networks**: All services use `chatbot-network` for internal communication

**Port Exposure**:
- Nginx: Port 80 exposed to host (public)
- Backend: Port 8080 exposed only to internal network
- Frontend: Port 3000 exposed only to internal network
- Database: Port 5432 exposed only to internal network

---

## 🧪 Testing the Setup

### Test API Access
```bash
# Health check
curl http://localhost/health

# API endpoint
curl http://localhost/api/chatbots

# Should return 404 (Thymeleaf pages blocked)
curl http://localhost/chatbots
```

### Test Rate Limiting
```bash
# Send 30 requests quickly (should see 429 errors after burst limit)
for i in {1..30}; do curl http://localhost/api/health; done
```

### Test Frontend
```bash
# Open in browser
open http://localhost
```

---

## 🐛 Troubleshooting

### Issue: nginx fails to start
**Cause**: Configuration syntax error or port already in use

**Solution**:
```bash
# Test nginx configuration
docker run --rm -v $(pwd)/nginx.conf:/etc/nginx/nginx.conf:ro nginx:alpine nginx -t

# Check if port 80 is in use
sudo lsof -i :80

# View nginx logs
docker-compose logs nginx
```

### Issue: 502 Bad Gateway
**Cause**: Backend or frontend not responding

**Solution**:
```bash
# Check if services are running
docker-compose ps

# Check backend health (use 8081 for local dev, 8080 for Docker)
curl http://localhost:8081/actuator/health

# Check frontend health
curl http://localhost:3000

# Restart services
docker-compose restart backend frontend
```

### Issue: CORS errors in browser console
**Cause**: CORS_ALLOWED_ORIGINS not configured correctly

**Solution**:
```bash
# Update .env file
CORS_ALLOWED_ORIGINS=http://localhost,https://yourdomain.com

# Restart backend
docker-compose restart backend
```

### Issue: API calls returning 404
**Cause**: Frontend configured with wrong API URL

**Solution**:
```bash
# In docker-compose.yml, frontend should use:
NEXT_PUBLIC_API_URL: http://localhost

# In production, use your domain:
NEXT_PUBLIC_API_URL: https://yourdomain.com
```

---

## 📊 Monitoring

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f nginx
docker-compose logs -f backend
docker-compose logs -f frontend
```

### Check Resource Usage
```bash
# Container stats
docker stats

# Disk usage
docker system df
```

---

## 🔄 Updates and Maintenance

### Update Application
```bash
# Pull latest code
git pull

# Rebuild and restart services
docker-compose build
docker-compose up -d

# Clean up old images
docker image prune -a
```

### Database Backup
```bash
# Backup PostgreSQL database
docker exec chatbot-postgres pg_dump -U postgres chatbot > backup.sql

# Restore
docker exec -i chatbot-postgres psql -U postgres chatbot < backup.sql
```

---

## 📋 Production Checklist

- [ ] All environment variables configured in `.env`
- [ ] Strong PostgreSQL password set
- [ ] JWT_SECRET is random and at least 32 characters
- [ ] CORS_ALLOWED_ORIGINS includes production domain
- [ ] SSL/HTTPS configured with Let's Encrypt
- [ ] Firewall configured (only ports 80, 443 open)
- [ ] Database backups scheduled
- [ ] Monitoring/logging configured
- [ ] Docker containers set to restart automatically
- [ ] Domain DNS pointing to server IP
- [ ] OAuth credentials configured for production domain
- [ ] Stripe webhook URL updated in Stripe dashboard

---

## 🆘 Support

If you encounter issues:

1. Check logs: `docker-compose logs -f`
2. Verify configuration: `docker run --rm -v $(pwd)/nginx.conf:/etc/nginx/nginx.conf:ro nginx:alpine nginx -t`
3. Test backend directly: `curl http://localhost:8081/actuator/health` (use 8081 for local dev, 8080 for Docker)
4. Review this documentation
5. Check GitHub issues: https://github.com/yourusername/chatbot-java-spring-ai/issues

---

## 🔗 Additional Resources

- [Nginx Documentation](https://nginx.org/en/docs/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Let's Encrypt Documentation](https://letsencrypt.org/docs/)
- [Spring Boot Production Guide](https://docs.spring.io/spring-boot/docs/current/reference/html/deployment.html)
- [Next.js Deployment](https://nextjs.org/docs/deployment)
