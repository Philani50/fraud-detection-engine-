package za.co.capitecbank.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.capitecbank.security.FieldEncryptionService;

@ExtendWith(MockitoExtension.class)
class EncryptedStringConverterUnitTest {

    @Mock
    private FieldEncryptionService fieldEncryptionService;

    @InjectMocks
    private EncryptedStringConverter converter;

    @Test
    void convertToDatabaseColumn_shouldEncrypt() {
        when(fieldEncryptionService.encrypt("plaintext")).thenReturn("ciphertext");
        assertThat(converter.convertToDatabaseColumn("plaintext")).isEqualTo("ciphertext");
        verify(fieldEncryptionService).encrypt("plaintext");
    }

    @Test
    void convertToEntityAttribute_shouldDecrypt() {
        when(fieldEncryptionService.decrypt("ciphertext")).thenReturn("plaintext");
        assertThat(converter.convertToEntityAttribute("ciphertext")).isEqualTo("plaintext");
        verify(fieldEncryptionService).decrypt("ciphertext");
    }

    @Test
    void convertToDatabaseColumn_whenNull_shouldReturnNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_whenNull_shouldReturnNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
