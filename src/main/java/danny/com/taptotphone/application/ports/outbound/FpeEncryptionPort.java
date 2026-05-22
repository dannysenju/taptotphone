package danny.com.taptotphone.application.ports.outbound;

public interface FpeEncryptionPort {
    /**
     * Encrypts the PAN in a format-preserving way.
     * The input is a 16-digit numeric string, and the output is a 16-digit numeric string.
     */
    String encrypt(String rawPan);

    /**
     * Decrypts the format-preserving encrypted PAN.
     * The input is a 16-digit numeric string, and the output is the original 16-digit PAN.
     */
    String decrypt(String encryptedPan);
}
