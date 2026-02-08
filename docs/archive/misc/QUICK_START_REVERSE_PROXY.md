# Quick Start: Reverse Proxy Deployment

## 🚀 Start Application (Development)

```bash
# 1. Ensure .env file is configured
cp .env.example .env
nano .env  # Add your API keys

# 2. Start all services with nginx reverse proxy
docker-compose up -d

# 3. Access application
open http://localhost
```

## 🔍 Quick Commands

```bash
# View logs
docker-compose logs -f

# Check status
docker-compose ps

# Restart services
docker-compose restart

# Stop services
docker-compose down

# Stop and clean up
docker-compose down -v
```

## 📊 Service Access

| Service | Internal Port | Public Access | Notes |
|---------|--------------|---------------|-------|
| Nginx | 80 | http://localhost | Main entry point |
| Frontend | 3000 | Through nginx only | Not directly accessible |
| Backend | 8081 (8080 in Docker) | Through nginx only | Not directly accessible |
| Database | 5432 | Internal only | Not accessible from outside |

## 🛣️ URL Routing

| URL Pattern | Destination | Purpose |
|------------|-------------|---------|
| `/` | Frontend | Main application |
| `/api/*` | Backend | REST API endpoints |
| `/chatbot-widget.js` | Backend | Chatbot embed script |
| `/login`, `/oauth2` | Backend | Authentication |
| `/chatbots`, `/analytics` | **BLOCKED** | Thymeleaf pages (dev only) |

## ✅ Health Checks

```bash
# Check nginx
curl http://localhost/health

# Check backend API
curl http://localhost/api/health

# Check all services
docker-compose ps
```

## 🔒 Security Features

✅ Single public entry point (port 80)
✅ Backend/frontend not directly exposed
✅ Rate limiting enabled
✅ Thymeleaf pages blocked from public access
✅ CORS configured
✅ Security headers enabled

## 🐛 Common Issues

**502 Bad Gateway**
```bash
# Check if backend is running
docker-compose logs backend
docker-compose restart backend
```

**Connection Refused**
```bash
# Ensure all services are up
docker-compose ps
docker-compose up -d
```

**CORS Errors**
```bash
# Update CORS_ALLOWED_ORIGINS in .env
CORS_ALLOWED_ORIGINS=http://localhost
docker-compose restart backend
```

## 📚 Full Documentation

See [REVERSE_PROXY_SETUP.md](./REVERSE_PROXY_SETUP.md) for complete setup guide.
