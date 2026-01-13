# ⚠️ Docker Not Installed - Installation Required

Docker Desktop is not currently installed on your system. You'll need to install it before running the database.

---

## 📥 Step 1: Install Docker Desktop

### Download Docker Desktop for Windows

**👉 Download here**: https://www.docker.com/products/docker-desktop

### Installation Steps

1. **Download** the Docker Desktop installer
2. **Run** the installer (requires admin privileges)
3. **Follow** the installation wizard
4. **Restart** your computer if prompted
5. **Start** Docker Desktop from the Start menu
6. **Wait** for Docker to fully start (you'll see a whale icon in the system tray)

### System Requirements

- Windows 10/11 64-bit (Pro, Enterprise, or Education)
- WSL 2 feature enabled (installer will guide you)
- Virtualization enabled in BIOS
- At least 4GB RAM

---

## ✅ Step 2: Verify Docker is Running

After installation, open PowerShell or Command Prompt and run:

```powershell
docker --version
docker compose version
```

You should see something like:
```
Docker version 24.x.x, build xxxxxxx
Docker Compose version v2.x.x
```

---

## 🚀 Step 3: Start the Database

Once Docker is installed and running, you have two options:

### Option A: Use the Batch Script (Easiest)

**Double-click** this file:
```
027chatbot-java-spring-ai/start-database.bat
```

### Option B: Use Command Line

Open PowerShell or Command Prompt in the project directory and run:

```powershell
cd "C:\Users\rober\Documents\Web Development\027chatbot-java-spring-ai"
docker compose -f docker-compose.dev.yml up -d
```

---

## 🔍 Verify Database Started

Run the verification script:

**Double-click**: `verify-database.bat`

OR in command line:
```powershell
docker compose -f docker-compose.dev.yml ps
```

You should see:
```
NAME                    STATUS
chatbot-postgres-dev    Up X seconds (healthy)
```

---

## 🎯 Step 4: Start the Application

Once the database is running:

```powershell
cd backend
mvn spring-boot:run -DskipTests
```

Look for these success messages:
```
✅ CohereEmbeddingModel created successfully!
✅ PgVectorStore created successfully!
Started AiChatbotApplication in X.XXX seconds
```

---

## 🆘 Troubleshooting Docker Installation

### Docker Desktop Won't Install

**Issue**: Installation fails or hangs

**Solutions**:
1. Ensure you're running Windows 10/11 Pro, Enterprise, or Education
2. Enable virtualization in BIOS:
   - Restart computer
   - Enter BIOS (usually F2, F10, or Del key during boot)
   - Find "Virtualization Technology" or "VT-x" and enable it
   - Save and exit
3. Enable WSL 2:
   ```powershell
   # Run as Administrator
   wsl --install
   ```

### Docker Desktop Won't Start

**Issue**: Docker Desktop shows error or won't start

**Solutions**:
1. Restart Docker Desktop
2. Restart your computer
3. Check if Hyper-V is enabled (Windows Features)
4. Run Docker Desktop as Administrator

### "Docker daemon is not running"

**Issue**: Docker commands fail with this error

**Solution**:
- Start Docker Desktop application
- Wait for whale icon to appear in system tray
- Wait until icon stops animating (Docker is fully started)

---

## 🔄 Alternative: Use Cloud Database (Render)

If you can't install Docker, you can use the cloud PostgreSQL database:

### Update Configuration Files

**Edit**: `backend/.env`
```properties
# Use your production database URL from Render dashboard
DATABASE_URL=jdbc:postgresql://YOUR_RENDER_HOST:5432/YOUR_DATABASE_NAME
DATABASE_USERNAME=YOUR_DATABASE_USER
DATABASE_PASSWORD=YOUR_DATABASE_PASSWORD
```

**Edit**: `backend/src/main/resources/application-local.yml`
```yaml
spring:
  datasource:
    url: jdbc:postgresql://YOUR_RENDER_HOST:5432/YOUR_DATABASE_NAME
    username: YOUR_DATABASE_USER
    password: YOUR_DATABASE_PASSWORD
```

**⚠️ Note**: Verify that the Render PostgreSQL database has pgvector extension enabled:

```bash
# Connect to Render database (get URL from Render dashboard)
psql "YOUR_DATABASE_URL"

# Enable pgvector
CREATE EXTENSION IF NOT EXISTS vector;

# Verify
\dx
```

---

## 📚 Next Steps After Docker Installation

1. ✅ Install Docker Desktop
2. ✅ Verify Docker is running
3. ✅ Run `start-database.bat`
4. ✅ Run `verify-database.bat`
5. ✅ Start Spring Boot application
6. ✅ Test vector search functionality

---

## 📖 Additional Resources

- **Docker Desktop Documentation**: https://docs.docker.com/desktop/
- **WSL 2 Setup**: https://docs.microsoft.com/en-us/windows/wsl/install
- **Quick Start Guide**: See `QUICK_START.md`
- **Database Setup**: See `DOCKER_DATABASE_SETUP.md`

---

## ✅ Summary

**Current Status**: ⚠️ Docker not installed

**What you need to do**:
1. Install Docker Desktop from https://www.docker.com/products/docker-desktop
2. Start Docker Desktop
3. Run `start-database.bat`
4. Start Spring Boot application

**Estimated time**: 10-15 minutes (including Docker installation)

---

**Everything else is ready!** Once Docker is installed, the database will start in seconds. All configuration files have been updated and the application code is ready to use the vector store.
