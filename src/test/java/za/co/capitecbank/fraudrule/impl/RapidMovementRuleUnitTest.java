package za.co.capitecbank.fraudrule.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.capitecbank.config.RuleEngineConfig;
import za.co.capitecbank.enums.TransactionType;
import za.co.capitecbank.fraudrule.context.RuleContext;
import za.co.capitecbank.fraudrule.util.FraudAlertFactory;
import za.co.capitecbank.fraudrule.util.TimeWindowCalculator;
import za.co.capitecbank.persistence.entity.FraudAlertEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RapidMovementRuleUnitTest {

    @Mock
    private RuleEngineConfig ruleEngineConfig;

    @Mock
    private FraudAlertFactory fraudAlertFactory;

    @Mock
    private TimeWindowCalculator timeWindowCalculator;

    @Mock
    private RuleContext ruleContext;

    @InjectMocks
    private RapidMovementRule rule;

    private TransactionEntity withdrawal;

    @BeforeEach
    void setUp() {
        withdrawal = TransactionEntity.builder()
                .clientId("CLIENT-001")
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("22000.00"))
                .build();
        when(ruleEngineConfig.rule006Enabled()).thenReturn(true);
        when(timeWindowCalculator.minutesAgo(30)).thenReturn(LocalDateTime.now().minusMinutes(30));
    }

    @Test
    void evaluate_outboundExceeds80PercentOfInbound_shouldGenerateAlert() {
        final TransactionEntity deposit = TransactionEntity.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("25000.00"))
                .build();
        when(ruleContext.getTransactionsBetween(any(), any())).thenReturn(List.of(deposit));
        when(fraudAlertFactory.create(any(), any(), any(), any(int.class), any(), any()))
                .thenReturn(FraudAlertEntity.builder().build());

        assertThat(rule.evaluate(withdrawal, ruleContext)).isPresent();
    }

    @Test
    void evaluate_outboundBelow80PercentOfInbound_shouldReturnEmpty() {
        final TransactionEntity deposit = TransactionEntity.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("50000.00"))
                .build();
        when(ruleContext.getTransactionsBetween(any(), any())).thenReturn(List.of(deposit));

        assertThat(rule.evaluate(withdrawal, ruleContext)).isEmpty();
    }

    @Test
    void evaluate_noInboundInWindow_shouldReturnEmpty() {
        when(ruleContext.getTransactionsBetween(any(), any())).thenReturn(List.of());

        assertThat(rule.evaluate(withdrawal, ruleContext)).isEmpty();
    }

    @Test
    void evaluate_inboundBelowThreshold_shouldReturnEmpty() {
        final TransactionEntity smallDeposit = TransactionEntity.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("5000.00"))
                .build();
        when(ruleContext.getTransactionsBetween(any(), any())).thenReturn(List.of(smallDeposit));

        assertThat(rule.evaluate(withdrawal, ruleContext)).isEmpty();
    }

    @Test
    void isApplicable_withdrawal_shouldReturnTrue() {
        assertThat(rule.isApplicable(withdrawal)).isTrue();
    }

    @Test
    void isApplicable_deposit_shouldReturnFalse() {
        final TransactionEntity deposit = TransactionEntity.builder()
                .transactionType(TransactionType.DEPOSIT)
                .build();
        assertThat(rule.isApplicable(deposit)).isFalse();
    }
}
