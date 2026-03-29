# Install psql on Windows

Use this if you need to run one-off SQL (e.g. `ALTER TABLE`) against your Render PostgreSQL from your PC.

---

## 1. Verify psql is not installed

In PowerShell run:

```powershell
Get-Command psql -ErrorAction SilentlyContinue
```

- If you see nothing (or "psql not found"), psql is **not** on your system.
- If you see a path like `C:\Program Files\PostgreSQL\16\bin\psql.exe`, it is installed; use that path or add that `bin` folder to your PATH.

---

## 2. Install PostgreSQL (includes psql)

1. **Download** the Windows installer:  
   https://www.postgresql.org/download/windows/  
   → Click "Download the installer" → pick the latest (e.g. 17.x).

2. **Run the installer** and:
   - Use default port **5432** (or note the one you pick).
   - Set a **password for the postgres user** (only for the local PostgreSQL; you can forget it if you only need `psql` for Render).
   - In the component list, ensure **"Command Line Tools"** is selected (this installs `psql`).

3. **Finish** the installer. Optionally add PostgreSQL `bin` to your PATH when prompted.

4. **Restart your terminal** (or open a new PowerShell window).

---

## 3. Verify psql is installed

```powershell
psql --version
```

You should see something like `psql (PostgreSQL) 17.x`.

If you see "psql is not recognized", add the `bin` folder to PATH manually:

- Typical path: `C:\Program Files\PostgreSQL\17\bin` (replace `17` with your version).
- **Settings → System → About → Advanced system settings → Environment Variables** → under "Path" add that folder, then restart the terminal.

---

## 4. Run psql against Render (one-off ALTER)

Use your **external** connection string from Render (Dashboard → PostgreSQL → Connect). In PowerShell:

```powershell
psql "postgresql://USER:PASSWORD@HOST/DATABASE?sslmode=require"
```

Then in the `psql` prompt:

```sql
ALTER TABLE subscriptions ALTER COLUMN stripe_customer_id DROP NOT NULL;
\q
```

To run the command in one go (no interactive prompt):

```powershell
psql "postgresql://USER:PASSWORD@HOST/DATABASE?sslmode=require" -c "ALTER TABLE subscriptions ALTER COLUMN stripe_customer_id DROP NOT NULL;"
```

Replace `USER`, `PASSWORD`, `HOST`, and `DATABASE` with the values from Render’s connection string.
