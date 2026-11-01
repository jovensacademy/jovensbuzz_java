# Jovens Buzz — API (Spring Boot 3.3)

REST API for enquiries, admin authentication, content management, email notifications and Google Sheets sync.
The full project overview (architecture, all three projects) is in the
[project README](../frontend-next/docs/README.md).

```powershell
copy .env.example .env      # then edit .env; it is git-ignored and loaded from this folder
.\mvnw.cmd spring-boot:run  # http://localhost:8081 (run from backend/ so .env is found)
.\mvnw.cmd test             # unit + MySQL integration tests (jovens_buzz_test database)
```

## Documentation

- [docs/DATABASE_SETUP.md](docs/DATABASE_SETUP.md) — XAMPP MySQL setup, environment variables, Flyway migrations
- [docs/SMTP_SETUP.md](docs/SMTP_SETUP.md) — Gmail App Password SMTP setup, Microsoft 365
- [docs/GOOGLE_SHEETS_SETUP.md](docs/GOOGLE_SHEETS_SETUP.md) — Google Sheets sync setup
- [docs/API_EXAMPLES.md](docs/API_EXAMPLES.md) — curl and PowerShell requests for every endpoint group

## Scripts

- `scripts/init-database.sql` — creates `jovens_buzz` and `jovens_buzz_test` (optional least-privilege user)
- `scripts/GoogleAppsScript_Enquiries.js` — the Apps Script web app that writes enquiries to the sheet
  (deployed manually from the Apps Script editor; the secret token lives in Script Properties)
