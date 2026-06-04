package za.co.capitecbank.security.impl;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.capitecbank.config.EncryptionProperties;
import za.co.capitecbank.exception.FieldEncryptionException;
import za.co.capitecbank.security.FieldEncryptionService;

@Slf4j
@Service
@RequiredArgsConstructor
public class AesGcmFieldEncryptionService implements FieldEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EncryptionProperties encryptionProperties;

    // Encrypts a plain-text string using AES-GCM — a random IV is generated for every call
    // and prepended to the output so each encrypted value is unique even for the same input
    @Override
    public String encrypt(final String plaintext) {
        try {
            // Generate a fresh random IV for this encryption
            final byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            final Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, buildKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            final byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Store IV + ciphertext together so decrypt can extract the IV
            final byte[] combined = ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            log.error("Encryption failed", ex);
            throw new FieldEncryptionException("Encryption failed", ex);
        }
    }

    // Decrypts a value that was produced by encrypt() — extracts the IV from the first 12 bytes
    @Override
    public String decrypt(final String ciphertext) {
        try {
            final byte[] combined = Base64.getDecoder().decode(ciphertext);
            final ByteBuffer buffer = ByteBuffer.wrap(combined);

            // Split the stored bytes back into IV and encrypted payload
            final byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            final byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            final Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, buildKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.error("Decryption failed", ex);
            throw new FieldEncryptionException("Decryption failed", ex);
        }
    }

    // Builds a 256-bit AES key from the configured key string — pads or truncates to exactly 32 bytes
    private SecretKeySpec buildKey() {
        final byte[] keyBytes = encryptionProperties.key().getBytes(StandardCharsets.UTF_8);
        final byte[] key = new byte[32];
        System.arraycopy(keyBytes, 0, key, 0, Math.min(keyBytes.length, 32));
        return new SecretKeySpec(key, "AES");
    }
}
