package com.jovensbuzz.integration.googlesheets;

import com.jovensbuzz.common.validation.InternationalPhoneValidator;
import com.jovensbuzz.enquiry.entity.Enquiry;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Immutable snapshot of one spreadsheet row, captured inside a transaction so the
 * Google API / Web App call itself runs without holding a database connection.
 * Column order must match HEADERS in backend/scripts/GoogleAppsScript_Enquiries.js.
 */
public record SheetRow(
        String reference,
        String createdAt,
        String firstName,
        String lastName,
        String company,
        String email,
        String countryCode,
        String phone,
        String service,
        String message) {

    public static final List<Object> HEADER = List.of(
            "Reference", "Created Date", "First Name", "Last Name", "Company / Organization",
            "Email", "Country Code", "Phone", "How Can We Help You?", "Message");

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    public static SheetRow fromEnquiry(Enquiry enquiry) {
        String first = nullToEmpty(enquiry.getFirstName()).trim();
        String last = nullToEmpty(enquiry.getLastName()).trim();
        if (first.isEmpty() && last.isEmpty()) {
            // Enquiries from before first/last were stored: split on the first space
            String full = nullToEmpty(enquiry.getFullName()).trim();
            int space = full.indexOf(' ');
            first = space < 0 ? full : full.substring(0, space);
            last = space < 0 ? "" : full.substring(space + 1).trim();
        }

        return new SheetRow(
                enquiry.getPublicReference(),
                TIMESTAMP.format(enquiry.getCreatedAt()),
                first,
                last,
                nullToEmpty(enquiry.getCompanyName()),
                nullToEmpty(enquiry.getEmail()),
                InternationalPhoneValidator.dialCode(enquiry.getPhone()),
                InternationalPhoneValidator.nationalNumber(enquiry.getPhone()),
                nullToEmpty(enquiry.getServiceInterest()),
                nullToEmpty(enquiry.getMessage()));
    }

    public List<Object> values() {
        return List.of(reference, createdAt, firstName, lastName, company, email, countryCode, phone, service, message);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
