package za.co.capitecbank.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.capitecbank.mapper.FraudMapper;
import za.co.capitecbank.model.request.FraudEvaluationRequest;
import za.co.capitecbank.model.response.FraudEvaluationResponse;
import za.co.capitecbank.persistence.FraudPersistenceService;
import za.co.capitecbank.persistence.entity.FraudAlertEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;
import za.co.capitecbank.service.FraudEvaluationService;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/fraud")
public class FraudController {

    private final FraudEvaluationService fraudEvaluationService;
    private final FraudPersistenceService fraudPersistenceService;
    private final FraudMapper fraudMapper;

    @PreAuthorize("hasAuthority('SCOPE_fraud:evaluate')")
    @PostMapping(
            path = "/evaluations",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FraudEvaluationResponse> evaluateFraud(
            @Valid @RequestBody final FraudEvaluationRequest request) {

        final String correlationId = UUID.randomUUID().toString();

        log.info(
                "POST /v1/fraud/evaluations received [correlationId={}, clientId={}]",
                correlationId,
                request.clientId());

        final TransactionEntity transaction = fraudMapper.toEntity(request);
        final TransactionEntity saved = fraudPersistenceService.saveTransaction(transaction, correlationId);
        final List<FraudAlertEntity> alerts = fraudEvaluationService.evaluate(saved);
        final FraudEvaluationResponse response = fraudMapper.toEvaluationResponse(saved, alerts);

        log.info(
                "POST /v1/fraud/evaluations completed [correlationId={}, clientId={}, alerts={}]",
                correlationId,
                request.clientId(),
                alerts.size());
        return ResponseEntity.ok(response);
    }
}
