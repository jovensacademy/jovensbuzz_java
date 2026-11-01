package com.jovensbuzz.enquiry.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lead pipeline states and the transitions an administrator may make between them.
 * CLOSED and SPAM can be reopened so mistakes are recoverable.
 */
public enum EnquiryStatus {
    NEW,
    CONTACTED,
    QUALIFIED,
    CONVERTED,
    CLOSED,
    SPAM;

    public Set<EnquiryStatus> allowedTransitions() {
        return switch (this) {
            case NEW -> EnumSet.of(CONTACTED, QUALIFIED, CLOSED, SPAM);
            case CONTACTED -> EnumSet.of(QUALIFIED, CONVERTED, CLOSED, SPAM);
            case QUALIFIED -> EnumSet.of(CONTACTED, CONVERTED, CLOSED);
            case CONVERTED -> EnumSet.of(CLOSED);
            case CLOSED -> EnumSet.of(NEW, CONTACTED);
            case SPAM -> EnumSet.of(NEW);
        };
    }

    public boolean canTransitionTo(EnquiryStatus target) {
        return target != null && allowedTransitions().contains(target);
    }

    public static boolean isValid(String status) {
        if (status == null) return false;
        try {
            valueOf(status.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
