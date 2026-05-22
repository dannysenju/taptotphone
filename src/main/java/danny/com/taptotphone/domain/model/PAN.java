package danny.com.taptotphone.domain.model;

/**
 * Value Object representing a Primary Account Number (PAN).
 * Implements validation (Luhn checksum, length checks) and secure masking.
 */
public record PAN(String value) {

    public PAN {
        if (value == null || !value.matches("\\d{13,19}")) {
            throw new IllegalArgumentException("Invalid PAN: must be numeric and between 13 and 19 digits long");
        }
        if (!isValidLuhn(value)) {
            throw new IllegalArgumentException("Invalid PAN: failed Luhn checksum validation");
        }
    }

    /**
     * Returns a masked version of the PAN preserving first 6 digits (BIN) and last 4 digits.
     * e.g., 411111******1111
     */
    public String masked() {
        if (value.length() <= 10) {
            return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
        }
        return value.substring(0, 6) + "*".repeat(value.length() - 10) + value.substring(value.length() - 4);
    }

    public String first6() {
        return value.substring(0, 6);
    }

    public String last4() {
        return value.substring(value.length() - 4);
    }

    /**
     * Extracts the middle 6 digits (indexes 6 to 12) for Format-Preserving Encryption.
     */
    public String middle6() {
        if (value.length() == 16) {
            return value.substring(6, 12);
        }
        throw new UnsupportedOperationException("Format-Preserving Encryption middle-6 extraction only supported for 16-digit PANs");
    }

    private static boolean isValidLuhn(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = Character.getNumericValue(number.charAt(i));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }
}
