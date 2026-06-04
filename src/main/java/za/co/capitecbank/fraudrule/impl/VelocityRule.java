package za.co.capitecbank.fraudrule.impl;

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
@Order(4)
public class VelocityRule implements FraudRule {

    private static final String RULE_CODE = "RULE-004";
    private static final String RULE_NAME = "Velocity Spike";
    private static final int VELOCITY_THRESHOLD = 10;
    private static final int WINDOW_MINUTES = 60;
    private static final String DETAIL_TEMPLATE = "Velocity spike detected: %d transactions within the last 1 hour.";

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
        return ruleEngineConfig.rule004Enabled();
    }

    @Override
    public boolean isApplicable(final TransactionEntity transaction) {
        return true;
    }

    // RULE-004: Velocity Spike
    // Flags a client who makes 10 or more transactions within a single hour —
    // an unusually high transaction rate that may indicate automated or fraudulent activity
    @Override
    public Optional<FraudAlertEntity> evaluate(final TransactionEntity transaction, final RuleContext context) {
        log.debug("Evaluating [rule={}]", RULE_CODE);

        // Count all transactions in the last 60 minutes and alert if the threshold is breached
        final long count = context.getTransactionCountSince(timeWindowCalculator.minutesAgo(WINDOW_MINUTES));

        if (count < VELOCITY_THRESHOLD) {
            return Optional.empty();
        }

        log.info("Alert raised [rule={}, clientId={}]", RULE_CODE, transaction.getClientId());
        return Optional.of(fraudAlertFactory.create(
                transaction, RULE_CODE, RULE_NAME, 70, AlertSeverity.HIGH, String.format(DETAIL_TEMPLATE, count)));
    }
}
