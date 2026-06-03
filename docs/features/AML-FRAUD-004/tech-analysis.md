# AML-FRAUD-004 — Technical Analysis

**Status:** APPROVED  
**Depends on:** AML-FRAUD-001 (COMPLETE), AML-FRAUD-002 (COMPLETE)

## Key Decisions

| ID | Decision |
|---|---|
| A1 | `springdoc-openapi-starter-webmvc-ui` already in pom from previous fix |
| A2 | MapStruct 1.6.3 JARs fully cached — add dependency + annotation processor path |
| A3 | `spring-boot-starter-security` and `spring-boot-starter-oauth2-resource-server` not cached at 3.5.14 — will resolve via IntelliJ. For local dev, disable security with `spring.autoconfigure.exclude` to allow unauthenticated testing |
| A4 | `FraudMapper` maps `FraudEvaluationRequest` → `TransactionEntity` and `FraudAlertEntity` → `FraudAlertResponse` |
| A5 | `GlobalExceptionHandler` handles `MethodArgumentNotValidException`, `FraudEvaluationException`, `BusinessException`, catch-all `Exception` |
| A6 | `ErrorResponse` is a Java record |
| A7 | Security disabled locally via `spring.autoconfigure.exclude` — added to `application.yml`. Production config server provides real security config |

All questions resolved.
