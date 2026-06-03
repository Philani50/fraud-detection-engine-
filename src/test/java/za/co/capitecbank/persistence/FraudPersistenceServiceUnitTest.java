package za.co.capitecbank.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import za.co.capitecbank.exception.FraudEvaluationException;
import za.co.capitecbank.persistence.entity.FraudAlertEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;
import za.co.capitecbank.persistence.repository.ClientProfileRepository;
import za.co.capitecbank.persistence.repository.FraudAlertRepository;
import za.co.capitecbank.persistence.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class FraudPersistenceServiceUnitTest {

    private static final String TRACE_ID = "test-trace-id";
    private static final String CLIENT_ID = "CLIENT-001";

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ClientProfileRepository clientProfileRepository;

    @Mock
    private FraudAlertRepository fraudAlertRepository;

    @InjectMocks
    private FraudPersistenceService service;

    @Test
    void countTransactionsByClientSince_shouldDelegateToRepository() {
        final LocalDateTime after = LocalDateTime.now().minusHours(1);
        when(transactionRepository.countByClientIdAndTimestampAfter(CLIENT_ID, after))
                .thenReturn(5L);

        assertThat(service.countTransactionsByClientSince(CLIENT_ID, after, TRACE_ID))
                .isEqualTo(5L);
    }

    @Test
    void findCashTransactionsByClientSince_shouldDelegateToRepository() {
        when(transactionRepository.findByClientIdAndTransactionTypeInAndTimestampAfterAndAmountLessThan(
                        any(), any(), any(), any()))
                .thenReturn(List.of());

        assertThat(service.findCashTransactionsByClientSince(
                        CLIENT_ID, List.of(), LocalDateTime.now(), java.math.BigDecimal.TEN, TRACE_ID))
                .isEmpty();
    }

    @Test
    void findClientProfile_shouldReturnEmpty_whenNotFound() {
        when(clientProfileRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.empty());

        assertThat(service.findClientProfile(CLIENT_ID, TRACE_ID)).isEmpty();
    }

    @Test
    void saveAlert_shouldPersistAndReturn() {
        final FraudAlertEntity alert = FraudAlertEntity.builder()
                .alertId(UUID.randomUUID())
                .clientId(CLIENT_ID)
                .build();
        when(fraudAlertRepository.save(alert)).thenReturn(alert);

        assertThat(service.saveAlert(alert, TRACE_ID)).isEqualTo(alert);
    }

    @Test
    void saveAlert_whenDuplicateKey_shouldThrowFraudEvaluationException() {
        final FraudAlertEntity alert =
                FraudAlertEntity.builder().clientId(CLIENT_ID).build();
        when(fraudAlertRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.saveAlert(alert, TRACE_ID))
                .isInstanceOf(FraudEvaluationException.class)
                .hasMessageContaining("Duplicate alert key violation");
    }

    @Test
    void saveTransaction_shouldPersistAndReturn() {
        final TransactionEntity transaction =
                TransactionEntity.builder().clientId(CLIENT_ID).build();
        when(transactionRepository.save(transaction)).thenReturn(transaction);

        assertThat(service.saveTransaction(transaction, TRACE_ID)).isEqualTo(transaction);
    }
}
