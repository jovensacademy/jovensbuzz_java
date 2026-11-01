package com.jovensbuzz.admin.dto;

/**
 * Google Sheets integration health. Contains no secrets: the spreadsheet ID is masked and
 * only the (non-secret) service account email is shown so the sheet can be shared with it.
 */
public class IntegrationStatusDto {

    private boolean configured;
    private boolean spreadsheetIdSet;
    private String spreadsheetIdHint;
    private String worksheetName;
    private boolean credentialsFileReadable;
    private String serviceAccountEmail;
    private long pending;
    private long processing;
    private long retrying;
    private long failed;
    private long succeeded;

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public boolean isSpreadsheetIdSet() {
        return spreadsheetIdSet;
    }

    public void setSpreadsheetIdSet(boolean spreadsheetIdSet) {
        this.spreadsheetIdSet = spreadsheetIdSet;
    }

    public String getSpreadsheetIdHint() {
        return spreadsheetIdHint;
    }

    public void setSpreadsheetIdHint(String spreadsheetIdHint) {
        this.spreadsheetIdHint = spreadsheetIdHint;
    }

    public String getWorksheetName() {
        return worksheetName;
    }

    public void setWorksheetName(String worksheetName) {
        this.worksheetName = worksheetName;
    }

    public boolean isCredentialsFileReadable() {
        return credentialsFileReadable;
    }

    public void setCredentialsFileReadable(boolean credentialsFileReadable) {
        this.credentialsFileReadable = credentialsFileReadable;
    }

    public String getServiceAccountEmail() {
        return serviceAccountEmail;
    }

    public void setServiceAccountEmail(String serviceAccountEmail) {
        this.serviceAccountEmail = serviceAccountEmail;
    }

    public long getPending() {
        return pending;
    }

    public void setPending(long pending) {
        this.pending = pending;
    }

    public long getProcessing() {
        return processing;
    }

    public void setProcessing(long processing) {
        this.processing = processing;
    }

    public long getRetrying() {
        return retrying;
    }

    public void setRetrying(long retrying) {
        this.retrying = retrying;
    }

    public long getFailed() {
        return failed;
    }

    public void setFailed(long failed) {
        this.failed = failed;
    }

    public long getSucceeded() {
        return succeeded;
    }

    public void setSucceeded(long succeeded) {
        this.succeeded = succeeded;
    }
}
