package za.co.capitecbank.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.capitecbank.enums.Channel;
import za.co.capitecbank.enums.TransactionType;

class TransactionEntityUnitTest {

    @Test
    void builder_shouldSetAllFields() {
        final UUID id = UUID.randomUUID();
        final LocalDateTime now = LocalDateTime.now();

        final TransactionEntity entity = TransactionEntity.builder()
                .transactionId(id)
                .clientId("CLIENT-001")
                .accountId("ACC-001")
                .amount(new BigDecimal("1500.00"))
                .currency("ZAR")
                .transactionType(TransactionType.DEPOSIT)
                .channel(Channel.ONLINE)
                .timestamp(now)
                .build();

        assertThat(entity.getTransactionId()).isEqualTo(id);
        assertThat(entity.getClientId()).isEqualTo("CLIENT-001");
        assertThat(entity.getAccountId()).isEqualTo("ACC-001");
        assertThat(entity.getAmount()).isEqualByComparingTo("1500.00");
        assertThat(entity.getCurrency()).isEqualTo("ZAR");
        assertThat(entity.getTransactionType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(entity.getChannel()).isEqualTo(Channel.ONLINE);
        assertThat(entity.getTimestamp()).isEqualTo(now);
    }

    @Test
    void version_shouldBePresent() {
        final TransactionEntity entity = TransactionEntity.builder().build();
        assertThat(entity.getVersion()).isZero();
    }
}
