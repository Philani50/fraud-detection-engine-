package za.co.capitecbank.fraudrule.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import za.co.capitecbank.persistence.entity.ClientProfileEntity;
import za.co.capitecbank.persistence.entity.FraudAlertEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class ProfileChangeRule implements FraudRule {

    private static final String RULE_CODE = "RULE-002";
    private static final String RULE_NAME = "Large Transaction After Profile Change";
    private static final BigDecimal LARGE_AMOUNT_THRESHOLD = new BigDecimal("50000");
    private static final int PROFILE_CHANGE_WINDOW_HOURS = 48;
    private static final String DETAIL_TEMPLATE =
            "Large transaction of %s ZAR occurred within 48 hours of a PII profile update on %s.";

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
        return ruleEngineConfig.rule002Enabled();
    }

    @Override
    public boolean isApplicable(final TransactionEntity transaction) {
        return transaction.getAmount().compareTo(LARGE_AMOUNT_THRESHOLD) >= 0;
    }

    // RULE-002: Large Transaction After Profile Change
    // Flags a large transaction (R50,000+) that happens within 48 hours of the client's personal
    // information being updated — a common pattern when fraudsters take over an account and
    // immediately change details before draining funds
    @Override
    public Optional<FraudAlertEntity> evaluate(final TransactionEntity transaction, final RuleContext context) {
        log.debug("Evaluating [rule={}]", RULE_CODE);

        // Cannot evaluate without a client profile — skip gracefully
        final Optional<ClientProfileEntity> profileOpt = context.getClientProfile();
        if (profileOpt.isEmpty()) {
            log.warn("Client profile absent — cannot evaluate [rule={}]", RULE_CODE);
            return Optional.empty();
        }

        final ClientProfileEntity profile = profileOpt.get();
        if (profile.getLastProfileUpdateDate() == null) {
            return Optional.empty();
        }

        // Check if the profile was updated within the last 48 hours
        final LocalDateTime windowStart = timeWindowCalculator.hoursAgo(PROFILE_CHANGE_WINDOW_HOURS);
        if (profile.getLastProfileUpdateDate().isBefore(windowStart)) {
            return Optional.empty();
        }

        log.info("Alert raised [rule={}, clientId={}]", RULE_CODE, transaction.getClientId());
        return Optional.of(fraudAlertFactory.create(
                transaction,
                RULE_CODE,
                RULE_NAME,
                90,
                AlertSeverity.CRITICAL,
                String.format(
                        DETAIL_TEMPLATE, transaction.getAmount().toPlainString(), profile.getLastProfileUpdateDate())));
    }
}
