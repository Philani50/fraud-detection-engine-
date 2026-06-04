package za.co.capitecbank.security;

public interface FieldEncryptionService {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
