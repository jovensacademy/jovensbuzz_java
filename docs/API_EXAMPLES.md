# API examples

Base URL (local): `http://localhost:8081/api/v1`. Interactive docs: http://localhost:8081/swagger-ui.html. In Swagger,
click **Authorize** and paste the `token` returned by login.

Every response uses the same envelope:

```json
{ "success": true, "message": "Operation successful", "data": { }, "timestamp": "2026-10-30T10:00:00Z" }
```

Validation errors return `400` with `errors: { field: message }`. Unauthenticated requests return `401`, a missing role
returns `403`, a missing record `404`, and rate limiting `429` with a `Retry-After` header.

> The examples use Git Bash or `curl.exe`. PowerShell equivalents follow each group. In PowerShell, call `curl.exe`
> explicitly, because `curl` is an alias for `Invoke-WebRequest`.

## Public

```bash
curl http://localhost:8081/api/v1/health
curl http://localhost:8081/api/v1/services
curl http://localhost:8081/api/v1/faqs
curl http://localhost:8081/api/v1/site-content

# Submit an enquiry (201 Created; an identical resubmission within 15 min returns 200 + the same reference)
curl -X POST http://localhost:8081/api/v1/enquiries \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Ada Lovelace",
    "email": "ada@example.com",
    "phone": "+447400123456",
    "phoneCountry": "GB",
    "companyName": "Analytical Engines Ltd",
    "serviceId": 6,
    "budget": "One-off project",
    "message": "We need a new brand identity for our launch.",
    "privacyConsent": true
  }'
# => {"success":true,"data":{"publicReference":"JB-2026-7KQ4MX","submittedAt":"…","duplicate":false}}
```

Send `serviceId` (a published service from `GET /api/v1/services`) or, for older clients, `serviceInterest` (a published
title, `Not sure yet` or `Other`). `phone` must start with `+` and is validated with libphonenumber against
`phoneCountry` (`US`, `IN`, `CA`, `GB`, `AU`; the US and Canada share +1, so the country decides the rules). It is
stored in E.164. A successful submission also queues the admin and client emails. Limit: 5 submissions per IP per 10 minutes.

```powershell
$body = @{ fullName="Ada Lovelace"; email="ada@example.com"; phone="+447400123456"; phoneCountry="GB";
           serviceId=6; message="We need a new brand identity."; privacyConsent=$true } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/v1/enquiries -ContentType "application/json" -Body $body
```

## Authentication

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"jovens_buzz_2026","password":"Jovensbuzz@2026"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')

curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/v1/auth/me
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/v1/auth/refresh      # new token, fresh expiry
curl -X PUT  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"displayName":"Jovens Admin","email":"admin@jovensbuzz.com"}' http://localhost:8081/api/v1/auth/me
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"currentPassword":"Jovensbuzz@2026","newPassword":"A-New#Strong-Passw0rd"}' \
     http://localhost:8081/api/v1/auth/change-password    # returns a new token; old tokens are revoked
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/v1/auth/logout       # revokes all tokens
```

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/v1/auth/login -ContentType "application/json" `
  -Body '{"username":"jovens_buzz_2026","password":"Jovensbuzz@2026"}'
$h = @{ Authorization = "Bearer $($login.data.token)" }
Invoke-RestMethod -Uri http://localhost:8081/api/v1/auth/me -Headers $h
```

## Dashboard

```bash
H="Authorization: Bearer $TOKEN"
curl -H "$H" "http://localhost:8081/api/v1/admin/dashboard/summary?tz=America/Chicago"
curl -H "$H" "http://localhost:8081/api/v1/admin/dashboard/enquiry-trends?days=30&tz=America/Chicago"
curl -H "$H" "http://localhost:8081/api/v1/admin/dashboard/enquiry-trends?from=2026-10-01&to=2026-10-30"
curl -H "$H" "http://localhost:8081/api/v1/admin/dashboard/service-distribution?from=2026-10-01&to=2026-10-30"
curl -H "$H" "http://localhost:8081/api/v1/admin/dashboard/status-distribution"
```

## Enquiries

```bash
# Search / filter / sort / paginate (page is 0-based; size max 100)
curl -H "$H" "http://localhost:8081/api/v1/admin/enquiries?search=acme&status=NEW&service=Brand%20Identity&startDate=2026-10-01T00:00:00Z&endDate=2026-10-31T23:59:59Z&page=0&size=25&sortBy=createdAt&sortDir=desc"
curl -H "$H" http://localhost:8081/api/v1/admin/enquiries/filter-options
curl -H "$H" http://localhost:8081/api/v1/admin/enquiries/1

# Status (allowed transitions are validated: e.g. NEW -> CONTACTED, QUALIFIED, CLOSED, SPAM)
curl -X PATCH -H "$H" -H "Content-Type: application/json" -d '{"status":"CONTACTED","note":"Intro call booked"}' \
     http://localhost:8081/api/v1/admin/enquiries/1/status

# Notes, assignment, sync
curl -H "$H" http://localhost:8081/api/v1/admin/enquiries/1/notes
curl -X POST  -H "$H" -H "Content-Type: application/json" -d '{"note":"Sent proposal"}' http://localhost:8081/api/v1/admin/enquiries/1/notes
curl -H "$H" http://localhost:8081/api/v1/admin/users
curl -X PATCH -H "$H" -H "Content-Type: application/json" -d '{"adminId":1}'    http://localhost:8081/api/v1/admin/enquiries/1/assignment
curl -X PATCH -H "$H" -H "Content-Type: application/json" -d '{"adminId":null}' http://localhost:8081/api/v1/admin/enquiries/1/assignment
curl -H "$H" http://localhost:8081/api/v1/admin/enquiries/1/sync-status
curl -X POST -H "$H" http://localhost:8081/api/v1/admin/enquiries/1/retry-sync

# Email notifications (admin notification + client confirmation)
curl -H "$H" http://localhost:8081/api/v1/admin/enquiries/1/notifications
curl -X POST -H "$H" http://localhost:8081/api/v1/admin/enquiries/1/notifications/retry   # never re-sends SENT emails
curl -H "$H" http://localhost:8081/api/v1/admin/settings/integrations/email                  # no credentials returned

# CSV export (same filters as the list; max 10,000 rows)
curl -H "$H" -o enquiries.csv "http://localhost:8081/api/v1/admin/enquiries/export?status=QUALIFIED"
```

## Content management

```bash
# Services (CRUD)
curl -H "$H" http://localhost:8081/api/v1/admin/services
curl -X POST -H "$H" -H "Content-Type: application/json" \
  -d '{"title":"SEO Audits","slug":"seo-audits","description":"Technical and content SEO reviews.","icon":"web","displayOrder":9,"published":true}' \
  http://localhost:8081/api/v1/admin/services
curl -X PUT -H "$H" -H "Content-Type: application/json" -d '{ …same shape… }' http://localhost:8081/api/v1/admin/services/9
curl -X DELETE -H "$H" http://localhost:8081/api/v1/admin/services/9

# FAQs (CRUD)
curl -X POST -H "$H" -H "Content-Type: application/json" \
  -d '{"question":"Do you work with startups?","answer":"Yes.","displayOrder":9,"published":true}' \
  http://localhost:8081/api/v1/admin/faqs

# Site content (registered keys only; URLs must be http(s); markup is stripped)
curl -H "$H" http://localhost:8081/api/v1/admin/content
curl -X PUT -H "$H" -H "Content-Type: application/json" -d '{"contentValue":"Making Small Businesses Look Big"}' \
     http://localhost:8081/api/v1/admin/content/hero_headline
curl -X POST -H "$H" -H "Content-Type: application/json" \
     -d '{"contact_email":"hello@jovensbuzz.com","social_instagram":"https://instagram.com/jovensbuzz"}' \
     http://localhost:8081/api/v1/admin/content/batch
```

> Editing content directly against the API updates the database, but the public site picks it up after
> `CONTENT_REVALIDATE_SECONDS`. Edits made through the dashboard revalidate immediately.

## Settings & audit

```bash
curl -H "$H" http://localhost:8081/api/v1/admin/settings/integrations/google-sheets
curl -X POST -H "$H" http://localhost:8081/api/v1/admin/settings/integrations/google-sheets/retry-failed
curl -H "$H" "http://localhost:8081/api/v1/admin/audit-logs?page=0&size=20"
```
