package za.co.capitecbank.fraudrule.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import za.co.capitecbank.config.RuleEngineConfig;
import za.co.capitecbank.enums.AlertSeverity;
import za.co.capitecbank.enums.TransactionType;
import za.co.capitecbank.fraudrule.FraudRule;
import za.co.capitecbank.fraudrule.context.RuleContext;
import za.co.capitecbank.fraudrule.util.FraudAlertFactory;
import za.co.capitecbank.fraudrule.util.TimeWindowCalculator;
import za.co.capitecbank.persistence.entity.FraudAlertEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class StructuringRule implements FraudRule {

    private static final String RULE_CODE = "RULE-001";
    private static final String RULE_NAME = "Structuring Over Time";
    private static final BigDecimal THRESHOLD_AMOUNT = new BigDecimal("10000");
    private static final int MIN_TRANSACTION_COUNT = 3;
    private static final int WINDOW_HOURS = 24;
    private static final String DETAIL_TEMPLATE =
            "Structuring detected: %d cash transactions totalling %s ZAR within 24 hours, each below R10,000.";

    private final RuleEngineConfig ruleEngineConfig;
    private final FraudAlertFactory fraudAlertFactory;
    private final TimeWindowCalculator timeWindowCalculator;

    @Override
    public String getRuleCode() {
        return RULE_CODE;
    }

    @Override
    public String getRuleName() {
        return RULE_NAME;
    }

    @Override
    public boolean isEnabled() {
        return ruleEngineConfig.rule001Enabled();
    }

    @Override
    public boolean isApplicable(final TransactionEntity transaction) {
        return transaction.getTransactionType() == TransactionType.DEPOSIT
                || transaction.getTransactionType() == TransactionType.WITHDRAWAL;
    }

    // RULE-001: Structuring Over Time
    // Flags clients who make multiple small cash transactions that individually stay below R10,000
    // but together add up to a suspicious total — a known method used to avoid detection thresholds
    @Override
    public Optional<FraudAlertEntity> evaluate(final TransactionEntity transaction, final RuleContext context) {
        log.debug("Evaluating [rule={}]", RULE_CODE);

        // Fetch all cash transactions in the last 24 hours below the threshold
        final List<TransactionEntity> cashTransactions =
                context.getCashTransactionsSince(timeWindowCalculator.hoursAgo(WINDOW_HOURS), THRESHOLD_AMOUNT);

        if (cashTransactions.size() < MIN_TRANSACTION_COUNT) {
            return Optional.empty();
        }

        // Add up the total — alert only if the combined amount is also above the threshold
        final BigDecimal total =
                cashTransactions.stream().map(TransactionEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(THRESHOLD_AMOUNT) < 0) {
            return Optional.empty();
        }

        log.info("Alert raised [rule={}, clientId={}]", RULE_CODE, transaction.getClientId());
        return Optional.of(fraudAlertFactory.create(
                transaction,
                RULE_CODE,
                RULE_NAME,
                75,
                AlertSeverity.HIGH,
                String.format(DETAIL_TEMPLATE, cashTransactions.size(), total.toPlainString())));
    }
}
