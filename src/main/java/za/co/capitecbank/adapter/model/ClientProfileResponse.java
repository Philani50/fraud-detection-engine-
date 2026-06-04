package za.co.capitecbank.adapter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import za.co.capitecbank.enums.RiskRating;

/**
 * Immutable response record representing a client profile received from the external
 * Client Profile service.
 *
 * <p>Fields map directly to the JSON response body via {@code @JsonProperty}.
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} ensures the record does not break
 * if the upstream service adds new fields in future.
 *
 * <p>Converted to {@link za.co.capitecbank.persistence.entity.ClientProfileEntity}
 * by {@link za.co.capitecbank.mapper.ClientProfileMapper} before being used by fraud rules.
 *
 * @param clientId unique identifier of the client
 * @param fullName full name of the client (PII — encrypted at rest)
 * @param idNumber national identity number (PII — encrypted at rest)
 * @param accountOpenDate date the client's account was opened
 * @param averageMonthlyIncome average monthly income used by spend rules
 * @param riskRating current risk rating assigned to the client
 * @param lastProfileUpdateDate timestamp of the most recent profile change
 */
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientProfileResponse(
        @JsonProperty("client_id") String clientId,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("id_number") String idNumber,
        @JsonProperty("account_open_date") LocalDate accountOpenDate,
        @JsonProperty("average_monthly_income") BigDecimal averageMonthlyIncome,
        @JsonProperty("risk_rating") RiskRating riskRating,
        @JsonProperty("last_profile_update_date") LocalDateTime lastProfileUpdateDate) {}
