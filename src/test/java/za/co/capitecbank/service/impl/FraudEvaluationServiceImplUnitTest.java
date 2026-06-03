package za.co.capitecbank.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.capitecbank.exception.FraudEvaluationException;
import za.co.capitecbank.fraudrule.FraudRule;
import za.co.capitecbank.fraudrule.context.RuleContext;
import za.co.capitecbank.fraudrule.context.RuleContextFactory;
import za.co.capitecbank.persistence.FraudPersistenceService;
import za.co.capitecbank.persistence.entity.FraudAlertEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;

@ExtendWith(MockitoExtension.class)
class FraudEvaluationServiceImplUnitTest {

    private static final String CLIENT_ID = "CLIENT-001";
    private static final String RULE_CODE = "RULE-001";

    @Mock
    private FraudRule fraudRule;

    @Mock
    private RuleContextFactory ruleContextFactory;

    @Mock
    private FraudPersistenceService fraudPersistenceService;

    @Mock
    private RuleContext ruleContext;

    private SimpleMeterRegistry meterRegistry;
    private FraudEvaluationServiceImpl service;
    private TransactionEntity transaction;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new FraudEvaluationServiceImpl(
                List.of(fraudRule), ruleContextFactory, fraudPersistenceService, meterRegistry);
        transaction = TransactionEntity.builder().clientId(CLIENT_ID).build();
        when(ruleContextFactory.create(any(), anyString())).thenReturn(ruleContext);
        when(fraudRule.getRuleCode()).thenReturn(RULE_CODE);
    }

    @Test
    void evaluate_allRulesEnabled_shouldCollectAndPersistAlerts() {
        final FraudAlertEntity alert = FraudAlertEntity.builder()
                .alertId(UUID.randomUUID())
                .clientId(CLIENT_ID)
                .build();
        when(fraudRule.isEnabled()).thenReturn(true);
        when(fraudRule.isApplicable(transaction)).thenReturn(true);
        when(fraudRule.evaluate(transaction, ruleContext)).thenReturn(Optional.of(alert));
        when(fraudPersistenceService.saveAlert(any(), anyString())).thenReturn(alert);

        final List<FraudAlertEntity> results = service.evaluate(transaction);

        assertThat(results).hasSize(1);
        verify(fraudPersistenceService).saveAlert(any(), anyString());
    }

    @Test
    void evaluate_disabledRule_shouldBeSkipped() {
        when(fraudRule.isEnabled()).thenReturn(false);

        final List<FraudAlertEntity> results = service.evaluate(transaction);

        assertThat(results).isEmpty();
        verify(fraudRule, never()).isApplicable(any());
        verify(fraudRule, never()).evaluate(any(), any());
    }

    @Test
    void evaluate_notApplicableRule_shouldBeSkipped() {
        when(fraudRule.isEnabled()).thenReturn(true);
        when(fraudRule.isApplicable(transaction)).thenReturn(false);

        final List<FraudAlertEntity> results = service.evaluate(transaction);

        assertThat(results).isEmpty();
        verify(fraudRule, never()).evaluate(any(), any());
    }

    @Test
    void evaluate_ruleThrowsException_shouldWrapInFraudEvaluationException() {
        when(fraudRule.isEnabled()).thenReturn(true);
        when(fraudRule.isApplicable(transaction)).thenReturn(true);
        when(fraudRule.evaluate(transaction, ruleContext)).thenThrow(new RuntimeException("unexpected"));

        assertThatThrownBy(() -> service.evaluate(transaction))
                .isInstanceOf(FraudEvaluationException.class)
                .hasMessageContaining("RULE-001");
    }

    @Test
    void evaluate_shouldIncrementAlertCounter_perRuleCode() {
        final FraudAlertEntity alert = FraudAlertEntity.builder()
                .alertId(UUID.randomUUID())
                .clientId(CLIENT_ID)
                .build();
        when(fraudRule.isEnabled()).thenReturn(true);
        when(fraudRule.isApplicable(transaction)).thenReturn(true);
        when(fraudRule.evaluate(transaction, ruleContext)).thenReturn(Optional.of(alert));
        when(fraudPersistenceService.saveAlert(any(), anyString())).thenReturn(alert);

        service.evaluate(transaction);

        assertThat(meterRegistry
                        .counter("fraud.alerts.generated", "ruleCode", RULE_CODE)
                        .count())
                .isEqualTo(1.0);
    }
}
