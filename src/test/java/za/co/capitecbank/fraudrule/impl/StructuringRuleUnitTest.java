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
class StructuringRuleUnitTest {

    @Mock
    private RuleEngineConfig ruleEngineConfig;

    @Mock
    private FraudAlertFactory fraudAlertFactory;

    @Mock
    private TimeWindowCalculator timeWindowCalculator;

    @Mock
    private RuleContext ruleContext;

    @InjectMocks
    private StructuringRule rule;

    private TransactionEntity transaction;

    @BeforeEach
    void setUp() {
        transaction = TransactionEntity.builder()
                .clientId("CLIENT-001")
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("4000.00"))
                .build();
        when(ruleEngineConfig.rule001Enabled()).thenReturn(true);
        when(timeWindowCalculator.hoursAgo(24)).thenReturn(LocalDateTime.now().minusHours(24));
    }

    @Test
    void evaluate_threeTransactionsTotallingOverThreshold_shouldGenerateAlert() {
        final List<TransactionEntity> transactions = List.of(buildTx("4000"), buildTx("4000"), buildTx("4000"));
        when(ruleContext.getCashTransactionsSince(any(), any())).thenReturn(transactions);
        when(fraudAlertFactory.create(any(), any(), any(), any(int.class), any(), any()))
                .thenReturn(FraudAlertEntity.builder().build());

        assertThat(rule.evaluate(transaction, ruleContext)).isPresent();
    }

    @Test
    void evaluate_onlyTwoTransactions_shouldReturnEmpty() {
        when(ruleContext.getCashTransactionsSince(any(), any())).thenReturn(List.of(buildTx("4000"), buildTx("4000")));

        assertThat(rule.evaluate(transaction, ruleContext)).isEmpty();
    }

    @Test
    void evaluate_threeTransactionsBelowTotalThreshold_shouldReturnEmpty() {
        when(ruleContext.getCashTransactionsSince(any(), any()))
                .thenReturn(List.of(buildTx("1000"), buildTx("1000"), buildTx("1000")));

        assertThat(rule.evaluate(transaction, ruleContext)).isEmpty();
    }

    @Test
    void isApplicable_deposit_shouldReturnTrue() {
        assertThat(rule.isApplicable(transaction)).isTrue();
    }

    @Test
    void isApplicable_cardPurchase_shouldReturnFalse() {
        transaction = TransactionEntity.builder()
                .transactionType(TransactionType.CARD_PURCHASE)
                .build();
        assertThat(rule.isApplicable(transaction)).isFalse();
    }

    @Test
    void isEnabled_whenDisabled_shouldReturnFalse() {
        when(ruleEngineConfig.rule001Enabled()).thenReturn(false);
        assertThat(rule.isEnabled()).isFalse();
    }

    private TransactionEntity buildTx(final String amount) {
        return TransactionEntity.builder().amount(new BigDecimal(amount)).build();
    }
}
