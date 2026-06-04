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
import za.co.capitecbank.fraudrule.FraudRule;
import za.co.capitecbank.fraudrule.context.RuleContext;
import za.co.capitecbank.fraudrule.util.FraudAlertFactory;
import za.co.capitecbank.fraudrule.util.TimeWindowCalculator;
import za.co.capitecbank.persistence.entity.FraudAlertEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(5)
public class RoundAmountRule implements FraudRule {

    private static final String RULE_CODE = "RULE-005";
    private static final String RULE_NAME = "Round Amount Pattern";
    private static final BigDecimal ROUND_AMOUNT_DIVISOR = new BigDecimal("1000");
    private static final int MIN_ROUND_COUNT = 3;
    private static final int WINDOW_HOURS = 24;
    private static final String DETAIL_TEMPLATE =
            "Round amount pattern detected: %d transactions with amounts divisible by R1,000 within 24 hours.";

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
        return ruleEngineConfig.rule005Enabled();
    }

    @Override
    public boolean isApplicable(final TransactionEntity transaction) {
        return transaction.getAmount().remainder(ROUND_AMOUNT_DIVISOR).compareTo(BigDecimal.ZERO) == 0;
    }

    // RULE-005: Round Amount Pattern
    // Flags clients who repeatedly transact in round amounts (divisible by R1,000) —
    // a pattern sometimes used to test stolen card limits or move money in predictable increments
    @Override
    public Optional<FraudAlertEntity> evaluate(final TransactionEntity transaction, final RuleContext context) {
        log.debug("Evaluating [rule={}]", RULE_CODE);

        // Fetch all transactions in the last 24 hours (no upper-amount filter needed here)
        final List<TransactionEntity> allTransactions = context.getCashTransactionsSince(
                timeWindowCalculator.hoursAgo(WINDOW_HOURS), BigDecimal.valueOf(Long.MAX_VALUE));

        // Count only the ones with amounts exactly divisible by R1,000
        final long roundCount = allTransactions.stream()
                .filter(tx -> tx.getAmount().remainder(ROUND_AMOUNT_DIVISOR).compareTo(BigDecimal.ZERO) == 0)
                .count();

        if (roundCount < MIN_ROUND_COUNT) {
            return Optional.empty();
        }

        log.info("Alert raised [rule={}, clientId={}]", RULE_CODE, transaction.getClientId());
        return Optional.of(fraudAlertFactory.create(
                transaction,
                RULE_CODE,
                RULE_NAME,
                55,
                AlertSeverity.MEDIUM,
                String.format(DETAIL_TEMPLATE, roundCount)));
    }
}
