package com.jovensbuzz.common.validation;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.jovensbuzz.common.exception.BadRequestException;

import java.util.List;
import java.util.Locale;

/**
 * Country-aware phone validation backed by Google's libphonenumber. The ISO country chosen by the
 * visitor decides the numbering plan (the US and Canada both use +1, so the dial code alone can't).
 */
public final class InternationalPhoneValidator {

    /** Countries offered by the contact form, in display order. */
    public static final List<String> SUPPORTED_COUNTRIES = List.of("US", "IN", "CA", "GB", "AU");

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    private InternationalPhoneValidator() {}

    public record NormalizedPhone(String e164, String country) {}

    /**
     * Validates and normalises a phone number to E.164.
     *
     * @param rawPhone number as typed (national or +international); letters are rejected
     * @param country  ISO 3166-1 alpha-2 code; when null the number must include its +country code
     */
    public static NormalizedPhone normalize(String rawPhone, String country) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new BadRequestException("Phone number is required");
        }
        String phone = rawPhone.trim();
        if (!phone.matches("^\\+?[0-9 ().\\-]{4,32}$")) {
            throw new BadRequestException("Phone number may contain only digits, spaces, brackets and hyphens");
        }

        String region = country == null || country.isBlank() ? null : country.trim().toUpperCase(Locale.ROOT);
        if (region != null && !SUPPORTED_COUNTRIES.contains(region)) {
            throw new BadRequestException("Phone country is not supported");
        }

        PhoneNumber parsed;
        try {
            parsed = UTIL.parse(phone, region != null ? region : "ZZ");
        } catch (NumberParseException e) {
            throw new BadRequestException("Please enter a valid phone number");
        }

        boolean valid = region != null ? UTIL.isValidNumberForRegion(parsed, region) : UTIL.isValidNumber(parsed);
        if (!valid) {
            throw new BadRequestException(region != null
                    ? "Please enter a valid " + displayName(region) + " phone number"
                    : "Please enter a valid phone number with country code");
        }
        String resolvedRegion = region != null ? region : UTIL.getRegionCodeForNumber(parsed);
        return new NormalizedPhone(UTIL.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164), resolvedRegion);
    }

    /** Human-friendly international format for emails and the dashboard, e.g. "+1 415-555-2671". */
    public static String formatInternational(String e164) {
        if (e164 == null || e164.isBlank()) return "";
        try {
            return UTIL.format(UTIL.parse(e164, "ZZ"), PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
        } catch (NumberParseException e) {
            return e164;
        }
    }

    /** Dialling code of an E.164 number, e.g. "+91"; empty when it cannot be parsed. */
    public static String dialCode(String e164) {
        if (e164 == null || e164.isBlank()) return "";
        try {
            return "+" + UTIL.parse(e164, "ZZ").getCountryCode();
        } catch (NumberParseException e) {
            return "";
        }
    }

    /** National part of an E.164 number in international grouping, e.g. "63793 62630". */
    public static String nationalNumber(String e164) {
        if (e164 == null || e164.isBlank()) return "";
        try {
            PhoneNumber parsed = UTIL.parse(e164, "ZZ");
            String international = UTIL.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
            String prefix = "+" + parsed.getCountryCode();
            return international.startsWith(prefix) ? international.substring(prefix.length()).trim() : international;
        } catch (NumberParseException e) {
            return e164;
        }
    }

    private static String displayName(String region) {
        return new Locale("", region).getDisplayCountry(Locale.ENGLISH);
    }
}
