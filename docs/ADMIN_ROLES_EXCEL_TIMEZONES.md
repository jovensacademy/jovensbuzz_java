# Admin roles, user management, Excel import/export and time zones

## Roles and permissions

| Permission | What it allows | Owner | Manager default | User (read-only) |
|---|---|---|---|---|
| `ENQUIRIES_VIEW` | List and open enquiries, assignee picker | ✓ | ✓ | ✓ default |
| `ENQUIRIES_MANAGE` | Status, notes, assignment, sync/email retry | ✓ | ✓ | never |
| `ENQUIRIES_IMPORT` | Excel import (template, preview, validate, commit) | ✓ | ✓ | never |
| `ENQUIRIES_EXPORT` | Excel and CSV export | ✓ | ✓ | optional |
| `ENQUIRIES_DELETE` | Delete (soft) enquiries, view Deleted enquiries (permanent deletion is owner-only) | ✓ | – (grant explicitly) | never |
| `ANALYTICS_VIEW` | Overview dashboard and analytics | ✓ | ✓ | ✓ default |
| `CONTENT_MANAGE` | Services and FAQs | ✓ | – | never |
| `SETTINGS_VIEW` | Integration status, retry failed syncs | ✓ | – | never |
| `AUDIT_VIEW` | Audit log | ✓ | – | never |
| `USERS_MANAGE` | Admin users, roles, permissions (owner only, never assignable) | ✓ | never | never |

- **OWNER** (Super Admin) holds every permission. **MANAGER** holds only what an owner ticks in **Users**.
- **USER** is read-only (`ROLE_USER`): at most `ENQUIRIES_VIEW`, `ANALYTICS_VIEW` and `ENQUIRIES_EXPORT`
  (defaults: view + analytics). Granting anything else to a User is rejected (400), and the cap is applied again when
  permissions are evaluated, so a stored write permission can never take effect. No migration: `role` is a text column.
- Enforced on the API in `SecurityConfig` (first match wins; any new `/api/v1/admin/**` path defaults to owner-only).
  Authorities are re-read from the database on every request, so permission changes apply immediately.
- Migration `V9__admin_roles_permissions_timezone.sql` turns existing `ROLE_ADMIN` accounts into owners
  (time zone `America/Chicago`) and adds `permissions`, `timezone`, `deleted_at`, `deleted_by`.

## User management safety rules (`/api/v1/admin/admin-users`, owners only)

- Nobody can disable, delete or change the role of their own account.
- The last active owner can never be disabled, deleted or demoted (owner rows are locked while checking).
- Disable, delete and password reset bump the account's token version: every existing session ends at once.
- Deletion is soft: the row, audit history, notes and assignments are kept; the username stays reserved.
- Owners set every password (initial and new). There is no temporary password and no forced change at sign-in.

| Method | Path | Purpose |
|---|---|---|
| GET | `/admin/admin-users` | List accounts (not deleted) |
| GET | `/admin/admin-users/options` | Roles, assignable permissions, manager defaults, time zones |
| POST | `/admin/admin-users` | Create (username, displayName, email, role, permissions, timezone, initialPassword) |
| PUT | `/admin/admin-users/{id}` | Update details, role, permissions, time zone |
| PATCH | `/admin/admin-users/{id}/status` | `{ "enabled": false }` disables and revokes sessions |
| POST | `/admin/admin-users/{id}/reset-password` | `{ "newPassword": "..." }` (owner, any account incl. own); revokes sessions |
| DELETE | `/admin/admin-users/{id}` | Soft delete; revokes sessions |
| GET | `/auth/timezones` | Time zone options for any signed-in user |
| PUT | `/auth/me` | Now also accepts `timezone` (IANA) for the signed-in user |

## Authentication and passwords

Sign-in always uses `admin_users` (username + BCrypt hash). `BOOTSTRAP_ADMIN_*` is read **only** when the table is
empty, to create the first owner; it never overwrites an existing account, and the API starts normally without these
variables once an owner exists. After the first owner has signed in, remove
`BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` from the production environment.
Lockout (5 failures → 15 min), per-IP rate limiting and audit entries for sign-in and account changes are unchanged.

- **Passwords are never retrievable.** Only BCrypt hashes are stored; no API, DTO or screen returns a password or hash.
- **Owners manage all passwords.** An owner chooses the initial password when creating an account and can set a new
  password for any account, including their own (`POST /admin/admin-users/{id}/reset-password`). The password is
  shown to the owner once in User Management with a Copy button and is then gone; share it securely. Setting a
  password revokes every session of that account and is audited; User Management shows when it was last set.
- **No self-service changes.** `POST /auth/change-password` and the `/admin/change-password` page were removed (404).
  Accounts sign in and go straight to the dashboard. Managers get 403 on every user-management API.
- The legacy `must_change_password` column (V3) is kept in the schema but is no longer read or written.
- Request fields `initialPassword` / `newPassword` also accept the old name `temporaryPassword`.

## Enquiry deletion — soft delete, then owner-only permanent delete

| Method | Path | Who | Purpose |
|---|---|---|---|
| DELETE | `/admin/enquiries/{id}` | `PERM_ENQUIRIES_DELETE` | Soft delete: moves the enquiry to Deleted enquiries (audited `DELETE_ENQUIRY`) |
| GET | `/admin/enquiries/deleted` | `PERM_ENQUIRIES_DELETE` | Deleted enquiries (latest 500) |
| DELETE | `/admin/enquiries/deleted/{id}` | **Owner only** (`ROLE_OWNER`) | Permanent delete of a deleted enquiry (audited `PERMANENT_DELETE_ENQUIRY`); cannot be undone |

**There is no restore.** `POST /admin/enquiries/{id}/restore` was removed (404); deleted enquiries can only be deleted
permanently. Managers get 403 on permanent deletion even when they hold `PERM_ENQUIRIES_DELETE` (checked in
`SecurityConfig` and again with `@PreAuthorize`); unauthenticated requests get 401.

Soft delete (`V10__enquiry_soft_delete.sql`: `deleted_at`/`deleted_by`): deleted enquiries are excluded from every list,
search, dashboard figure, analytics chart, Excel/CSV export and duplicate check (an `@SQLRestriction` on the entity);
their history, notes, sync and email jobs are kept while they sit in Deleted enquiries, and their references stay reserved.

Permanent delete: only an enquiry that is already soft-deleted can be purged (an active one returns 404). In one
transaction the row is locked, the audit entry is written (reference, contact name, when and by whom it was
soft-deleted; no email), then the row is deleted. Its notes, status history and email/sync job rows go with it
(`ON DELETE CASCADE`, no migration needed); earlier audit entries for it are kept. Google Sheets rows are never
changed or deleted. Bulk deletion is intentionally not offered.

## Excel export — `GET /admin/enquiries/export/excel`

Same parameters as the list (`search`, `status`, `service`, `assignedAdminId`, `startDate`, `endDate`, `sortBy`,
`sortDir`). Every matching record is exported (not just one page), in the list's sort order, up to 100,000 rows.
The workbook lists the applied filters, sort, total count, generation time and time zone; the header row is frozen,
filtered, styled and bordered; dates and times are real Excel values in the exporting user's time zone.
The response header `X-Export-Count` carries the number of rows. CSV export (`/export`) is unchanged.

## Excel import — `/admin/enquiries/import/*`

1. `GET template` — workbook with the columns, example row, dropdowns and an Instructions sheet.
2. `POST preview` (multipart `file`, `.xlsx` or `.xls`, ≤ 5 MB, ≤ 5,000 rows) — validates every row; saves nothing.
3. `POST validate` — re-checks rows corrected in the browser; saves nothing.
4. `POST commit` — re-validates, then inserts all rows in **one transaction** (all or nothing). Rows still invalid or
   duplicate are rejected with their errors and nothing is saved.

Validation: required names, email, phone valid for the chosen country (or `+` international), known service, status,
`Submitted At` (`yyyy-MM-dd HH:mm` or `M/d/yyyy h:mm AM/PM`) read in the importing user's time zone and stored in UTC,
never in the future. Duplicates: same email + service + message as an existing enquiry or an earlier row in the file.
Imported enquiries use source `ADMIN_IMPORT`, send **no emails**, and go to Google Sheets only when requested.

## Time zones

- Dashboard endpoints (`/admin/dashboard/*`) take an optional `tz`; when it is omitted they use the signed-in
  account's own zone (before: UTC). The dashboard pages rely on this so they can load data in parallel with the
  profile request instead of after it.

- Timestamps are stored in UTC. Each admin has an IANA zone (e.g. `Asia/Kolkata` for India, `America/Chicago` for
  Frisco, TX); fixed offsets (`+05:30`, `GMT-6`) are rejected so daylight saving (CST/CDT) is always correct.
- The dashboard renders every date in the signed-in user's zone with its abbreviation (CDT/CST/IST); "today", date
  filters, charts and Excel exports use the same zone. The server or browser zone is never the source.
- Email notifications go to a shared inbox and keep using `BUSINESS_TIMEZONE`.

## Deployment (Docker)

No new infrastructure. The image picks up Apache POI through Maven; Flyway applies V7–V10 on start.

```bash
cd /opt/jovens-buzz/backend
docker exec jovens-buzz-mysql sh -c 'exec mysqldump --single-transaction -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  | gzip > ~/backups/jovens_buzz-before-v10-$(date +%F).sql.gz      # back up first
git pull && docker compose --env-file .env.docker up -d --build backend
docker compose --env-file .env.docker logs --tail 100 backend | grep -E "Migrating|Successfully applied|Started"
```

Then rebuild/restart the Next.js frontend, sign in as the owner, set each user's time zone, create the India manager
in **Users**, and remove the bootstrap credentials from `.env.docker` once the owner password has been changed.
