package com.patorinaldi.wallet.common.logging;

import java.util.UUID;

/**
 * Utility class for masking sensitive data in logs.
 * Prevents PII (emails, UUIDs) from leaking into logs.
 */
public final class LogMasker {

    private LogMasker() {
        // Utility class - prevent instantiation
    }

    /**
     * Mask an email address, preserving only the first character and domain.
     * Example: "john@example.com" -> "j***@example.com"
     *
     * @param email the email to mask
     * @return masked email or "***" if invalid
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + email.substring(atIndex);
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    /**
     * Mask a UUID, preserving only the first segment.
     * Example: "550e8400-e29b-41d4-a716-446655440000" -> "550e8400-***"
     *
     * @param uuid the UUID to mask
     * @return masked UUID or "***" if null
     */
    public static String maskUuid(UUID uuid) {
        if (uuid == null) {
            return "***";
        }
        String str = uuid.toString();
        return str.substring(0, 8) + "-***";
    }

    /**
     * Mask a UUID string, preserving only the first segment.
     *
     * @param uuidStr the UUID string to mask
     * @return masked UUID or "***" if invalid
     */
    public static String maskUuid(String uuidStr) {
        if (uuidStr == null || uuidStr.length() < 8) {
            return "***";
        }
        return uuidStr.substring(0, 8) + "-***";
    }

    /**
     * Mask a phone number, preserving only the last 4 digits.
     * Example: "+1234567890" -> "***7890"
     *
     * @param phone the phone number to mask
     * @return masked phone or "***" if invalid
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
        return "***" + phone.substring(phone.length() - 4);
    }
}
