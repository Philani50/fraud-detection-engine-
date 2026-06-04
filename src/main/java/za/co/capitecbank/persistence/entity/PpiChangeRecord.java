package za.co.capitecbank.persistence.entity;

import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.co.capitecbank.persistence.converter.EncryptedStringConverter;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class PpiChangeRecord {

    private String fieldChanged;

    @Convert(converter = EncryptedStringConverter.class)
    private String oldValue;

    @Convert(converter = EncryptedStringConverter.class)
    private String newValue;

    private LocalDateTime changeDate;
}
