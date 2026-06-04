package za.co.capitecbank.fraudrule.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
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
import za.co.capitecbank.persistence.entity.ClientProfileEntity;
import za.co.capitecbank.persistence.entity.FraudAlertEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SuspiciousSpendRuleUnitTest {

    @Mock
    private RuleEngineConfig ruleEngineConfig;

    @Mock
    private FraudAlertFactory fraudAlertFactory;

    @Mock
    private RuleContext ruleContext;

    @InjectMocks
    private SuspiciousSpendRule rule;

    private TransactionEntity transaction;

    @BeforeEach
    void setUp() {
        transaction = TransactionEntity.builder()
                .clientId("CLIENT-001")
                .amount(new BigDecimal("35000.00"))
                .build();
        when(ruleEngineConfig.rule003Enabled()).thenReturn(true);
    }

    @Test
    void evaluate_amountExceeds300PercentOfIncome_shouldGenerateAlert() {
        final ClientProfileEntity profile = ClientProfileEntity.builder()
                .averageMonthlyIncome(new BigDecimal("10000.00"))
                .build();
        when(ruleContext.getClientProfile()).thenReturn(Optional.of(profile));
        when(fraudAlertFactory.create(any(), any(), any(), any(int.class), any(), any()))
                .thenReturn(FraudAlertEntity.builder().build());

        assertThat(rule.evaluate(transaction, ruleContext)).isPresent();
    }

    @Test
    void evaluate_amountBelow300PercentOfIncome_shouldReturnEmpty() {
        final ClientProfileEntity profile = ClientProfileEntity.builder()
                .averageMonthlyIncome(new BigDecimal("20000.00"))
                .build();
        when(ruleContext.getClientProfile()).thenReturn(Optional.of(profile));

        assertThat(rule.evaluate(transaction, ruleContext)).isEmpty();
    }

    @Test
    void evaluate_profileAbsent_shouldReturnEmpty() {
        when(ruleContext.getClientProfile()).thenReturn(Optional.empty());

        assertThat(rule.evaluate(transaction, ruleContext)).isEmpty();
    }

    @Test
    void evaluate_zeroIncome_shouldReturnEmpty() {
        final ClientProfileEntity profile = ClientProfileEntity.builder()
                .averageMonthlyIncome(BigDecimal.ZERO)
                .build();
        when(ruleContext.getClientProfile()).thenReturn(Optional.of(profile));

        assertThat(rule.evaluate(transaction, ruleContext)).isEmpty();
    }

    @Test
    void isApplicable_shouldAlwaysReturnTrue() {
        assertThat(rule.isApplicable(transaction)).isTrue();
    }
}
