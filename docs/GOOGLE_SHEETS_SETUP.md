# Google Sheets integration

Every enquiry is saved in MySQL first. A background worker then appends the same data to a Google Sheet using a
**service account**, which is a robot Google identity that needs no browser login. If Sheets is unavailable or not yet
configured, enquiries are still saved and the worker retries later.

## 1. Create the spreadsheet

1. Create a new Google Sheet, for example "Jovens Buzz – Enquiries".
2. Rename the first tab to **`Enquiries`**, or use another name and set `GOOGLE_SHEETS_WORKSHEET_NAME` to match.
3. Leave it empty. The API writes this header row on first sync:

   `Enquiry Reference | Submitted At (UTC) | Full Name | Email | Phone | Company | Service Interest | Budget | Message | Status | Source | Sync Status`

4. Copy the **spreadsheet ID** from the URL:
   `https://docs.google.com/spreadsheets/d/`**`1AbC…xyz`**`/edit`

## 2. Create a service account

1. Open https://console.cloud.google.com/ and create (or choose) a project.
2. **APIs & Services → Library** → search **Google Sheets API** → **Enable**.
3. **APIs & Services → Credentials → Create credentials → Service account**. Give it a name like `jovens-buzz-sheets`.
   It needs no project roles.
4. Open the service account → **Keys → Add key → Create new key → JSON**. A `.json` file downloads.
5. Move the file **outside the repository**, for example `C:\secrets\jovens-buzz-sheets.json`. Never commit it; the
   `.gitignore` blocks `*service-account*.json` and `*-credentials.json` as a safety net.

## 3. Share the sheet with the service account

Open the JSON file and copy the `client_email` value (`…@….iam.gserviceaccount.com`). In the spreadsheet click
**Share**, paste that address, choose **Editor**, untick "Notify people", and share. The service account can access
only files shared with it. Editor on this one sheet is the minimum it needs to append rows.

## 4. Configure the API

In `backend/.env` (or real environment variables):

```properties
GOOGLE_SHEETS_SPREADSHEET_ID=1AbC...xyz
GOOGLE_SHEETS_WORKSHEET_NAME=Enquiries
GOOGLE_SERVICE_ACCOUNT_FILE=C:/secrets/jovens-buzz-sheets.json
```

Restart the API. In the dashboard, **Settings → Google Sheets integration** should show **Connected**. It also shows the
service account email to share with (read from the key's public `client_email`; the key itself is never shown).

Enquiries that failed only because Sheets wasn't configured are picked up automatically within about a minute. You
can also click **Retry failed syncs**, or **Retry sync now** on an individual enquiry.

## How synchronisation behaves

| Situation | What happens |
|---|---|
| Enquiry submitted | Enquiry + sync job saved in one transaction; sync starts right after commit |
| Google API error / timeout | Job → `RETRYING`; retried after 1, 2, 4, 8, 16 minutes; `FAILED` after 6 attempts |
| Retry after a timeout that actually succeeded | Column A is checked for the reference first, so **no duplicate row** is written |
| Scheduler and post-submit worker both see a job | The job is claimed atomically; only one processes it |
| API crashed mid-sync | Jobs stuck in `PROCESSING` > 10 min return to the retry queue |
| Not configured | Job → `FAILED` ("not configured"); auto-resumes once configured |

Values are written with `valueInputOption=RAW`, so text such as `=HYPERLINK(...)` in a message is stored as plain
text, never evaluated as a formula.

## Production

Prefer a secret manager or workload identity over a JSON key file on disk, for example Google Cloud Secret Manager or a
Kubernetes secret mounted read-only. Restrict file permissions so only the API's OS user can read the key, and rotate
the key periodically from the service account's **Keys** tab.

## Troubleshooting

| Error in "Last error" | Fix |
|---|---|
| `Google Sheets is not configured …` | Set `GOOGLE_SHEETS_SPREADSHEET_ID` and `GOOGLE_SERVICE_ACCOUNT_FILE`, then restart. Check the path exists and is readable |
| `403 … The caller does not have permission` | Share the sheet with the service account's `client_email` as **Editor** |
| `403 … Google Sheets API has not been used in project …` | Enable the Google Sheets API in that Cloud project and wait a few minutes |
| `400 … Unable to parse range: Enquiries!A:A` | The tab name doesn't match `GOOGLE_SHEETS_WORKSHEET_NAME` (case and spaces matter) |
| `404 … Requested entity was not found` | Wrong spreadsheet ID (copy only the part between `/d/` and `/edit`) |
| `invalid_grant` / `Invalid JWT Signature` | Key revoked or the server clock is wrong. Create a new key and sync the system time |
| `SocketTimeoutException` / `UnknownHostException` | Network or proxy blocks `sheets.googleapis.com`; retries continue automatically |
