package com.jovensbuzz.common.util;

import java.security.SecureRandom;
import java.time.Year;

public final class ReferenceGenerator {

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // omitted confusing 0/O, 1/I
    private static final SecureRandom RANDOM = new SecureRandom();

    private ReferenceGenerator() {}

    public static String generateEnquiryReference() {
        int year = Year.now().getValue();
        StringBuilder sb = new StringBuilder("JB-").append(year).append("-");
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
