# SMTP Email Setup — Jovens Buzz

Email notifications for enquiries (admin alert + client confirmation) are sent via SMTP.
The application works without SMTP — enquiries still save to MySQL, and their email jobs wait
as **Pending** until SMTP is configured.

---

## Quick Overview

| Variable | Purpose |
|---|---|
| `SMTP_ENABLED` | Master switch. Set `true` to activate sending. |
| `SMTP_HOST` | Mail server hostname |
| `SMTP_PORT` | SMTP port (587 for STARTTLS) |
| `SMTP_USERNAME` | Full mailbox address used to authenticate |
| `SMTP_PASSWORD` | App Password (Gmail) or account password |
| `SMTP_FROM_EMAIL` | Address shown in the **From** field |
| `SMTP_FROM_NAME` | Display name in the **From** field |
| `ADMIN_NOTIFICATION_EMAIL` | Receives "New Enquiry Received" for every submission |
| `CLIENT_SUPPORT_EMAIL` | Optional Reply-To on client thank-you emails |
| `CLIENT_RESPONSE_TIME` | Response commitment quoted in client email (default: `24 hours`) |
| `PUBLIC_SITE_URL` | Website URL linked in emails |
| `ADMIN_DASHBOARD_URL` | Admin dashboard URL in the admin notification email |

---

## Gmail / Google Workspace Setup

> **Important:** Gmail and Google Workspace **require an App Password** for SMTP.
> Using your normal Google password will fail with error `535 5.7.8 Username and Password rejected`.

### Step 1 — Enable 2-Step Verification

1. Go to **Google Account** → **Security**: https://myaccount.google.com/security
2. Under **"How you sign in to Google"**, click **2-Step Verification**.
3. Follow the prompts to enable it.  
   *(App Passwords are only available after this is turned on.)*

### Step 2 — Generate an App Password

1. From the same Security page, click **App Passwords** (appears once 2SV is enabled).  
   Direct link: https://myaccount.google.com/apppasswords
2. Under **"Select app"**, choose **Mail**.
3. Under **"Select device"**, choose **Other (custom name)** and type e.g. `Jovens Buzz`.
4. Click **Generate**.
5. Copy the 16-character password (shown once — store it somewhere safe before closing the dialog).

### Step 3 — Update `backend/.env`

```env
SMTP_ENABLED=true

SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=you@gmail.com
SMTP_PASSWORD=abcd efgh ijkl mnop   # 16-character App Password (spaces are ignored)

SMTP_AUTH=true
SMTP_STARTTLS_ENABLE=true
SMTP_STARTTLS_REQUIRED=true

SMTP_FROM_EMAIL=you@gmail.com       # must match SMTP_USERNAME or a verified "Send mail as" alias
SMTP_FROM_NAME=Jovens Buzz

ADMIN_NOTIFICATION_EMAIL=team@yourdomain.com
CLIENT_SUPPORT_EMAIL=hello@yourdomain.com   # optional

CLIENT_RESPONSE_TIME=24 hours
PUBLIC_SITE_URL=https://jovensbuzz.com
ADMIN_DASHBOARD_URL=https://jovensbuzz.com/admin
```

### Step 4 — Restart the API

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

The worker polls every 60 seconds. Any **Pending** email jobs send automatically within a minute.

### Step 5 — Verify

Submit a test enquiry on the contact form. Check:
- `ADMIN_NOTIFICATION_EMAIL` inbox for **"New Enquiry Received - Jovens Buzz"**.
- Submitter's inbox for **"Thank You for Contacting Jovens Buzz!"**.
- The enquiry detail page in the admin dashboard shows email status as **Sent**.

---

## Microsoft 365 / Outlook

```env
SMTP_HOST=smtp.office365.com
SMTP_PORT=587
SMTP_USERNAME=you@yourdomain.com
SMTP_PASSWORD=your-account-password   # or App Password if MFA enforces Modern Auth
SMTP_FROM_EMAIL=you@yourdomain.com
```

SMTP AUTH must be enabled for the mailbox in the Microsoft 365 admin centre
(**Admin** → **Users** → **Active users** → select the mailbox → **Mail** tab → **Manage email apps** → enable **Authenticated SMTP**).

---

## Disabling SMTP

Set `SMTP_ENABLED=false` and restart the API. Enquiries continue saving normally.
Existing **Pending** jobs remain pending until SMTP is re-enabled; no data is lost.

---

## Troubleshooting

| Symptom | Cause / Fix |
|---|---|
| `535 5.7.8 Username and Password rejected` | Wrong SMTP password. Gmail requires an **App Password**, not the normal Google password. |
| `553/550 sender not allowed` | `SMTP_FROM_EMAIL` must be the authenticated mailbox or a verified "Send mail as" alias at your provider. |
| Connection timeout to smtp.gmail.com:587 | Port 587 is blocked by your firewall or ISP. Raise the issue with your ISP or try your provider's alternative (Gmail does not support port 465 with STARTTLS). |
| `AuthenticationFailedException` | Ensure 2-Step Verification is on and the App Password is newly generated; old passwords are revoked when you change your Google password. |
| Emails stuck as **Pending** | Check that `SMTP_ENABLED=true`, `SMTP_HOST`, `SMTP_FROM_EMAIL` and credentials are all set. The worker retries every minute; email errors appear on the enquiry detail page. |
| Admin email pending with "ADMIN_NOTIFICATION_EMAIL is not configured" | Set `ADMIN_NOTIFICATION_EMAIL` in `backend/.env` and restart. Click **Retry unsent emails** on the enquiry page. |

---

> **Security reminder:** Never commit `backend/.env` or any file containing SMTP credentials to Git.
> The `.gitignore` already excludes `.env`. Store secrets in `.env` only, or in real environment variables.
