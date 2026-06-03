package za.co.capitecbank.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import za.co.capitecbank.enums.RiskRating;

class ClientProfileEntityUnitTest {

    @Test
    void builder_shouldSetAllFields() {
        final ClientProfileEntity entity = ClientProfileEntity.builder()
                .clientId("CLIENT-001")
                .fullName("John Doe")
                .idNumber("8501015800085")
                .accountOpenDate(LocalDate.of(2020, 1, 15))
                .averageMonthlyIncome(new BigDecimal("50000.00"))
                .riskRating(RiskRating.LOW)
                .build();

        assertThat(entity.getClientId()).isEqualTo("CLIENT-001");
        assertThat(entity.getFullName()).isEqualTo("John Doe");
        assertThat(entity.getIdNumber()).isEqualTo("8501015800085");
        assertThat(entity.getRiskRating()).isEqualTo(RiskRating.LOW);
        assertThat(entity.getAverageMonthlyIncome()).isEqualByComparingTo("50000.00");
    }

    @Test
    void ppiChangeHistory_shouldBeEmptyByDefault() {
        final ClientProfileEntity entity = ClientProfileEntity.builder().build();
        assertThat(entity.getPpiChangeHistory()).isNotNull().isEmpty();
    }

    @Test
    void version_shouldBePresent() {
        final ClientProfileEntity entity = ClientProfileEntity.builder().build();
        assertThat(entity.getVersion()).isZero();
    }
}
