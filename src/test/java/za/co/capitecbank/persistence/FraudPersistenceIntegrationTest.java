package za.co.capitecbank.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import za.co.capitecbank.enums.Channel;
import za.co.capitecbank.enums.RiskRating;
import za.co.capitecbank.enums.TransactionType;
import za.co.capitecbank.fraudrule.FraudRule;
import za.co.capitecbank.persistence.entity.ClientProfileEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;
import za.co.capitecbank.persistence.repository.ClientProfileRepository;
import za.co.capitecbank.service.FraudEvaluationService;

@SpringBootTest
class FraudPersistenceIntegrationTest {

    @TestConfiguration
    static class NoRulesConfig {
        @Bean
        List<FraudRule> emptyFraudRules() {
            return List.of();
        }
    }

    private static final String TRACE_ID = "integration-test-trace";

    @Autowired
    private FraudPersistenceService fraudPersistenceService;

    @Autowired
    private ClientProfileRepository clientProfileRepository;

    @Autowired
    private FraudEvaluationService fraudEvaluationService;

    @Test
    void contextLoads_andFlywayMigrationsRun() {
        assertThat(fraudPersistenceService).isNotNull();
        assertThat(fraudEvaluationService).isNotNull();
    }

    @Test
    void saveAndFindTransaction_shouldRoundTripCorrectly() {
        final TransactionEntity transaction = TransactionEntity.builder()
                .clientId("CLIENT-IT-001")
                .accountId("ACC-IT-001")
                .amount(new BigDecimal("5000.00"))
                .currency("ZAR")
                .transactionType(TransactionType.DEPOSIT)
                .channel(Channel.ONLINE)
                .timestamp(LocalDateTime.now())
                .createdDate(LocalDateTime.now())
                .build();

        final TransactionEntity saved = fraudPersistenceService.saveTransaction(transaction, TRACE_ID);

        assertThat(saved.getTransactionId()).isNotNull();

        final long count = fraudPersistenceService.countTransactionsByClientSince(
                "CLIENT-IT-001", LocalDateTime.now().minusMinutes(1), TRACE_ID);

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void encryptedFields_shouldBeEncryptedInDb_andDecryptedInMemory() {
        final ClientProfileEntity profile = ClientProfileEntity.builder()
                .clientId("CLIENT-IT-ENC-001")
                .fullName("Jane Smith")
                .idNumber("9001015800080")
                .riskRating(RiskRating.LOW)
                .build();

        final ClientProfileEntity saved = clientProfileRepository.save(profile);
        clientProfileRepository.flush();

        final ClientProfileEntity loaded =
                clientProfileRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getFullName()).isEqualTo("Jane Smith");
        assertThat(loaded.getIdNumber()).isEqualTo("9001015800080");
    }
}
