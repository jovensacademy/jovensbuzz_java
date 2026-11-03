package com.jovensbuzz.common.util;

import com.jovensbuzz.common.exception.BadRequestException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * IANA time zone handling. Timestamps are always stored in UTC; these helpers only convert for display.
 * Region IDs (e.g. America/Chicago) are required so daylight saving (CST/CDT) is applied automatically;
 * fixed offsets such as "+05:30" or "GMT-6" are rejected.
 */
public final class TimeZones {

    public static final String DEFAULT_ZONE = "America/Chicago";

    /** Zones offered in the dashboard (any other valid IANA region is still accepted). */
    public static final List<Map<String, String>> OPTIONS = List.of(
            Map.of("id", "America/Chicago", "label", "USA – Central (Frisco, TX) · CST/CDT"),
            Map.of("id", "Asia/Kolkata", "label", "India (IST, UTC+05:30)"),
            Map.of("id", "America/New_York", "label", "USA – Eastern · EST/EDT"),
            Map.of("id", "America/Denver", "label", "USA – Mountain · MST/MDT"),
            Map.of("id", "America/Los_Angeles", "label", "USA – Pacific · PST/PDT"),
            Map.of("id", "Europe/London", "label", "United Kingdom · GMT/BST"),
            Map.of("id", "Australia/Sydney", "label", "Australia – Sydney · AEST/AEDT"),
            Map.of("id", "UTC", "label", "UTC"));

    private TimeZones() {}

    /** Validates and normalises an IANA region ID; throws BadRequestException otherwise. */
    public static String requireRegion(String zone) {
        if (zone == null || zone.isBlank()) {
            throw new BadRequestException("Time zone is required");
        }
        String id = zone.trim();
        boolean region = id.equals("UTC") || (id.contains("/") && !id.startsWith("Etc/") && !id.matches(".*[+-]\\d.*"));
        if (!region || !ZoneId.getAvailableZoneIds().contains(id)) {
            throw new BadRequestException("Use an IANA time zone such as America/Chicago or Asia/Kolkata (not a fixed UTC offset)");
        }
        return id;
    }

    /** The zone to display in: the given one when valid, otherwise the default. */
    public static ZoneId zoneOrDefault(String zone) {
        try {
            return ZoneId.of(requireRegion(zone));
        } catch (RuntimeException e) {
            return ZoneId.of(DEFAULT_ZONE);
        }
    }

    /** e.g. "Oct 31, 2026, 7:22 AM CDT" — the abbreviation follows daylight saving for the instant. */
    public static String formatWithZone(Instant instant, ZoneId zone) {
        if (instant == null) return "";
        ZonedDateTime local = instant.atZone(zone);
        return local.format(DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.US))
                + " " + local.format(DateTimeFormatter.ofPattern("zzz", Locale.US));
    }

    /** Short zone label for headings, e.g. "America/Chicago (CDT)" at the given instant. */
    public static String label(ZoneId zone, Instant at) {
        return zone.getId() + " (" + at.atZone(zone).format(DateTimeFormatter.ofPattern("zzz", Locale.US)) + ")";
    }
}
