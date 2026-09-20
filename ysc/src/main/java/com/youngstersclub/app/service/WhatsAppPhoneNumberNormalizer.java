package com.youngstersclub.app.service;

/** Shared canonical representation for WhatsApp recipient phone numbers. */
public final class WhatsAppPhoneNumberNormalizer {

    private WhatsAppPhoneNumberNormalizer() {
    }

    public static String normalize(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 10) {
            return "91" + digits;
        }
        if (digits.length() == 12 && digits.startsWith("91")) {
            return digits;
        }
        return null;
    }
}
