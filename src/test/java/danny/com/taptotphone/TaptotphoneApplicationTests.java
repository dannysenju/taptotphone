package danny.com.taptotphone;

import danny.com.taptotphone.domain.model.PAN;
import danny.com.taptotphone.infrastructure.adapters.outbound.encryption.FpeEncryptionAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaptotphoneApplicationTests {

    private final FpeEncryptionAdapter fpeAdapter = new FpeEncryptionAdapter("0123456789ABCDEF0123456789ABCDEF");

    @Test
    void testFormatPreservingEncryption() {
        // Standard Visa card with valid Luhn checksum
        String originalPan = "4111112222213333";
        
        // 1. Perform FPE encryption
        String encryptedPan = fpeAdapter.encrypt(originalPan);
        
        assertNotNull(encryptedPan);
        assertEquals(16, encryptedPan.length());
        assertTrue(encryptedPan.matches("\\d+"), "Encrypted PAN must be fully numeric");
        
        // 2. Verify format preservation constraints (BIN and last 4 intact)
        assertEquals(originalPan.substring(0, 6), encryptedPan.substring(0, 6), "BIN (first 6 digits) must be unchanged");
        assertEquals(originalPan.substring(12, 16), encryptedPan.substring(12, 16), "Last 4 digits must be unchanged");
        
        // 3. Verify middle 6 digits are encrypted
        assertNotEquals(originalPan.substring(6, 12), encryptedPan.substring(6, 12), "Middle 6 digits must be obfuscated");
        
        // 4. Perform FPE decryption
        String decryptedPan = fpeAdapter.decrypt(encryptedPan);
        
        assertNotNull(decryptedPan);
        assertEquals(originalPan, decryptedPan, "Decrypted PAN must match the original PAN exactly");
    }

    @Test
    void testPANValueObjectValidation() {
        // Valid Visa PAN
        PAN validPan = new PAN("4111112222213333");
        assertEquals("4111112222213333", validPan.value());
        assertEquals("411111******3333", validPan.masked());

        // Invalid checksum
        assertThrows(IllegalArgumentException.class, () -> new PAN("4111112222223334"));

        // Non-numeric
        assertThrows(IllegalArgumentException.class, () -> new PAN("411111222222333A"));

        // Too short
        assertThrows(IllegalArgumentException.class, () -> new PAN("123456789"));
    }
}
