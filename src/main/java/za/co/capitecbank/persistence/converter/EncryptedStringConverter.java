package za.co.capitecbank.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.capitecbank.security.FieldEncryptionService;

@Converter(autoApply = false)
@Component
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final FieldEncryptionService fieldEncryptionService;

    // Called by JPA before writing to the database — encrypts the value so PII is never stored in plain text
    @Override
    public String convertToDatabaseColumn(final String plaintext) {
        if (plaintext == null) {
            return null;
        }
        return fieldEncryptionService.encrypt(plaintext);
    }

    // Called by JPA after reading from the database — decrypts the value back to plain text for use in code
    @Override
    public String convertToEntityAttribute(final String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        return fieldEncryptionService.decrypt(ciphertext);
    }
}
