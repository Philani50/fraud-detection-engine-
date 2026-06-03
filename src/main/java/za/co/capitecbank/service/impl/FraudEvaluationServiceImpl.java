package za.co.capitecbank.service.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.capitecbank.exception.FraudEvaluationException;
import za.co.capitecbank.fraudrule.FraudRule;
import za.co.capitecbank.fraudrule.context.RuleContext;
import za.co.capitecbank.fraudrule.context.RuleContextFactory;
import za.co.capitecbank.persistence.FraudPersistenceService;
import za.co.capitecbank.persistence.entity.FraudAlertEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;
import za.co.capitecbank.service.FraudEvaluationService;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudEvaluationServiceImpl implements FraudEvaluationService {

    private static final String METRIC_ALERTS_GENERATED = "fraud.alerts.generated";
    private static final String METRIC_EVALUATION_DURATION = "fraud.evaluation.duration";
    private static final String TAG_RULE_CODE = "ruleCode";
    private static final String TAG_CLIENT_ID = "clientId";
    private static final String RULE_EVALUATION_FAILED = "Rule evaluation failed for ";

    private final List<FraudRule> fraudRules;
    private final RuleContextFactory ruleContextFactory;
    private final FraudPersistenceService fraudPersistenceService;
    private final MeterRegistry meterRegistry;

    @Override
    public List<FraudAlertEntity> evaluate(final TransactionEntity transaction) {
        final String clientId = transaction.getClientId();
        final String traceId = UUID.randomUUID().toString();

        return Timer.builder(METRIC_EVALUATION_DURATION)
                .tag(TAG_CLIENT_ID, clientId)
                .register(meterRegistry)
                .record(() -> runEvaluation(transaction, traceId));
    }

    private List<FraudAlertEntity> runEvaluation(final TransactionEntity transaction, final String traceId) {
        final RuleContext context = ruleContextFactory.create(transaction, traceId);
        final List<FraudAlertEntity> generatedAlerts = new ArrayList<>();

        for (final FraudRule rule : fraudRules) {
            if (!rule.isEnabled()) {
                log.debug("Rule skipped — disabled [correlationId={}, ruleCode={}]", traceId, rule.getRuleCode());
                continue;
            }
            if (!rule.isApplicable(transaction)) {
                log.debug("Rule skipped — not applicable [correlationId={}, ruleCode={}]", traceId, rule.getRuleCode());
                continue;
            }
            try {
                rule.evaluate(transaction, context).ifPresent(alert -> {
                    final FraudAlertEntity saved = fraudPersistenceService.saveAlert(alert, traceId);
                    meterRegistry
                            .counter(METRIC_ALERTS_GENERATED, TAG_RULE_CODE, rule.getRuleCode())
                            .increment();
                    log.info(
                            "Fraud alert generated [correlationId={}, ruleCode={}, alertId={}]",
                            traceId,
                            rule.getRuleCode(),
                            saved.getAlertId());
                    generatedAlerts.add(saved);
                });
            } catch (Exception ex) {
                log.error("Rule evaluation failed [correlationId={}, ruleCode={}]", traceId, rule.getRuleCode(), ex);
                throw new FraudEvaluationException(RULE_EVALUATION_FAILED + rule.getRuleCode(), ex);
            }
        }
        return generatedAlerts;
    }
}
