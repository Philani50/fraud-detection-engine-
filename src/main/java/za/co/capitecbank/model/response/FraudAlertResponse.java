package za.co.capitecbank.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import za.co.capitecbank.enums.AlertSeverity;
import za.co.capitecbank.enums.AlertStatus;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record FraudAlertResponse(
        @JsonProperty("alert_id") UUID alertId,
        @JsonProperty("transaction_id") UUID transactionId,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("rule_code") String ruleCode,
        @JsonProperty("rule_name") String ruleName,
        @JsonProperty("risk_score") Integer riskScore,
        @JsonProperty("severity") AlertSeverity severity,
        @JsonProperty("status") AlertStatus status,
        @JsonProperty("details") String details,
        @JsonProperty("created_at") LocalDateTime createdAt) {}
