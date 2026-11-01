/**
 * ==============================================================================
 * Jovens Buzz — Google Sheets Sync Web App (Google Apps Script)
 * ==============================================================================
 *
 * Appends every website enquiry to the EXISTING, formatted worksheet "Enquiries"
 * (title banner, header row, banding, filter and column widths are left untouched).
 *
 * - Opens the spreadsheet with SpreadsheetApp.openById(SPREADSHEET_ID).
 * - Never creates, renames, clears or deletes a worksheet and never writes the header
 *   row. If "Enquiries" or its header row is missing, the request fails and the backend
 *   retries later; nothing is written anywhere else (Sheet1 and other tabs are ignored).
 * - Finds the header row by content (the banner row above it is skipped), then appends
 *   directly below the last used row, inside the existing banding/filter range.
 * - Writes cells as plain text so phone numbers and codes keep their "+" exactly.
 * - Validates a shared secret (Script Property SECRET_TOKEN) on every POST.
 * - Deduplicates by enquiry reference (column A) under a script lock, so retries and
 *   concurrent requests never add duplicate rows.
 * - Reads the written row back and reports success only when it matches.
 * - Always answers JSON: { "success": true|false, "message": "...", "row": N }.
 *
 * DEPLOYMENT:
 * 1. Open the spreadsheet "jovens_Buzz_website" -> Extensions -> Apps Script
 *    (or the Apps Script project that owns the existing web app URL).
 * 2. Replace ALL contents of Code.gs with this file and save.
 * 3. Project Settings -> Script Properties: SECRET_TOKEN = <GOOGLE_SHEETS_SECRET_TOKEN in backend/.env>
 *    (Optional) SPREADSHEET_ID to override the default below. The token is NOT stored in this file.
 * 4. Run the function "setup" once (read-only check; it changes nothing) and read the log.
 * 5. Deploy -> Manage deployments -> pencil icon on the ACTIVE web app deployment
 *    -> Version: "New version" -> Execute as: Me, Who has access: Anyone -> Deploy.
 *    (Editing the existing deployment keeps the same /exec URL used in backend/.env.)
 * 6. Open the /exec URL: it must return {"success":true,...,"headerRow":2,"headersValid":true}.
 * ==============================================================================
 */

var SPREADSHEET_ID = "17199Se3TWXse2bfkPvz73aUJR_6hxuQdRzETZzOdXJg";
// The permanent lead worksheet. Fixed on purpose: enquiries must never go to another tab.
var WORKSHEET_NAME = "Enquiries";
// How many rows from the top to search for the header row (row 1 holds the title banner)
var HEADER_SEARCH_ROWS = 5;

// Column order; must match SheetRow in the Spring Boot backend
var HEADERS = [
  "Reference",
  "Created Date",
  "First Name",
  "Last Name",
  "Company / Organization",
  "Email",
  "Country Code",
  "Phone",
  "How Can We Help You?",
  "Message"
];

// Other labels accepted for the same column (the header row itself is never rewritten)
var HEADER_ALIASES = {
  "Email": ["Company Email"]
};

function getConfig() {
  var props = PropertiesService.getScriptProperties();
  return {
    spreadsheetId: (props.getProperty("SPREADSHEET_ID") || SPREADSHEET_ID).trim(),
    secretToken: (props.getProperty("SECRET_TOKEN") || "").trim()
  };
}

/** Returns the existing "Enquiries" worksheet, or throws. Never creates one. */
function getLeadSheet(ss) {
  var sheet = ss.getSheetByName(WORKSHEET_NAME);
  if (!sheet) {
    throw new Error("Worksheet '" + WORKSHEET_NAME + "' was not found; nothing was written");
  }
  return sheet;
}

/** 1-based row number of the header row, or 0 when no row matches the 10 columns. */
function findHeaderRow(sheet) {
  var rows = Math.min(HEADER_SEARCH_ROWS, sheet.getLastRow());
  if (rows < 1) return 0;
  var values = sheet.getRange(1, 1, rows, HEADERS.length).getDisplayValues();
  for (var r = 0; r < values.length; r++) {
    if (matchesHeaders(values[r])) return r + 1;
  }
  return 0;
}

function matchesHeaders(row) {
  for (var i = 0; i < HEADERS.length; i++) {
    var cell = String(row[i]).trim();
    var accepted = [HEADERS[i]].concat(HEADER_ALIASES[HEADERS[i]] || []);
    if (accepted.indexOf(cell) === -1) return false;
  }
  return true;
}

/** Row number holding this reference in column A below the header, or 0. */
function findReference(sheet, headerRow, reference) {
  var lastRow = sheet.getLastRow();
  if (lastRow <= headerRow) return 0;
  var refs = sheet.getRange(headerRow + 1, 1, lastRow - headerRow, 1).getDisplayValues();
  for (var i = 0; i < refs.length; i++) {
    if (String(refs[i][0]).trim() === reference) return headerRow + 1 + i;
  }
  return 0;
}

/**
 * Run once from the Apps Script editor: authorizes the script and reports what it found.
 * Read-only: it does not create, rename or modify anything.
 */
function setup() {
  var config = getConfig();
  var ss = SpreadsheetApp.openById(config.spreadsheetId);
  var sheet = getLeadSheet(ss);
  var headerRow = findHeaderRow(sheet);
  var summary = "Spreadsheet: " + ss.getName() +
      " | Worksheet: " + sheet.getName() +
      " | Header row: " + (headerRow || "NOT FOUND - row must contain: " + HEADERS.join(", ")) +
      " | Last row: " + sheet.getLastRow() +
      " | SECRET_TOKEN set: " + (config.secretToken ? "yes" : "NO - add it in Script Properties");
  Logger.log(summary);
  return summary;
}

/**
 * Connection check (GET). Returns no enquiry data, only status and layout.
 */
function doGet(e) {
  try {
    var config = getConfig();
    var ss = SpreadsheetApp.openById(config.spreadsheetId);
    var sheet = ss.getSheetByName(WORKSHEET_NAME);
    var headerRow = sheet ? findHeaderRow(sheet) : 0;
    return jsonResponse(true, "Google Sheets connection active", {
      spreadsheetName: ss.getName(),
      worksheet: WORKSHEET_NAME,
      worksheetExists: !!sheet,
      headerRow: headerRow,
      headersValid: headerRow > 0,
      totalRows: sheet ? sheet.getLastRow() : 0,
      dataRows: sheet && headerRow ? Math.max(0, sheet.getLastRow() - headerRow) : 0,
      tokenConfigured: !!config.secretToken
    });
  } catch (err) {
    return jsonResponse(false, "Connection check failed: " + err.toString());
  }
}

/**
 * Enquiry submission (POST).
 */
function doPost(e) {
  var lock = LockService.getScriptLock();
  var locked = false;
  try {
    var config = getConfig();
    if (!config.secretToken) {
      return jsonResponse(false, "SECRET_TOKEN script property is not configured");
    }

    if (!e || !e.postData || !e.postData.contents) {
      return jsonResponse(false, "No post data received");
    }

    var data;
    try {
      data = JSON.parse(e.postData.contents);
    } catch (parseErr) {
      return jsonResponse(false, "Invalid JSON payload");
    }

    var receivedToken = String(data.token || "").trim();
    if (receivedToken !== config.secretToken) {
      return jsonResponse(false, "Unauthorized");
    }

    var reference = String(data.reference || "").trim();
    if (!reference) {
      return jsonResponse(false, "Missing enquiry reference");
    }

    // Serialize writes so the duplicate check and append are atomic
    lock.waitLock(30000);
    locked = true;

    var ss = SpreadsheetApp.openById(config.spreadsheetId);
    var sheet = getLeadSheet(ss);
    var headerRow = findHeaderRow(sheet);
    if (!headerRow) {
      return jsonResponse(false, "Header row not found in '" + WORKSHEET_NAME + "'; nothing was written");
    }

    var existingRow = findReference(sheet, headerRow, reference);
    if (existingRow) {
      return jsonResponse(true, "Already exists", { reference: reference, row: existingRow, worksheet: WORKSHEET_NAME });
    }

    var values = [
      reference,
      data.createdDate || new Date().toISOString(),
      data.firstName || "",
      data.lastName || "",
      data.company || "",
      data.email || "",
      data.countryCode || "",
      data.phone || "",
      data.service || "",
      data.message || ""
    ].map(function (v) { return String(v); });

    // Directly below the last used row (inside the existing banding and filter range).
    // Plain-text cells: otherwise "+91" becomes 91 and "+1 201-555-0123" a formula (#ERROR!).
    var targetRow = Math.max(sheet.getLastRow(), headerRow) + 1;
    var range = sheet.getRange(targetRow, 1, 1, HEADERS.length);
    range.setNumberFormat("@");
    range.setValues([values]);
    SpreadsheetApp.flush();

    // Confirm the write before reporting success
    var written = String(sheet.getRange(targetRow, 1).getDisplayValue()).trim();
    if (written !== reference) {
      return jsonResponse(false, "Write could not be confirmed for " + reference + " at row " + targetRow);
    }

    return jsonResponse(true, "Enquiry appended successfully", {
      reference: reference,
      row: targetRow,
      worksheet: WORKSHEET_NAME
    });
  } catch (err) {
    return jsonResponse(false, "Error processing enquiry: " + err.toString());
  } finally {
    if (locked) {
      lock.releaseLock();
    }
  }
}

function jsonResponse(success, message, extra) {
  var output = { success: success, message: message };
  if (extra) {
    for (var k in extra) {
      output[k] = extra[k];
    }
  }
  return ContentService.createTextOutput(JSON.stringify(output))
    .setMimeType(ContentService.MimeType.JSON);
}
