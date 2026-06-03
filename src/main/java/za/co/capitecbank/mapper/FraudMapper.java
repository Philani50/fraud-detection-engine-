package za.co.capitecbank.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import za.co.capitecbank.model.request.FraudEvaluationRequest;
import za.co.capitecbank.model.response.FraudAlertResponse;
import za.co.capitecbank.model.response.FraudEvaluationResponse;
import za.co.capitecbank.persistence.entity.FraudAlertEntity;
import za.co.capitecbank.persistence.entity.TransactionEntity;

@Mapper(componentModel = "spring")
public interface FraudMapper {

    @Mapping(target = "transactionId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    TransactionEntity toEntity(FraudEvaluationRequest request);

    FraudAlertResponse toAlertResponse(FraudAlertEntity entity);

    List<FraudAlertResponse> toAlertResponses(List<FraudAlertEntity> entities);

    default FraudEvaluationResponse toEvaluationResponse(
            final TransactionEntity transaction, final List<FraudAlertEntity> alerts) {
        final List<FraudAlertResponse> alertResponses = toAlertResponses(alerts);
        return FraudEvaluationResponse.builder()
                .transactionId(transaction.getTransactionId())
                .clientId(transaction.getClientId())
                .alertCount(alertResponses.size())
                .alerts(alertResponses)
                .evaluatedAt(LocalDateTime.now())
                .build();
    }
}
