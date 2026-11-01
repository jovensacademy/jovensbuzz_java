package com.jovensbuzz.integration.email.service;

import com.jovensbuzz.common.validation.InternationalPhoneValidator;
import com.jovensbuzz.enquiry.entity.Enquiry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the branded HTML + plain-text emails. Every dynamic value is HTML-escaped, so visitor
 * input can never inject markup into an email; layout uses tables and inline styles for
 * compatibility with email clients.
 */
@Service
public class EmailTemplateService {

    public static final String ADMIN_SUBJECT = "New Enquiry Received - Jovens Buzz";
    public static final String CLIENT_SUBJECT = "Thank You for Contacting Jovens Buzz!";

    private static final String ORANGE = "#f4761f";
    private static final String INK = "#1d1d1d";
    private static final String MUTED = "#5b6270";

    private final EmailSettings settings;
    private final DateTimeFormatter timestampFormat;

    public EmailTemplateService(EmailSettings settings, @Value("${app.business-timezone:America/Chicago}") String timezone) {
        this.settings = settings;
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone);
        } catch (DateTimeException e) {
            zone = ZoneOffset.UTC;
        }
        this.timestampFormat = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a z", Locale.ENGLISH).withZone(zone);
    }

    public record EmailContent(String subject, String html, String text) {}

    // ------------------------------------------------------------------ admin notification

    public EmailContent adminNotification(Enquiry enquiry) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Customer Name", enquiry.getFullName());
        details.put("Email Address", enquiry.getEmail());
        details.put("International Phone Number", phoneLabel(enquiry));
        details.put("Company Name", orNotProvided(enquiry.getCompanyName()));
        details.put("Selected Service", enquiry.getServiceInterest());
        details.put("Budget", orNotProvided(enquiry.getBudget()));
        details.put("Submission Date and Time", timestampFormat.format(enquiry.getCreatedAt()));
        String dashboardLink = settings.getAdminDashboardUrl() + "/enquiries/" + enquiry.getId();

        StringBuilder rows = new StringBuilder();
        details.forEach((label, value) -> rows.append(detailRow(label, value)));
        String html = layout(
                "A new enquiry has been submitted",
                "<p style=\"margin:0 0 16px;\">Hello Admin,</p>"
                        + "<p style=\"margin:0 0 24px;\">A new enquiry has been submitted through the Jovens Buzz website.</p>"
                        + "<h2 style=\"margin:0 0 12px;font-size:16px;color:" + INK + ";\">Enquiry Details</h2>"
                        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;\">"
                        + rows
                        + "</table>"
                        + "<h2 style=\"margin:24px 0 8px;font-size:16px;color:" + INK + ";\">Message</h2>"
                        + "<div style=\"padding:16px;border-radius:12px;background:#fff7ef;border:1px solid #fde0c5;line-height:1.6;\">"
                        + multiline(enquiry.getMessage())
                        + "</div>"
                        + button("View Enquiry in Admin Dashboard", dashboardLink));

        StringBuilder text = new StringBuilder("Hello Admin,\n\nA new enquiry has been submitted through the Jovens Buzz website.\n\nEnquiry Details:\n");
        details.forEach((label, value) -> text.append("- ").append(label).append(": ").append(value).append('\n'));
        text.append("\nMessage:\n").append(nullToEmpty(enquiry.getMessage()))
                .append("\n\nView Enquiry in Admin Dashboard: ").append(dashboardLink).append('\n');

        return new EmailContent(ADMIN_SUBJECT, html, text.toString());
    }

    // ------------------------------------------------------------------ client confirmation

    public EmailContent clientConfirmation(Enquiry enquiry) {
        String name = customerName(enquiry.getFullName());
        String responseTime = settings.getResponseTime();
        String site = settings.getSiteUrl();

        String html = layout(
                "Thank you for contacting Jovens Buzz",
                "<p style=\"margin:0 0 16px;\">Hello " + esc(name) + ",</p>"
                        + "<p style=\"margin:0 0 16px;\">Thank you for reaching out to Jovens Buzz!</p>"
                        + "<p style=\"margin:0 0 16px;\">We've successfully received your enquiry about <strong>"
                        + esc(enquiry.getServiceInterest()) + "</strong> and appreciate your interest in our services.</p>"
                        + "<p style=\"margin:0 0 16px;\">Our team will review your requirements and get in touch with you "
                        + esc(responseTime) + ".</p>"
                        + "<p style=\"margin:0 0 24px;\">We look forward to understanding your goals and exploring how we can help your business grow.</p>"
                        + "<p style=\"margin:0;\">Best Regards,<br><strong>Jovens Buzz Team</strong><br>"
                        + "<a href=\"" + escAttr(site) + "\" style=\"color:" + ORANGE + ";text-decoration:none;font-weight:600;\">"
                        + esc(displayUrl(site)) + "</a></p>");

        String text = "Hello " + name + ",\n\n"
                + "Thank you for reaching out to Jovens Buzz!\n\n"
                + "We've successfully received your enquiry about " + enquiry.getServiceInterest()
                + " and appreciate your interest in our services.\n\n"
                + "Our team will review your requirements and get in touch with you " + responseTime + ".\n\n"
                + "We look forward to understanding your goals and exploring how we can help your business grow.\n\n"
                + "Best Regards,\nJovens Buzz Team\n" + site + "\n";

        return new EmailContent(CLIENT_SUBJECT, html, text);
    }

    // ------------------------------------------------------------------ building blocks

    private String layout(String preheader, String body) {
        String site = settings.getSiteUrl();
        String logoUrl = settings.getLogoUrl();
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Jovens Buzz</title></head>"
                + "<body style=\"margin:0;padding:0;background:#f4f5f7;\">"
                + "<span style=\"display:none;max-height:0;overflow:hidden;\">" + esc(preheader) + "</span>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f5f7;\"><tr>"
                + "<td align=\"center\" style=\"padding:32px 12px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:600px;background:#ffffff;"
                + "border-radius:16px;overflow:hidden;border:1px solid #eceef1;\">"
                + "<tr><td align=\"left\" style=\"padding:24px 32px;background:" + INK + ";\">"
                + "<a href=\"" + escAttr(site) + "\" target=\"_blank\" style=\"text-decoration:none;display:inline-block;\">"
                + "<img src=\"" + escAttr(logoUrl) + "\" alt=\"Jovens Buzz\" width=\"146\" height=\"55\" border=\"0\" "
                + "style=\"display:block;border:0;outline:none;text-decoration:none;-ms-interpolation-mode:bicubic;max-width:146px;width:146px;height:auto;\" />"
                + "</a></td></tr>"
                + "<tr><td style=\"height:4px;background:" + ORANGE + ";line-height:4px;font-size:0;\">&nbsp;</td></tr>"
                + "<tr><td style=\"padding:32px;font-family:Arial,Helvetica,sans-serif;font-size:15px;line-height:1.6;color:" + INK + ";\">"
                + body
                + "</td></tr>"
                + "<tr><td style=\"padding:20px 32px;background:#fafafa;font-family:Arial,Helvetica,sans-serif;font-size:12px;color:" + MUTED + ";\">"
                + "Jovens Buzz &middot; Creative. Strategic. Results-Driven.</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private String detailRow(String label, String value) {
        return "<tr><td style=\"padding:10px 12px 10px 0;border-bottom:1px solid #eceef1;color:" + MUTED + ";font-size:14px;"
                + "vertical-align:top;width:42%;\">" + esc(label) + "</td>"
                + "<td style=\"padding:10px 0;border-bottom:1px solid #eceef1;font-size:14px;font-weight:600;color:" + INK + ";"
                + "word-break:break-word;\">" + esc(value) + "</td></tr>";
    }

    private String button(String label, String url) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:28px 0 0;\"><tr>"
                + "<td style=\"border-radius:999px;background:" + ORANGE + ";\">"
                + "<a href=\"" + escAttr(url) + "\" style=\"display:inline-block;padding:14px 28px;font-family:Arial,Helvetica,sans-serif;"
                + "font-size:15px;font-weight:700;color:#ffffff;text-decoration:none;\">" + esc(label) + "</a></td></tr></table>";
    }

    private static String phoneLabel(Enquiry enquiry) {
        String formatted = InternationalPhoneValidator.formatInternational(enquiry.getPhone());
        return enquiry.getPhoneCountry() != null ? formatted + " (" + enquiry.getPhoneCountry() + ")" : formatted;
    }

    static String customerName(String fullName) {
        return fullName == null || fullName.isBlank() ? "there" : fullName.trim();
    }

    private static String multiline(String value) {
        return esc(nullToEmpty(value)).replace("\n", "<br>");
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(nullToEmpty(value), "UTF-8");
    }

    /** Only http(s) URLs from configuration or cid: URIs are used; anything else becomes "#". */
    private static String escAttr(String url) {
        return url != null && (url.matches("(?i)^https?://[^\\s\"'<>]+$") || url.matches("^cid:[a-zA-Z0-9_-]+$"))
                ? esc(url) : "#";
    }

    private static String displayUrl(String url) {
        return url == null ? "" : url.replaceFirst("(?i)^https?://", "");
    }

    private static String orNotProvided(String value) {
        return value == null || value.isBlank() ? "Not provided" : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
