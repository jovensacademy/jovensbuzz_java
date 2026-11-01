# Database Setup — Jovens Buzz (Local XAMPP)

The backend uses **MySQL / MariaDB** (XAMPP's bundled MariaDB works perfectly).
All tables are created and migrated automatically by **Flyway** when the Spring Boot API starts —
you never create tables by hand.

---

## Quick Setup

### Step 1 — Start MySQL in XAMPP

1. Open **XAMPP Control Panel**.
2. Click **Start** next to **MySQL**.  
   The status turns green and the port shows `3306`.

> If MySQL stops immediately, another MySQL service may already be using port 3306. Check `services.msc`
> or run `netstat -ano | findstr :3306` in a terminal, then stop the conflicting service.

### Step 2 — Create the `jovens_buzz` Database

**Option A — phpMyAdmin (easiest)**

1. Click **Admin** next to MySQL in XAMPP Control Panel (or start Apache first and visit http://localhost/phpmyadmin).
2. Click the **Databases** tab.
3. Enter `jovens_buzz`, set collation to `utf8mb4_unicode_ci`, click **Create**.

**Option B — SQL script (recommended, also creates the test database)**

```powershell
C:\xampp\mysql\bin\mysql.exe -u root < "e:\jovens academy project\jovens buzz\jovens buzz website\backend\scripts\init-database.sql"
```

**Option C — one-liner**

```powershell
C:\xampp\mysql\bin\mysql.exe -u root -e "CREATE DATABASE IF NOT EXISTS jovens_buzz CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### Step 3 — Configure `backend/.env`

```env
DB_HOST=localhost
DB_PORT=3306
DB_NAME=jovens_buzz

# XAMPP default: user is "root" with an EMPTY password.
# If you set a password in phpMyAdmin, put it here.
DB_USERNAME=root
DB_PASSWORD=

DB_USE_SSL=false
DB_ALLOW_PUBLIC_KEY_RETRIEVAL=true
DB_TIMEZONE=UTC
DB_CHARACTER_ENCODING=UTF-8
```

> The JDBC URL is assembled from these variables automatically in `application.properties`.
> You do not need to set a `DB_URL` variable.

### Step 4 — Start the API (Flyway runs migrations)

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

On first start you should see lines like:

```
Successfully applied X migrations to schema `jovens_buzz` (execution time ...)
Started JovensBuzzApplication in ... seconds
```

### Step 5 — Verify the Tables

```powershell
C:\xampp\mysql\bin\mysql.exe -u root jovens_buzz -e "SHOW TABLES;"
```

Expected output (order may vary):

```
admin_users
audit_logs
email_jobs
enquiries
enquiry_notes
enquiry_status_history
faqs
flyway_schema_history
integration_sync_jobs
services
site_content
```

---

## Environment Variables Reference

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `localhost` | MySQL server hostname |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `jovens_buzz` | Database name |
| `DB_USERNAME` | `root` | MySQL user |
| `DB_PASSWORD` | *(empty)* | MySQL password |
| `DB_USE_SSL` | `false` | Enable TLS to the DB (set `true` in production) |
| `DB_ALLOW_PUBLIC_KEY_RETRIEVAL` | `true` | Required for MySQL 8 without SSL locally |
| `DB_TIMEZONE` | `UTC` | Server timezone for JDBC (keep UTC) |
| `DB_CHARACTER_ENCODING` | `UTF-8` | Character encoding for the connection |

---

## Keeping Data Safe

- **Never run `DROP DATABASE jovens_buzz`** on a database with real enquiries.
- **Never edit migration files** in `backend/src/main/resources/db/migration/` after they have been applied;  
  add new `V{n}__description.sql` files for schema changes instead.
- To reset a **local development database only**: drop and recreate `jovens_buzz`, then restart the API (Flyway re-applies all migrations).

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Communications link failure` / `Connection refused` | MySQL isn't running. Start it in XAMPP Control Panel. |
| `Access denied for user 'root'@'localhost'` | Wrong `DB_USERNAME`/`DB_PASSWORD`. XAMPP default is `root` with an empty password. |
| `Unknown database 'jovens_buzz'` | Create it (Step 2 above). |
| `Validate failed: Migrations have failed validation` | A migration file was edited after being applied. Revert the edit and add a new migration. |
| `Schema-validation: missing column …` | The DB is behind the code. Restart the API; Flyway applies pending migrations. |
| Backend tests fail connecting to MySQL | Tests use `jovens_buzz_test`. Run `init-database.sql` (Option B above) to create it. |
