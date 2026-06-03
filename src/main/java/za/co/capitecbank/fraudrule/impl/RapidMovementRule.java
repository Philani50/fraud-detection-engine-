package za.co.capitecbank.fraudrule.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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
@Order(6)
public class RapidMovementRule implements FraudRule {

    private static final String RULE_CODE = "RULE-006";
    private static final String RULE_NAME = "High-Risk Rapid Movement";
    private static final BigDecimal INBOUND_THRESHOLD = new BigDecimal("20000");
    private static final BigDecimal OUTBOUND_RATIO_THRESHOLD = new BigDecimal("0.80");
    private static final int WINDOW_MINUTES = 30;
    private static final String DETAIL_TEMPLATE =
            "Rapid fund movement detected: incoming %s ZAR followed by outgoing %s ZAR (%s%%) within 30 minutes.";

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
        return ruleEngineConfig.rule006Enabled();
    }

    @Override
    public boolean isApplicable(final TransactionEntity transaction) {
        return transaction.getTransactionType() == TransactionType.TRANSFER
                || transaction.getTransactionType() == TransactionType.WITHDRAWAL;
    }

    // RULE-006: High-Risk Rapid Movement
    // Flags a client who receives a large amount (R20,000+) and then immediately moves 80% or more
    // of it back out within 30 minutes — a classic money mule or account-takeover pattern
    @Override
    public Optional<FraudAlertEntity> evaluate(final TransactionEntity transaction, final RuleContext context) {
        log.debug("Evaluating [rule={}]", RULE_CODE);

        // Look up all transactions in the last 30 minutes
        final List<TransactionEntity> recentTransactions = context.getTransactionsBetween(
                timeWindowCalculator.minutesAgo(WINDOW_MINUTES),
                LocalDateTime.now().plusMinutes(5));

        // Find a qualifying inbound transaction (deposit or transfer of R20,000+)
        final Optional<TransactionEntity> inboundOpt = recentTransactions.stream()
                .filter(tx -> tx.getTransactionType() == TransactionType.DEPOSIT
                        || tx.getTransactionType() == TransactionType.TRANSFER)
                .filter(tx -> tx.getAmount().compareTo(INBOUND_THRESHOLD) >= 0)
                .findFirst();

        if (inboundOpt.isEmpty()) {
            return Optional.empty();
        }

        // Alert if this outbound transaction is at least 80% of what came in
        final TransactionEntity inbound = inboundOpt.get();
        final BigDecimal outboundThreshold = inbound.getAmount().multiply(OUTBOUND_RATIO_THRESHOLD);

        if (transaction.getAmount().compareTo(outboundThreshold) < 0) {
            return Optional.empty();
        }

        // Calculate the outbound percentage for the alert detail message
        final BigDecimal percent = transaction
                .getAmount()
                .divide(inbound.getAmount(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);

        log.info("Alert raised [rule={}, clientId={}]", RULE_CODE, transaction.getClientId());
        return Optional.of(fraudAlertFactory.create(
                transaction,
                RULE_CODE,
                RULE_NAME,
                85,
                AlertSeverity.CRITICAL,
                String.format(
                        DETAIL_TEMPLATE,
                        inbound.getAmount().toPlainString(),
                        transaction.getAmount().toPlainString(),
                        percent.toPlainString())));
    }
}
