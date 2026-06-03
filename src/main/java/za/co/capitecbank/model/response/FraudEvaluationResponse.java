package za.co.capitecbank.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record FraudEvaluationResponse(
        @JsonProperty("transaction_id") UUID transactionId,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("alert_count") int alertCount,
        @JsonProperty("alerts") List<FraudAlertResponse> alerts,
        @JsonProperty("evaluated_at") LocalDateTime evaluatedAt) {}
