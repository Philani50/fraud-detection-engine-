package za.co.capitecbank.security.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.capitecbank.config.EncryptionProperties;

class AesGcmFieldEncryptionServiceUnitTest {

    private AesGcmFieldEncryptionService service;

    @BeforeEach
    void setUp() {
        service = new AesGcmFieldEncryptionService(new EncryptionProperties("test-aes-256-key-32byteslong!!!!!"));
    }

    @Test
    void encrypt_shouldProduceCiphertextDifferentFromPlaintext() {
        final String plaintext = "sensitive-value";
        final String ciphertext = service.encrypt(plaintext);
        assertThat(ciphertext).isNotEqualTo(plaintext);
        assertThat(ciphertext).isNotBlank();
    }

    @Test
    void decrypt_shouldRecoverOriginalPlaintext() {
        final String plaintext = "sensitive-value";
        final String ciphertext = service.encrypt(plaintext);
        assertThat(service.decrypt(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    void encryptDecrypt_roundTrip_shouldBeIdempotent() {
        final String plaintext = "round-trip-test-12345";
        assertThat(service.decrypt(service.encrypt(plaintext))).isEqualTo(plaintext);
    }

    @Test
    void encrypt_shouldProduceUniqueOutputPerCall() {
        final String plaintext = "same-input";
        final String first = service.encrypt(plaintext);
        final String second = service.encrypt(plaintext);
        assertThat(first).isNotEqualTo(second);
    }
}
