package za.co.capitecbank.fraudrule;

import java.util.Optional;
import za.co.capitecbank.fraudrule.context.RuleContext;
import za.co.capitecbank.persistence.entity.FraudAlertEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;

public interface FraudRule {

    String getRuleCode();

    String getRuleName();

    boolean isEnabled();

    boolean isApplicable(TransactionEntity transaction);

    Optional<FraudAlertEntity> evaluate(TransactionEntity transaction, RuleContext context);
}
