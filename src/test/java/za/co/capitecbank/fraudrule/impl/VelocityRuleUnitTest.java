package za.co.capitecbank.fraudrule.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.capitecbank.config.RuleEngineConfig;
import za.co.capitecbank.fraudrule.context.RuleContext;
import za.co.capitecbank.fraudrule.util.FraudAlertFactory;
import za.co.capitecbank.fraudrule.util.TimeWindowCalculator;
import za.co.capitecbank.persistence.entity.FraudAlertEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VelocityRuleUnitTest {

    @Mock
    private RuleEngineConfig ruleEngineConfig;

    @Mock
    private FraudAlertFactory fraudAlertFactory;

    @Mock
    private TimeWindowCalculator timeWindowCalculator;

    @Mock
    private RuleContext ruleContext;

    @InjectMocks
    private VelocityRule rule;

    private TransactionEntity transaction;

    @BeforeEach
    void setUp() {
        transaction = TransactionEntity.builder().clientId("CLIENT-001").build();
        when(ruleEngineConfig.rule004Enabled()).thenReturn(true);
        when(timeWindowCalculator.minutesAgo(60)).thenReturn(LocalDateTime.now().minusMinutes(60));
    }

    @Test
    void evaluate_tenOrMoreTransactions_shouldGenerateAlert() {
        when(ruleContext.getTransactionCountSince(any())).thenReturn(10L);
        when(fraudAlertFactory.create(any(), any(), any(), any(int.class), any(), any()))
                .thenReturn(FraudAlertEntity.builder().build());

        assertThat(rule.evaluate(transaction, ruleContext)).isPresent();
    }

    @Test
    void evaluate_nineTransactions_shouldReturnEmpty() {
        when(ruleContext.getTransactionCountSince(any())).thenReturn(9L);

        assertThat(rule.evaluate(transaction, ruleContext)).isEmpty();
    }

    @Test
    void isApplicable_shouldAlwaysReturnTrue() {
        assertThat(rule.isApplicable(transaction)).isTrue();
    }
}
