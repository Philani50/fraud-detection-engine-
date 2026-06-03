package za.co.capitecbank.fraudrule.util;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import za.co.capitecbank.enums.AlertSeverity;
import za.co.capitecbank.enums.AlertStatus;
import za.co.capitecbank.persistence.entity.FraudAlertEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;

@Component
public class FraudAlertFactory {

    public FraudAlertEntity create(
            final TransactionEntity transaction,
            final String ruleCode,
            final String ruleName,
            final int riskScore,
            final AlertSeverity severity,
            final String details) {
        return FraudAlertEntity.builder()
                .transactionId(transaction.getTransactionId())
                .clientId(transaction.getClientId())
                .ruleCode(ruleCode)
                .ruleName(ruleName)
                .riskScore(riskScore)
                .severity(severity)
                .status(AlertStatus.PENDING)
                .details(details)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
