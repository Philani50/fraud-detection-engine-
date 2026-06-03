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
import za.co.capitecbank.fraudrule.context.RuleContext;
import za.co.capitecbank.fraudrule.util.FraudAlertFactory;
import za.co.capitecbank.fraudrule.util.TimeWindowCalculator;
import za.co.capitecbank.persistence.entity.FraudAlertEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoundAmountRuleUnitTest {

    @Mock
    private RuleEngineConfig ruleEngineConfig;

    @Mock
    private FraudAlertFactory fraudAlertFactory;

    @Mock
    private TimeWindowCalculator timeWindowCalculator;

    @Mock
    private RuleContext ruleContext;

    @InjectMocks
    private RoundAmountRule rule;

    private TransactionEntity transaction;

    @BeforeEach
    void setUp() {
        transaction = TransactionEntity.builder()
                .clientId("CLIENT-001")
                .amount(new BigDecimal("5000.00"))
                .build();
        when(ruleEngineConfig.rule005Enabled()).thenReturn(true);
        when(timeWindowCalculator.hoursAgo(24)).thenReturn(LocalDateTime.now().minusHours(24));
    }

    @Test
    void evaluate_threeRoundAmountTransactions_shouldGenerateAlert() {
        final List<TransactionEntity> txns = List.of(buildRoundTx("5000"), buildRoundTx("3000"), buildRoundTx("2000"));
        when(ruleContext.getCashTransactionsSince(any(), any())).thenReturn(txns);
        when(fraudAlertFactory.create(any(), any(), any(), any(int.class), any(), any()))
                .thenReturn(FraudAlertEntity.builder().build());

        assertThat(rule.evaluate(transaction, ruleContext)).isPresent();
    }

    @Test
    void evaluate_fewerThanThreeRoundTransactions_shouldReturnEmpty() {
        when(ruleContext.getCashTransactionsSince(any(), any()))
                .thenReturn(List.of(buildRoundTx("5000"), buildRoundTx("3000")));

        assertThat(rule.evaluate(transaction, ruleContext)).isEmpty();
    }

    @Test
    void isApplicable_roundAmount_shouldReturnTrue() {
        assertThat(rule.isApplicable(transaction)).isTrue();
    }

    @Test
    void isApplicable_nonRoundAmount_shouldReturnFalse() {
        final TransactionEntity nonRound =
                TransactionEntity.builder().amount(new BigDecimal("1234.56")).build();
        assertThat(rule.isApplicable(nonRound)).isFalse();
    }

    private TransactionEntity buildRoundTx(final String amount) {
        return TransactionEntity.builder().amount(new BigDecimal(amount)).build();
    }
}
