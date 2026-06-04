package za.co.capitecbank.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import za.co.capitecbank.enums.Channel;
import za.co.capitecbank.enums.TransactionType;
import za.co.capitecbank.mapper.FraudMapper;
import za.co.capitecbank.model.request.FraudEvaluationRequest;
import za.co.capitecbank.model.response.FraudEvaluationResponse;
import za.co.capitecbank.persistence.FraudPersistenceService;
import za.co.capitecbank.persistence.entity.TransactionEntity;
import za.co.capitecbank.service.FraudEvaluationService;

@ExtendWith(MockitoExtension.class)
class FraudControllerUnitTest {

    @Mock
    private FraudEvaluationService fraudEvaluationService;

    @Mock
    private FraudPersistenceService fraudPersistenceService;

    @Mock
    private FraudMapper fraudMapper;

    private FraudController controller;

    @BeforeEach
    void setUp() {
        controller = new FraudController(fraudEvaluationService, fraudPersistenceService, fraudMapper);
    }

    @Test
    void evaluateFraud_validRequest_shouldReturn200() {
        final FraudEvaluationRequest request = new FraudEvaluationRequest(
                "ACC-001",
                "CLIENT-001",
                new BigDecimal("1000.00"),
                "ZAR",
                TransactionType.DEPOSIT,
                Channel.ONLINE,
                null,
                null,
                LocalDateTime.now(),
                null);

        final TransactionEntity transaction = TransactionEntity.builder()
                .transactionId(UUID.randomUUID())
                .clientId("CLIENT-001")
                .build();

        final FraudEvaluationResponse response = FraudEvaluationResponse.builder()
                .transactionId(transaction.getTransactionId())
                .clientId("CLIENT-001")
                .alertCount(0)
                .alerts(List.of())
                .evaluatedAt(LocalDateTime.now())
                .build();

        when(fraudMapper.toEntity(request)).thenReturn(transaction);
        when(fraudPersistenceService.saveTransaction(any(), anyString())).thenReturn(transaction);
        when(fraudEvaluationService.evaluate(transaction)).thenReturn(List.of());
        when(fraudMapper.toEvaluationResponse(any(), any())).thenReturn(response);

        final var result = controller.evaluateFraud(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().alertCount()).isZero();
    }
}
