package za.co.capitecbank.service;

import java.util.List;
import za.co.capitecbank.persistence.entity.FraudAlertEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;

public interface FraudEvaluationService {

    List<FraudAlertEntity> evaluate(TransactionEntity transaction);
}
