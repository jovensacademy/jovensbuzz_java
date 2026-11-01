package com.jovensbuzz.integration.googlesheets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for Google Sheets synchronization.
 * Supports both:
 * 1. Google Apps Script Web App (preferred, lightweight HTTP POST with token authentication)
 * 2. Google Sheets API v4 with Service Account credentials JSON key (direct fallback)
 *
 * Appends are idempotent per enquiry reference so retries never create duplicate rows.
 */
@Service
public class GoogleSheetsService {

    private static final Logger log = LoggerFactory.getLogger(GoogleSheetsService.class);

    @Value("${app.google.sheets.web-app-url:${GOOGLE_SHEETS_WEB_APP_URL:}}")
    private String webAppUrl;

    @Value("${app.google.sheets.secret-token:${GOOGLE_SHEETS_SECRET_TOKEN:}}")
    private String secretToken;

    @Value("${app.google.sheets.spreadsheet-id:${GOOGLE_SHEETS_SPREADSHEET_ID:}}")
    private String spreadsheetId;

    @Value("${app.google.sheets.spreadsheet-name:${GOOGLE_SHEETS_SPREADSHEET_NAME:jovens_Buzz_website}}")
    private String spreadsheetName;

    @Value("${app.google.sheets.worksheet-name:${GOOGLE_SHEETS_WORKSHEET_NAME:Enquiries}}")
    private String worksheetName;

    @Value("${app.google.sheets.credentials-path:${GOOGLE_SERVICE_ACCOUNT_FILE:}}")
    private String credentialsPath;

    @Value("${app.google.sheets.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    @Value("${app.google.sheets.read-timeout-ms:20000}")
    private int readTimeoutMs;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile Sheets cachedClient;

    public boolean isConfigured() {
        if (isWebAppConfigured()) {
            return true;
        }
        if (isServiceAccountConfigured()) {
            return true;
        }
        return false;
    }

    public boolean isWebAppConfigured() {
        return webAppUrl != null && !webAppUrl.trim().isEmpty();
    }

    public boolean isServiceAccountConfigured() {
        if (spreadsheetId == null || spreadsheetId.trim().isEmpty()) {
            return false;
        }
        if (credentialsPath == null || credentialsPath.trim().isEmpty()) {
            return false;
        }
        File credFile = new File(credentialsPath);
        return credFile.exists() && credFile.canRead();
    }

    /**
     * Appends the row unless a row with the same reference already exists.
     *
     * @return the updated range / status, or "existing:<row>" when the reference was already present
     */
    public String appendIfAbsent(SheetRow row) throws GeneralSecurityException, IOException, InterruptedException {
        if (!isConfigured()) {
            throw new IllegalStateException("Google Sheets integration is not configured. Set GOOGLE_SHEETS_WEB_APP_URL or GOOGLE_SHEETS_SPREADSHEET_ID with GOOGLE_SERVICE_ACCOUNT_FILE.");
        }

        if (isWebAppConfigured()) {
            return appendViaAppsScript(row);
        } else {
            return appendViaServiceAccount(row);
        }
    }

    /**
     * Sends enquiry payload to the Google Apps Script Web App via HTTP POST.
     * Follows HTTP 302/307 redirects to retrieve the execution outcome.
     */
    private String appendViaAppsScript(SheetRow row) throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();

        // 1. Authentication token (never logged)
        if (secretToken != null && !secretToken.isBlank()) {
            payload.put("token", secretToken.trim());
        }

        // 2. Spreadsheet identifiers
        if (spreadsheetId != null && !spreadsheetId.isBlank()) {
            payload.put("spreadsheetId", spreadsheetId.trim());
        }
        payload.put("spreadsheetName", spreadsheetName != null && !spreadsheetName.isBlank() ? spreadsheetName.trim() : "jovens_Buzz_website");
        payload.put("worksheet", worksheetName != null && !worksheetName.isBlank() ? worksheetName.trim() : "Enquiries");
        payload.put("worksheetName", worksheetName != null && !worksheetName.isBlank() ? worksheetName.trim() : "Enquiries");

        // 3. Enquiry fields, one per spreadsheet column (see SheetRow.HEADER)
        payload.put("reference", row.reference());
        payload.put("createdDate", row.createdAt());
        payload.put("firstName", row.firstName());
        payload.put("lastName", row.lastName());
        payload.put("company", row.company());
        payload.put("email", row.email());
        payload.put("countryCode", row.countryCode());
        payload.put("phone", row.phone());
        payload.put("service", row.service());
        payload.put("message", row.message());

        String jsonBody = objectMapper.writeValueAsString(payload);

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webAppUrl.trim()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/plain, */*")
                .timeout(Duration.ofMillis(readTimeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        log.debug("Sending enquiry '{}' to Google Apps Script Web App", row.reference());
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        int statusCode = response.statusCode();

        // Handle redirect if not followed automatically
        if (statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307) {
            String redirectUrl = response.headers().firstValue("Location").orElse(null);
            if (redirectUrl != null && !redirectUrl.isBlank()) {
                log.debug("Following manual redirect to {}", redirectUrl);
                HttpRequest redirectRequest = HttpRequest.newBuilder()
                        .uri(URI.create(redirectUrl))
                        .header("Accept", "application/json, text/plain, */*")
                        .timeout(Duration.ofMillis(readTimeoutMs))
                        .GET()
                        .build();
                response = client.send(redirectRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                statusCode = response.statusCode();
            }
        }

        String responseBody = response.body() != null ? response.body().trim() : "";
        log.debug("Google Apps Script response: HTTP {} - {}", statusCode, sanitize(responseBody));

        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("Google Apps Script returned HTTP " + statusCode + ": " + sanitize(responseBody));
        }

        // Only an explicit {"success":true} counts. Google answers with HTML (sign-in page,
        // "Script function not found") when the deployment is wrong, and that must never be
        // recorded as a successful sync.
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (IOException ex) {
            root = null;
        }
        if (root == null || !root.isObject() || !root.path("success").isBoolean()) {
            throw new IOException("Google Apps Script returned a non-JSON response (check the web app deployment): "
                    + abbreviate(sanitize(stripHtml(responseBody))));
        }

        String message = root.path("message").asText("");
        if (!root.path("success").asBoolean()) {
            throw new IllegalStateException("Google Apps Script rejected sync: " + (message.isBlank() ? "Unauthorized" : message));
        }

        if ("already exists".equalsIgnoreCase(message)) {
            int rowNum = root.path("row").asInt(0);
            log.info("Enquiry '{}' already present in Google Sheets (row {})", row.reference(), rowNum);
            return "existing:" + (rowNum > 0 ? rowNum : "row");
        }

        if (root.has("row")) {
            int rowNum = root.path("row").asInt(0);
            log.info("Appended enquiry '{}' to Google Sheets via Web App (row {})", row.reference(), rowNum);
            return "apps_script:row:" + (rowNum > 0 ? rowNum : "success");
        }

        log.info("Appended enquiry '{}' to Google Sheets via Web App", row.reference());
        return "apps_script:success";
    }

    /**
     * Direct append using Google Sheets API v4.
     */
    private String appendViaServiceAccount(SheetRow row) throws GeneralSecurityException, IOException {
        Sheets sheets = getSheetsService();
        String quotedSheet = "'" + worksheetName.replace("'", "''") + "'";

        ValueRange existing = sheets.spreadsheets().values()
                .get(spreadsheetId, quotedSheet + "!A:A")
                .execute();
        List<List<Object>> column = existing.getValues() != null ? existing.getValues() : Collections.emptyList();
        for (int i = 0; i < column.size(); i++) {
            List<Object> cell = column.get(i);
            if (!cell.isEmpty() && row.reference().equals(String.valueOf(cell.get(0)).trim())) {
                log.info("Enquiry '{}' already present in Google Sheets at row {}; skipping append", row.reference(), i + 1);
                return "existing:" + (i + 1);
            }
        }

        if (column.isEmpty()) {
            appendRaw(sheets, quotedSheet, SheetRow.HEADER);
        }

        AppendValuesResponse response = appendRaw(sheets, quotedSheet, row.values());
        String range = response.getUpdates() != null ? response.getUpdates().getUpdatedRange() : response.getTableRange();
        log.info("Appended enquiry '{}' to Google Sheets ({})", row.reference(), range);
        return range;
    }

    private AppendValuesResponse appendRaw(Sheets sheets, String quotedSheet, List<Object> values) throws IOException {
        ValueRange body = new ValueRange().setValues(Collections.singletonList(values));
        return sheets.spreadsheets().values()
                .append(spreadsheetId, quotedSheet + "!A:J", body)
                .setValueInputOption("RAW")
                .setInsertDataOption("INSERT_ROWS")
                .execute();
    }

    private Sheets getSheetsService() throws GeneralSecurityException, IOException {
        Sheets client = cachedClient;
        if (client != null) {
            return client;
        }
        synchronized (this) {
            if (cachedClient == null) {
                GoogleCredentials credentials;
                try (InputStream in = new FileInputStream(credentialsPath)) {
                    credentials = GoogleCredentials.fromStream(in)
                            .createScoped(Collections.singletonList(SheetsScopes.SPREADSHEETS));
                }
                HttpCredentialsAdapter credentialsAdapter = new HttpCredentialsAdapter(credentials);
                HttpRequestInitializer initializer = request -> {
                    credentialsAdapter.initialize(request);
                    request.setConnectTimeout(connectTimeoutMs);
                    request.setReadTimeout(readTimeoutMs);
                };
                cachedClient = new Sheets.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        initializer)
                        .setApplicationName("Jovens Buzz Agency")
                        .build();
            }
            return cachedClient;
        }
    }

    private static String sanitize(String input) {
        if (input == null) return "";
        return input.replaceAll("(?i)(token|key|secret)=[^&\\s]+", "$1=[redacted]");
    }

    private static String stripHtml(String input) {
        if (input == null) return "";
        return input.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String abbreviate(String input) {
        return input.length() > 200 ? input.substring(input.length() - 200) : input;
    }

    public String getServiceAccountEmail() {
        if (credentialsPath == null || credentialsPath.isBlank()) return null;
        File file = new File(credentialsPath);
        if (!file.canRead()) return null;
        try {
            JsonNode node = new ObjectMapper().readTree(file);
            return node.hasNonNull("client_email") ? node.get("client_email").asText() : null;
        } catch (IOException e) {
            log.warn("Could not read service account file: {}", e.getMessage());
            return null;
        }
    }

    public boolean isSpreadsheetIdSet() {
        return spreadsheetId != null && !spreadsheetId.isBlank();
    }

    public boolean isCredentialsFileReadable() {
        return credentialsPath != null && !credentialsPath.isBlank() && new File(credentialsPath).canRead();
    }

    public String getSpreadsheetId() {
        return spreadsheetId;
    }

    public String getSpreadsheetName() {
        return spreadsheetName;
    }

    public String getWorksheetName() {
        return worksheetName;
    }

    public String getCredentialsPath() {
        return credentialsPath;
    }

    public String getWebAppUrl() {
        return webAppUrl;
    }
}
