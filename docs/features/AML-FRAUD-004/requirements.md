# AML-FRAUD-004 — REST API, Security, OpenAPI & Global Exception Handler

**Status:** DRAFT  
**Project:** AML-FRAUD  
**Depends on:** AML-FRAUD-001, AML-FRAUD-002  
**Standards Reference:** java-spring-boot-banking-standards-v2.md v2.1

---

## 1. Overview

Expose the fraud evaluation engine via a secure, versioned REST endpoint. This ticket covers
the inbound request/response records (immutable Java records), the `FraudController`, the
global exception handler (`@RestControllerAdvice`), Spring Security OAuth2 configuration,
and the OpenAPI config class. No rule logic is touched here.

---

## 2. Scope

**In scope:**
- `FraudEvaluationRequest` record (inbound)
- `FraudAlertResponse` record (outbound per alert)
- `FraudEvaluationResponse` record (outbound wrapper)
- `FraudController` — `POST /v1/fraud/evaluations`
- `FraudMapper` (MapStruct) — `TransactionEntity` ↔ request, `FraudAlertEntity` → response
- `GlobalExceptionHandler` (`@RestControllerAdvice`)
- `ErrorResponse` record
- `SecurityConfig` — OAuth2 resource server, JWT validation
- `OpenApiConfig`

**Out of scope:**
- Rule implementations (AML-FRAUD-003)
- External ClientProfile adapter (AML-FRAUD-005)
- Persistence layer (AML-FRAUD-001)

---

## 3. Request / Response Records (Standard §7)

All boundary types are **immutable Java records** with `@JsonProperty` on every field and
`@JsonIgnoreProperties(ignoreUnknown = true)` at class level.

### 3.1 FraudEvaluationRequest

Located in `model/request/`.

| Field | Type | Validation | JSON property |
|---|---|---|---|
| `accountId` | `String` | `@NotBlank` | `"account_id"` |
| `clientId` | `String` | `@NotBlank` | `"client_id"` |
| `amount` | `BigDecimal` | `@NotNull`, `@DecimalMin("0.01")` | `"amount"` |
| `currency` | `String` | `@NotBlank`, `@Size(min=3, max=3)` | `"currency"` |
| `transactionType` | `TransactionType` | `@NotNull` | `"transaction_type"` |
| `channel` | `Channel` | `@NotNull` | `"channel"` |
| `merchantCategory` | `String` | nullable | `"merchant_category"` |
| `destinationAccountId` | `String` | nullable | `"destination_account_id"` |
| `timestamp` | `LocalDateTime` | `@NotNull` | `"timestamp"` |
| `location` | `String` | nullable | `"location"` |

### 3.2 FraudAlertResponse

Located in `model/response/`.

| Field | Type | JSON property |
|---|---|---|
| `alertId` | `UUID` | `"alert_id"` |
| `transactionId` | `UUID` | `"transaction_id"` |
| `clientId` | `String` | `"client_id"` |
| `ruleCode` | `String` | `"rule_code"` |
| `ruleName` | `String` | `"rule_name"` |
| `riskScore` | `Integer` | `"risk_score"` |
| `severity` | `AlertSeverity` | `"severity"` |
| `status` | `AlertStatus` | `"status"` |
| `details` | `String` | `"details"` |
| `createdAt` | `LocalDateTime` | `"created_at"` |

### 3.3 FraudEvaluationResponse

Located in `model/response/`.

| Field | Type | JSON property |
|---|---|---|
| `transactionId` | `UUID` | `"transaction_id"` |
| `clientId` | `String` | `"client_id"` |
| `alertCount` | `int` | `"alert_count"` |
| `alerts` | `List<FraudAlertResponse>` | `"alerts"` |
| `evaluatedAt` | `LocalDateTime` | `"evaluated_at"` |

### 3.4 ErrorResponse

Located in `model/response/`.

| Field | Type | JSON property |
|---|---|---|
| `status` | `int` | `"status"` |
| `error` | `String` | `"error"` |
| `message` | `String` | `"message"` |
| `timestamp` | `LocalDateTime` | `"timestamp"` |
| `correlationId` | `String` | `"correlation_id"` |

---

## 4. FraudMapper (Standard §15)

Located in `mapper/` package. MapStruct `@Mapper(componentModel = "spring")`.

No manual field assignments in controller or service — all mapping via this interface.

```
- toEntity(FraudEvaluationRequest request) → TransactionEntity
- toAlertResponse(FraudAlertEntity entity) → FraudAlertResponse
- toAlertResponses(List<FraudAlertEntity> entities) → List<FraudAlertResponse>
```

MapStruct `@Mapping` annotations map snake_case JSON field names to camelCase entity fields as needed.

---

## 5. FraudController (Standard §3, §8, §24)

Located in `controller/` package.

**Annotation order (Standard §3):**
```java
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/fraud")
```

**Endpoint:**

```
POST /v1/fraud/evaluations
Consumes: application/json
Produces: application/json
```

**Behaviour:**
1. Receive `@Valid @RequestBody FraudEvaluationRequest request`.
2. Log entry: `log.info("Fraud evaluation request received [correlationId={}, clientId={}]", traceId, request.clientId())`.
3. Map request → `TransactionEntity` via `FraudMapper`.
4. Persist transaction via `FraudPersistenceService.saveTransaction(entity)`.
5. Delegate to `FraudEvaluationService.evaluate(entity)`.
6. Map results → `FraudEvaluationResponse` via `FraudMapper`.
7. Return `ResponseEntity<FraudEvaluationResponse>` with `HTTP 200`.
8. No `try/catch` — exceptions propagate to `GlobalExceptionHandler` (Standard §10).

**Security:**
```java
@PreAuthorize("hasAuthority('SCOPE_fraud:evaluate')")
```
(Standard §19)

**OpenAPI annotations:**
```java
@Operation(operationId = "evaluateFraudRules", summary = "Evaluate transaction for fraud")
@ApiResponse(responseCode = "200", description = "Evaluation completed")
@ApiResponse(responseCode = "400", description = "Invalid request payload")
@ApiResponse(responseCode = "401", description = "Unauthorised — missing or invalid JWT")
@ApiResponse(responseCode = "500", description = "Internal server error")
```

---

## 6. GlobalExceptionHandler (Standard §10)

Located in `exception/` package. Annotated `@RestControllerAdvice`, `@Slf4j`.

| Exception | HTTP Status | Log Level | Notes |
|---|---|---|---|
| `MethodArgumentNotValidException` | `400` | `WARN` | Collect all field errors into `message` |
| `FraudEvaluationException` | `500` | `ERROR` | Log with exception object (never `getMessage()` alone) |
| `BusinessException` | `422` | `WARN` | — |
| `AccessDeniedException` | `403` | `WARN` | Spring Security scope mismatch |
| `AuthenticationException` | `401` | `WARN` | JWT invalid/expired |
| `Exception` (catch-all) | `500` | `ERROR` | Log with exception: `log.error("Unhandled exception [correlationId={}]", traceId, ex)` |

**Anti-patterns forbidden (Standard §10):**
- Do not log and rethrow in the same catch block.
- Do not use `ex.getMessage()` alone as the log message on system boundary — always pass `ex` as final parameter.

---

## 7. SecurityConfig (Standard §19)

Located in `config/` package.

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig { ... }
```

**Filter chain:**
- `csrf` disabled (stateless REST).
- Session: `STATELESS`.
- Permitted without auth: `/actuator/health`, `/actuator/info`, `/v3/api-docs/**`, `/swagger-ui/**`.
- All other requests: `authenticated()`.
- OAuth2 resource server: JWT with `JwtAuthenticationConverter`.

**JWT validation (Standard §19):**
- Signature validated against JWKS endpoint.
- `exp` claim validated automatically by Spring Security.
- `iss` claim: configured via `spring.security.oauth2.resourceserver.jwt.issuer-uri`.
- `aud` claim: configured via `spring.security.oauth2.resourceserver.jwt.audiences`.

**`application-local.yml` JWT config** (test stub — no real JWKS):
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9090/realms/test
          audiences: fraud-engine-service
```

**Security mandates (Standard §19):**
- Never log tokens or auth headers.
- CORS: explicit `allowedOrigins` per profile — no wildcard `*` in production profile.
- Rate limiting: offloaded to API Gateway (document this in OpenAPI spec description).

---

## 8. OpenApiConfig (Standard §8)

Located in `config/` package. No execution logic or custom beans — definition only.

```java
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Fraud Rule Engine Service",
        version = "1",
        description = "Evaluates financial transactions against configurable fraud detection rules. " +
            "Rate limiting is enforced at the API Gateway layer. " +
            "All endpoints require a valid JWT with appropriate scopes.",
        contact = @Contact(name = "AML Engineering", email = "aml-eng@capitecbank.co.za")))
public class OpenApiConfig {}
```

---

## 9. Application Configuration Additions

### `application-local.yml` additions
```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
```

---

## 10. Dependencies to Add to pom.xml

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-security` | Spring Security |
| `spring-boot-starter-oauth2-resource-server` | JWT resource server |
| `springdoc-openapi-starter-webmvc-ui` | OpenAPI / Swagger UI |
| `org.mapstruct:mapstruct` | MapStruct code generation |
| `org.mapstruct:mapstruct-processor` (annotation processor) | MapStruct APT |

---

## 11. Acceptance Criteria

| # | Criterion |
|---|---|
| AC-1 | `POST /v1/fraud/evaluations` with valid JWT returns `200` and a `FraudEvaluationResponse` |
| AC-2 | Request missing `clientId` returns `400` with `ErrorResponse` |
| AC-3 | Request without JWT returns `401` |
| AC-4 | JWT with wrong audience returns `401` |
| AC-5 | JWT with expired `exp` returns `401` |
| AC-6 | `FraudEvaluationException` from service returns `500` with `ErrorResponse` |
| AC-7 | `FraudController` contains no `try/catch` blocks |
| AC-8 | All response fields have explicit `@JsonProperty` |
| AC-9 | `/actuator/health` accessible without JWT |
| AC-10 | Swagger UI accessible at `/swagger-ui.html` in local profile |
| AC-11 | `FraudMapper` tested: request → entity, entity list → response list |
| AC-12 | `GlobalExceptionHandler` tested for each mapped exception type |
| AC-13 | No manual field assignments in controller or service (all via `FraudMapper`) |

---

## 12. Package Layout

```
za.co.capitecbank/
├── config/
│   ├── OpenApiConfig.java
│   └── SecurityConfig.java
├── controller/
│   └── FraudController.java
├── exception/
│   └── GlobalExceptionHandler.java
├── mapper/
│   └── FraudMapper.java
└── model/
    ├── request/
    │   └── FraudEvaluationRequest.java
    └── response/
        ├── FraudAlertResponse.java
        ├── FraudEvaluationResponse.java
        └── ErrorResponse.java
```
