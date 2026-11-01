package com.jovensbuzz.common.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Administrator password rules. Kept deliberately simple and explainable in the UI.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {}

    public static List<String> validate(String password, String username) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            problems.add("at least " + MIN_LENGTH + " characters");
            return problems;
        }
        if (password.length() > MAX_LENGTH) problems.add("at most " + MAX_LENGTH + " characters");
        if (password.chars().noneMatch(Character::isUpperCase)) problems.add("an uppercase letter");
        if (password.chars().noneMatch(Character::isLowerCase)) problems.add("a lowercase letter");
        if (password.chars().noneMatch(Character::isDigit)) problems.add("a number");
        if (password.chars().allMatch(Character::isLetterOrDigit)) problems.add("a symbol");
        if (username != null && password.toLowerCase().contains(username.toLowerCase())) {
            problems.add("must not contain the username");
        }
        return problems;
    }
}
