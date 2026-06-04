package za.co.capitecbank.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import za.co.capitecbank.enums.Channel;
import za.co.capitecbank.enums.TransactionType;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record FraudEvaluationRequest(
        @NotBlank @JsonProperty("account_id") String accountId,
        @NotBlank @JsonProperty("client_id") String clientId,
        @NotNull @DecimalMin("0.01") @JsonProperty("amount") BigDecimal amount,

        @NotBlank @Size(min = 3, max = 3) @JsonProperty("currency")
        String currency,

        @NotNull @JsonProperty("transaction_type") TransactionType transactionType,
        @NotNull @JsonProperty("channel") Channel channel,
        @JsonProperty("merchant_category") String merchantCategory,
        @JsonProperty("destination_account_id") String destinationAccountId,
        @NotNull @JsonProperty("timestamp") LocalDateTime timestamp,
        @JsonProperty("location") String location) {}
