# Docker deployment (VPS)

The API runs as the Compose project **`jovens-buzz`**: containers `jovens-buzz-backend` and
`jovens-buzz-mysql`, network `jovens-buzz-network` (172.29.82.0/24), volume `jovens_buzz_mysql_data`.
It listens on **127.0.0.1:8082** and has its own MySQL 8.4 database `jovens_buzz`. Nothing in it uses
port 8081 or touches other stacks (e.g. Jovens Yoga). Every `docker compose` command below is scoped to
this project.

| File | Purpose |
|---|---|
| `Dockerfile` | Multi-stage build: Maven + JDK 17 → JRE 17 Alpine, non-root user, health check |
| `docker-compose.yml` | API + MySQL, restart policies, health checks, loopback-only port |
| `.env.docker.example` | Template for `.env.docker` (git-ignored). `.env` stays the local XAMPP config |
| `.dockerignore` | Keeps secrets, tests and build output out of the image |

## 1. Pre-flight checks (read-only)

```bash
docker --version && docker compose version
sudo ss -ltnp | grep -E ':8082\b' || echo "port 8082 is free"
# The stack uses 172.29.82.0/24; make sure no existing network overlaps it
docker network ls -q | xargs docker network inspect --format '{{.Name}} {{range .IPAM.Config}}{{.Subnet}}{{end}}'
```

If 172.29.82.0/24 is taken, change `subnet`/`gateway` in `docker-compose.yml` and `TRUSTED_PROXIES` in `.env.docker`.

## 2. Configure

```bash
sudo mkdir -p /opt/jovens-buzz && sudo chown "$USER" /opt/jovens-buzz
git clone <your-repo-url> /opt/jovens-buzz        # or: cd /opt/jovens-buzz && git pull
cd /opt/jovens-buzz/backend
cp .env.docker.example .env.docker
chmod 600 .env.docker
openssl rand -base64 48    # JWT_SECRET
openssl rand -base64 33    # DB_PASSWORD, MYSQL_ROOT_PASSWORD (run twice)
nano .env.docker           # replace every CHANGE_ME; set CORS_ALLOWED_ORIGINS / PUBLIC_SITE_URL to your domain
grep -n CHANGE_ME .env.docker || echo "all values set"
```

## 3. Build and start

```bash
cd /opt/jovens-buzz/backend
docker compose --env-file .env.docker config --quiet && echo "compose file OK"
docker compose --env-file .env.docker build
docker compose --env-file .env.docker up -d
docker compose --env-file .env.docker ps            # both services should become "healthy"
docker compose --env-file .env.docker logs -f backend   # wait for "Started JovensBuzzApplication"; Ctrl+C
```

## 4. Verify

```bash
curl -fsS http://127.0.0.1:8082/actuator/health     # {"status":"UP",...}
curl -fsS http://127.0.0.1:8082/api/v1/health
curl -fsS http://127.0.0.1:8082/api/v1/services | head -c 300; echo
docker exec jovens-buzz-mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" \
  -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank"'
docker inspect --format '{{.Config.User}}' jovens-buzz-backend   # jovens (non-root)
```

Then sign in to the admin dashboard with the bootstrap account, change its password, and send one test
enquiry to confirm the Google Sheets row and both emails.

## 5. Reverse proxy (HTTPS)

The API is bound to loopback. Example Nginx location (inside your existing TLS `server` block):

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8082;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

Point the Next.js app at it: `NEXT_PUBLIC_API_BASE_URL=https://<your-domain>` and
`API_INTERNAL_URL=http://127.0.0.1:8082`. Requests from the host reach the container from
172.29.82.1, which `TRUSTED_PROXIES` trusts, so rate limits see the real visitor IP.

## Operations

```bash
cd /opt/jovens-buzz/backend
# Update to a new version (database volume is kept; Flyway applies new migrations on start)
git pull && docker compose --env-file .env.docker up -d --build backend
# Logs / restart
docker compose --env-file .env.docker logs --tail 200 backend
docker compose --env-file .env.docker restart backend
# Nightly backup (add to cron), and restore
mkdir -p ~/backups
docker exec jovens-buzz-mysql sh -c 'exec mysqldump --single-transaction --routines --triggers -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  | gzip > ~/backups/jovens_buzz-$(date +%F).sql.gz
gunzip -c ~/backups/jovens_buzz-YYYY-MM-DD.sql.gz | docker exec -i jovens-buzz-mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
```

Deleted enquiries have no restore in the app, and an owner's permanent delete is final: these backups are the
only way to recover one.

**Never run** `docker compose down -v` (deletes the database volume) or `docker system prune --volumes`
(can delete other stacks' data). `docker compose --env-file .env.docker down` without `-v` is safe.

### Moving existing local data

The container starts with an empty `jovens_buzz` database that Flyway fills. To carry over enquiries
from local XAMPP, export locally and import before first use:

```bash
# local (Windows):  C:\xampp\mysql\bin\mysqldump.exe -u root --single-transaction jovens_buzz > jovens_buzz.sql
# VPS:
docker exec -i jovens-buzz-mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < jovens_buzz.sql
docker compose --env-file .env.docker restart backend
```
