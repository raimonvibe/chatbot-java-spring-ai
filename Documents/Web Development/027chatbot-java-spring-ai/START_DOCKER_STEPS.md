# Start Docker - Post-Installation Steps

Docker is installed but not yet available in the command line. Follow these steps:

---

## ✅ Step 1: Start Docker Desktop

1. **Open Start Menu**
2. **Search for** "Docker Desktop"
3. **Click** to start Docker Desktop
4. **Wait** for Docker to fully start (you'll see a whale icon in the system tray)
5. **The whale icon will stop animating** when Docker is ready (this can take 1-2 minutes)

**Important**: Docker Desktop must be running before you can use docker commands!

---

## ✅ Step 2: Restart Your Terminal/IDE

After starting Docker Desktop:

1. **Close** all open Command Prompt / PowerShell / Terminal windows
2. **Close** your IDE (if open)
3. **Reopen** a fresh terminal or IDE

This ensures the Docker PATH is loaded.

---

## ✅ Step 3: Verify Docker is Working

Open a **new** PowerShell or Command Prompt window and run:

```powershell
docker --version
docker compose version
```

You should see:
```
Docker version 24.x.x, build xxxxxxx
Docker Compose version v2.x.x
```

If you see "docker: command not found" or similar:
- Make sure Docker Desktop is running (whale icon in tray)
- Restart your terminal again
- Try logging out and logging back into Windows

---

## ✅ Step 4: Start the Database

Once docker commands work, you have 3 options:

### Option A: Double-Click Script (Easiest)
Just **double-click** this file:
```
start-database.bat
```

### Option B: PowerShell/Command Prompt
```powershell
cd "C:\Users\rober\Documents\Web Development\027chatbot-java-spring-ai"
docker compose -f docker-compose.dev.yml up -d
```

### Option C: Docker Desktop GUI
1. Open Docker Desktop
2. Click "Containers" in left sidebar
3. You should see "chatbot-postgres-dev" (if not, use Option A or B)

---

## ✅ Step 5: Verify Database Started

Run in PowerShell/Command Prompt:
```powershell
docker compose -f docker-compose.dev.yml ps
```

Expected output:
```
NAME                    STATUS
chatbot-postgres-dev    Up X seconds (healthy)
```

OR **double-click**: `verify-database.bat`

---

## ✅ Step 6: Start Spring Boot Application

```powershell
cd "C:\Users\rober\Documents\Web Development\027chatbot-java-spring-ai\backend"
mvn spring-boot:run -DskipTests
```

Look for:
```
✅ CohereEmbeddingModel created successfully!
✅ PgVectorStore created successfully!
Started AiChatbotApplication in X.XXX seconds
```

---

## 🎯 Quick Checklist

- [ ] Docker Desktop is running (whale icon in system tray)
- [ ] Restarted terminal after starting Docker Desktop
- [ ] `docker --version` works in new terminal
- [ ] `docker compose -f docker-compose.dev.yml up -d` succeeded
- [ ] Database shows "healthy" status
- [ ] Spring Boot application starts without errors
- [ ] Logs show "✅ PgVectorStore created successfully!"

---

## ⚠️ Troubleshooting

### "docker: command not found" - Even After Restart

**Solution 1**: Add Docker to PATH manually
1. Right-click "This PC" → Properties → Advanced System Settings
2. Environment Variables
3. System Variables → Path → Edit
4. Add: `C:\Program Files\Docker\Docker\resources\bin`
5. Restart terminal

**Solution 2**: Use full path
```powershell
& "C:\Program Files\Docker\Docker\resources\bin\docker.exe" compose -f docker-compose.dev.yml up -d
```

### Docker Desktop Won't Start

1. Restart your computer
2. Run Docker Desktop as Administrator
3. Check if virtualization is enabled in BIOS
4. Check Windows Features: Hyper-V and WSL 2 must be enabled

### "Cannot connect to the Docker daemon"

**Issue**: Docker Desktop is not running

**Solution**:
- Start Docker Desktop application
- Wait for whale icon to appear and stop animating
- Try command again

---

## 🚀 Alternative: Use Docker Desktop GUI

If command line doesn't work, you can use Docker Desktop's GUI:

1. **Open Docker Desktop**
2. **Go to**: Images tab
3. **Search**: `ankane/pgvector`
4. **Pull** the image
5. **Create container**:
   - Name: `chatbot-postgres-dev`
   - Port: `5432:5432`
   - Environment variables:
     - `POSTGRES_DB=chatbot_db`
     - `POSTGRES_USER=postgres`
     - `POSTGRES_PASSWORD=postgres`
6. **Start** the container

---

## 📞 Still Having Issues?

If Docker still won't work after all troubleshooting:

### Use Cloud Database Instead (No Docker Required)

Edit these files to use Render PostgreSQL:

**`backend/.env`**:
```properties
# Use your production database URL from Render dashboard
DATABASE_URL=jdbc:postgresql://YOUR_RENDER_HOST:5432/YOUR_DATABASE_NAME
DATABASE_USERNAME=YOUR_DATABASE_USER
DATABASE_PASSWORD=YOUR_DATABASE_PASSWORD
```

**`backend/src/main/resources/application-local.yml`**:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://YOUR_RENDER_HOST:5432/YOUR_DATABASE_NAME
    username: YOUR_DATABASE_USER
    password: YOUR_DATABASE_PASSWORD
```

Then just start the application (no database needed):
```bash
cd backend
mvn spring-boot:run -DskipTests
```

---

## ✅ Next: Once Database is Running

After you see "healthy" status for the database container:

1. Start Spring Boot: `cd backend && mvn spring-boot:run -DskipTests`
2. Watch for success logs (EmbeddingModel and VectorStore created)
3. Open browser: http://localhost:8081 or http://localhost:3000
4. Test the application!

---

**Current Status**: Docker installed ✅ | Docker Desktop needs to be started ⏳
