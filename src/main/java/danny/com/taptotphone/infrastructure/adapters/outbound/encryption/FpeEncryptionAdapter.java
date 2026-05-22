package danny.com.taptotphone.infrastructure.adapters.outbound.encryption;

import danny.com.taptotphone.application.ports.outbound.FpeEncryptionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * High-performance Format-Preserving Encryption (FPE) adapter.
 * Uses a mathematically rigorous 4-round Feistel Network in Radix 1000.
 * Operates exclusively on the middle 6 digits of a 16-digit PAN,
 * leaving BIN (first 6) and last 4 digits in plaintext for legacy core compatibility
 * and PCI DSS routing allowance.
 */
@Component
public class FpeEncryptionAdapter implements FpeEncryptionPort {
    private static final Logger log = LoggerFactory.getLogger(FpeEncryptionAdapter.class);

    private final SecretKeySpec secretKey;

    public FpeEncryptionAdapter(@Value("${taptotphone.security.fpe-key}") String hexKey) {
        try {
            byte[] keyBytes = HexFormat.of().parseHex(hexKey);
            if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
                throw new IllegalArgumentException("AES FPE Key must be 128, 192, or 256 bits (16, 24, or 32 bytes)");
            }
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
            log.info("FPE Encryption Adapter initialized successfully with a {}-bit key.", keyBytes.length * 8);
        } catch (Exception e) {
            log.error("Failed to initialize FPE Encryption key", e);
            throw new IllegalStateException("FPE initialization failure", e);
        }
    }

    @Override
    public String encrypt(String rawPan) {
        if (rawPan == null || rawPan.length() != 16) {
            throw new IllegalArgumentException("FPE is only supported for 16-digit PANs");
        }

        String first6 = rawPan.substring(0, 6);
        String middle6 = rawPan.substring(6, 12);
        String last4 = rawPan.substring(12, 16);

        int value = Integer.parseInt(middle6);
        int encryptedValue = feistelCipher(value, true);

        return first6 + String.format("%06d", encryptedValue) + last4;
    }

    @Override
    public String decrypt(String encryptedPan) {
        if (encryptedPan == null || encryptedPan.length() != 16) {
            throw new IllegalArgumentException("FPE is only supported for 16-digit PANs");
        }

        String first6 = encryptedPan.substring(0, 6);
        String middle6 = encryptedPan.substring(6, 12);
        String last4 = encryptedPan.substring(12, 16);

        int value = Integer.parseInt(middle6);
        int decryptedValue = feistelCipher(value, false);

        return first6 + String.format("%06d", decryptedValue) + last4;
    }

    /**
     * Feistel network for a domain of [0, 999999] (base 1000 x 1000).
     * Split into L and R, where each represents a number [0, 999].
     */
    private int feistelCipher(int val, boolean encrypt) {
        int l = val / 1000;
        int r = val % 1000;

        int numRounds = 4;

        if (encrypt) {
            for (int round = 1; round <= numRounds; round++) {
                int nextL = r;
                int f = roundFunction(r, round);
                int nextR = (l + f) % 1000;
                l = nextL;
                r = nextR;
            }
        } else {
            for (int round = numRounds; round >= 1; round--) {
                int prevR = l;
                int f = roundFunction(l, round);
                int prevL = (r - f) % 1000;
                if (prevL < 0) {
                    prevL += 1000;
                }
                l = prevL;
                r = prevR;
            }
        }

        return l * 1000 + r;
    }

    /**
     * AES-based Feistel Round Function.
     * Takes a 3-digit number (0-999) and round number, encrypts with AES,
     * and maps to [0, 999] using modulus.
     */
    private int roundFunction(int r, int round) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            // Create a 16-byte block for AES
            ByteBuffer buffer = ByteBuffer.allocate(16);
            buffer.putInt(0, r);
            buffer.putInt(4, round);
            // Remaining bytes are zero-padded by default

            byte[] cipherText = cipher.doFinal(buffer.array());

            // Extract a positive integer from the cipherText
            int hash = ByteBuffer.wrap(cipherText).getInt(0);
            return Math.abs(hash) % 1000;
        } catch (GeneralSecurityException e) {
            log.error("Crypto exception in Feistel round function", e);
            throw new RuntimeException("Encryption internal error", e);
        }
    }
}
