package za.co.capitecbank.fraudrule.impl;

import java.math.BigDecimal;
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
import za.co.capitecbank.persistence.entity.ClientProfileEntity;
import za.co.capitecbank.persistence.entity.FraudAlertEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(3)
public class SuspiciousSpendRule implements FraudRule {

    private static final String RULE_CODE = "RULE-003";
    private static final String RULE_NAME = "Suspicious Spend Behaviour";
    private static final BigDecimal INCOME_MULTIPLIER_THRESHOLD = new BigDecimal("3");
    private static final String DETAIL_TEMPLATE =
            "Transaction amount %s ZAR exceeds 300%% of average monthly income (%s ZAR).";

    private final RuleEngineConfig ruleEngineConfig;
    private final FraudAlertFactory fraudAlertFactory;

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
        return ruleEngineConfig.rule003Enabled();
    }

    @Override
    public boolean isApplicable(final TransactionEntity transaction) {
        return true;
    }

    // RULE-003: Suspicious Spend Behaviour
    // Flags a single transaction that exceeds 300% of the client's average monthly income —
    // spending far beyond what is normal for that client may indicate stolen funds or account compromise
    @Override
    public Optional<FraudAlertEntity> evaluate(final TransactionEntity transaction, final RuleContext context) {
        log.debug("Evaluating [rule={}]", RULE_CODE);

        // Cannot evaluate without income data — skip gracefully
        final Optional<ClientProfileEntity> profileOpt = context.getClientProfile();
        if (profileOpt.isEmpty()) {
            log.warn("Client profile absent — cannot evaluate [rule={}]", RULE_CODE);
            return Optional.empty();
        }

        final ClientProfileEntity profile = profileOpt.get();
        if (profile.getAverageMonthlyIncome() == null
                || profile.getAverageMonthlyIncome().compareTo(BigDecimal.ZERO) == 0) {
            log.warn("Average monthly income absent — cannot evaluate [rule={}]", RULE_CODE);
            return Optional.empty();
        }

        // Alert if this transaction is more than 3x the client's average monthly income
        final BigDecimal threshold = profile.getAverageMonthlyIncome().multiply(INCOME_MULTIPLIER_THRESHOLD);
        if (transaction.getAmount().compareTo(threshold) <= 0) {
            return Optional.empty();
        }

        log.info("Alert raised [rule={}, clientId={}]", RULE_CODE, transaction.getClientId());
        return Optional.of(fraudAlertFactory.create(
                transaction,
                RULE_CODE,
                RULE_NAME,
                60,
                AlertSeverity.MEDIUM,
                String.format(
                        DETAIL_TEMPLATE,
                        transaction.getAmount().toPlainString(),
                        profile.getAverageMonthlyIncome().toPlainString())));
    }
}
