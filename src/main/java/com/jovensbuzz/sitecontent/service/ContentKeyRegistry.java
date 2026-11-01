package com.jovensbuzz.sitecontent.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The approved set of editable public content keys. Only these keys can be edited by
 * administrators or served publicly; each declares how its value is validated.
 * Add a key here (and optionally seed it in a migration) to make new copy editable.
 */
public final class ContentKeyRegistry {

    public enum ValueType { TEXT, LONG_TEXT, URL, EMAIL, PHONE }

    public record ContentKey(String key, String label, String group, ValueType type, int maxLength, boolean required) {}

    private static final Map<String, ContentKey> KEYS = new LinkedHashMap<>();

    static {
        register("hero_eyebrow", "Hero eyebrow", "Hero", ValueType.TEXT, 80, true);
        register("hero_headline", "Hero headline", "Hero", ValueType.TEXT, 120, true);
        register("hero_description", "Hero description", "Hero", ValueType.LONG_TEXT, 400, true);
        register("hero_primary_cta", "Primary button label", "Hero", ValueType.TEXT, 40, true);
        register("hero_secondary_cta", "Secondary button label", "Hero", ValueType.TEXT, 40, true);
        register("about_title", "About title", "About", ValueType.TEXT, 120, true);
        register("about_description", "About summary", "About", ValueType.LONG_TEXT, 400, true);
        register("about_body", "About body", "About", ValueType.LONG_TEXT, 2000, false);
        register("strategy_title", "Strategy title", "Strategy", ValueType.TEXT, 120, true);
        register("strategy_description", "Strategy description", "Strategy", ValueType.LONG_TEXT, 1000, true);
        register("footer_description", "Footer description", "Footer", ValueType.LONG_TEXT, 400, false);
        register("contact_office", "Office location", "Contact", ValueType.TEXT, 160, false);
        register("contact_phone", "Phone number", "Contact", ValueType.PHONE, 40, false);
        register("contact_email", "Email address", "Contact", ValueType.EMAIL, 128, false);
        register("social_instagram", "Instagram URL", "Social", ValueType.URL, 255, false);
        register("social_facebook", "Facebook URL", "Social", ValueType.URL, 255, false);
        register("social_linkedin", "LinkedIn URL", "Social", ValueType.URL, 255, false);
        register("social_youtube", "YouTube URL", "Social", ValueType.URL, 255, false);
        register("social_x", "X (Twitter) URL", "Social", ValueType.URL, 255, false);
        register("social_dribbble", "Dribbble URL", "Social", ValueType.URL, 255, false);
        register("social_behance", "Behance URL", "Social", ValueType.URL, 255, false);
    }

    private ContentKeyRegistry() {}

    private static void register(String key, String label, String group, ValueType type, int maxLength, boolean required) {
        KEYS.put(key, new ContentKey(key, label, group, type, maxLength, required));
    }

    public static Optional<ContentKey> find(String key) {
        return Optional.ofNullable(KEYS.get(key));
    }

    public static Iterable<ContentKey> all() {
        return KEYS.values();
    }

    public static boolean isPublic(String key) {
        return KEYS.containsKey(key);
    }
}
