package za.co.capitecbank.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.capitecbank.enums.AlertSeverity;
import za.co.capitecbank.enums.AlertStatus;

class FraudAlertEntityUnitTest {

    @Test
    void builder_shouldSetAllFields() {
        final UUID alertId = UUID.randomUUID();
        final UUID transactionId = UUID.randomUUID();
        final LocalDateTime now = LocalDateTime.now();

        final FraudAlertEntity entity = FraudAlertEntity.builder()
                .alertId(alertId)
                .transactionId(transactionId)
                .clientId("CLIENT-001")
                .ruleCode("RULE-001")
                .ruleName("Structuring Over Time")
                .riskScore(75)
                .severity(AlertSeverity.HIGH)
                .status(AlertStatus.PENDING)
                .details("Structuring detected")
                .createdAt(now)
                .build();

        assertThat(entity.getAlertId()).isEqualTo(alertId);
        assertThat(entity.getTransactionId()).isEqualTo(transactionId);
        assertThat(entity.getClientId()).isEqualTo("CLIENT-001");
        assertThat(entity.getRuleCode()).isEqualTo("RULE-001");
        assertThat(entity.getSeverity()).isEqualTo(AlertSeverity.HIGH);
        assertThat(entity.getStatus()).isEqualTo(AlertStatus.PENDING);
        assertThat(entity.getRiskScore()).isEqualTo(75);
    }

    @Test
    void status_defaultShouldBePending() {
        final FraudAlertEntity entity = FraudAlertEntity.builder().build();
        assertThat(entity.getStatus()).isEqualTo(AlertStatus.PENDING);
    }

    @Test
    void version_shouldBePresent() {
        final FraudAlertEntity entity = FraudAlertEntity.builder().build();
        assertThat(entity.getVersion()).isZero();
    }
}
