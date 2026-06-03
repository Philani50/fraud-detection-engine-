# AML-FRAUD-005 — ClientProfile Adapter, Resilience4j & Micrometer Metrics

**Status:** DRAFT  
**Project:** AML-FRAUD  
**Depends on:** AML-FRAUD-001, AML-FRAUD-002  
**Standards Reference:** java-spring-boot-banking-standards-v2.md v2.1

---

## 1. Overview

Isolate the `ClientProfile` retrieval from an external CRM or Core Banking service behind
a resilient REST adapter. This ticket covers the declarative Spring 6 HTTP client, Resilience4j
circuit breaker / bulkhead / time limiter wrappers, fallback behaviour, Caffeine caching of
profile data, and all supporting Micrometer metrics. No rule logic changes.

---

## 2. Scope

**In scope:**
- `ClientProfileProperties` (`@ConfigurationProperties` record)
- `ClientProfileProxyClient` (Spring 6 `@GetExchange` declarative client)
- `ClientProfileProxyConfig` (HTTP client factory bean)
- `ClientProfileAdapter` interface
- `ClientProfileAdapterImpl` with Resilience4j decorators + fallback
- Caffeine cache for `ClientProfileEntity` lookups by `clientId`
- Micrometer counters for circuit breaker events and fallback activations
- `application-local.yml` stub configuration for the external service

**Out of scope:**
- Rule implementations (AML-FRAUD-003)
- REST endpoint (AML-FRAUD-004)
- Core persistence layer (AML-FRAUD-001)

---

## 3. Outbound Properties (Standard §13)

Located in `adapter/config/` package.

```java
@ConfigurationProperties(prefix = "app.http-configs.rest.client-profile-service")
public record ClientProfileProperties(
    String url,
    Duration connectTimeout,
    Duration readTimeout
) {}
```

`application-local.yml` stub:
```yaml
app:
  http-configs:
    rest:
      client-profile-service:
        url: http://localhost:9091
        connect-timeout: PT2S
        read-timeout: PT3S
```

---

## 4. Declarative HTTP Client (Standard §13)

Located in `adapter/` package.

```java
public interface ClientProfileProxyClient {
    @GetExchange("/v1/clients/{clientId}/profile")
    ClientProfileResponse getClientProfile(
        @RequestHeader HttpHeaders headers,
        @PathVariable String clientId);
}
```

- Method parameters must **not** be `final` (interface — Standard §4).
- `ClientProfileResponse` is an immutable Java record (Standard §7) in `adapter/model/` package with `@JsonProperty` on every field.

**`ClientProfileResponse` fields:**

| Field | Type | JSON property |
|---|---|---|
| `clientId` | `String` | `"client_id"` |
| `fullName` | `String` | `"full_name"` |
| `idNumber` | `String` | `"id_number"` |
| `accountOpenDate` | `LocalDate` | `"account_open_date"` |
| `averageMonthlyIncome` | `BigDecimal` | `"average_monthly_income"` |
| `riskRating` | `RiskRating` | `"risk_rating"` |
| `lastProfileUpdateDate` | `LocalDateTime` | `"last_profile_update_date"` |

---

## 5. Configuration Factory (Standard §13)

Located in `adapter/config/` package.

```java
@Configuration
@EnableConfigurationProperties(ClientProfileProperties.class)
@Slf4j
@RequiredArgsConstructor
public class ClientProfileProxyConfig {

    @Value("${spring.application.name:fraud-engine-service}")
    private String applicationName;

    private final ClientProfileProperties properties;

    @Bean
    public ClientProfileProxyClient clientProfileProxyClient() { ... }
}
```

- Builds `RestClient` with `connectTimeout`, `readTimeout` from `ClientProfileProperties`.
- Adds `X-Channel-Source: {applicationName}` default header.
- Registers `HttpStatus.UNAUTHORIZED` status handler → throws `ClientProfileServiceException`.
- Wraps via `HttpServiceProxyFactory`.
- `@Value` field `applicationName` is field-injected after construction — correct pattern for `@Configuration` mixing injection styles (Standard §3 note).

---

## 6. Adapter Interface & Implementation (Standard §13, §20)

### 6.1 ClientProfileAdapter (interface)

Located in `adapter/` package.

```java
public interface ClientProfileAdapter {
    Optional<ClientProfileEntity> fetchProfile(String clientId);
}
```

### 6.2 ClientProfileAdapterImpl

Located in `adapter/impl/` package. `@Component`, `@RequiredArgsConstructor`, `@Slf4j`.

**Resilience4j decorators (Standard §20):**
```java
@CircuitBreaker(name = "client-profile-service", fallbackMethod = "fetchProfileFallback")
@TimeLimiter(name = "client-profile-service")
@Bulkhead(name = "client-profile-service")
@Override
public Optional<ClientProfileEntity> fetchProfile(final String clientId) { ... }
```

**Behaviour:**
1. Build `HttpHeaders` with `Authorization: Bearer {token}` (token from injected `TokenClient` or application identity token).
2. Call `clientProfileProxyClient.getClientProfile(headers, clientId)`.
3. Map `ClientProfileResponse` → `ClientProfileEntity` via `ClientProfileMapper`.
4. Return `Optional.of(entity)`.
5. On `HttpStatusCodeException`: log `ERROR`, increment `client.profile.service.errors` counter, throw `ClientProfileServiceException`.
6. On `RestClientException`: log `ERROR` with exception, rethrow.

**Fallback method:**
```java
private Optional<ClientProfileEntity> fetchProfileFallback(
    final String clientId, final Throwable ex) {
    log.warn("ClientProfile service circuit breaker open [correlationId={}, clientId={}]. Reason: {}",
        traceId, clientId, ex.getMessage());
    meterRegistry.counter("client.profile.service.fallback",
        "reason", ex.getClass().getSimpleName()).increment();
    return Optional.empty();
}
```

- Fallback must **never return `null`** (Standard §20).
- Returning `Optional.empty()` triggers the `WARN` log path in RULE-002 and RULE-003 (graceful degradation).

---

## 7. ClientProfileMapper (Standard §15)

Located in `mapper/` package. MapStruct `@Mapper(componentModel = "spring")`.

```
- toEntity(ClientProfileResponse response) → ClientProfileEntity
```

No manual field assignments in adapter implementation.

---

## 8. Caching (Standard §28)

The `ClientProfileAdapterImpl.fetchProfile(...)` result is cached using Spring Cache.

```java
@Cacheable(value = "clientProfiles", key = "#clientId")
public Optional<ClientProfileEntity> fetchProfile(final String clientId) { ... }
```

**Cache rules:**
- Cache name `"clientProfiles"` already declared in `CacheConfig` (AML-FRAUD-002) with 5-minute TTL.
- PII fields are inside the `ClientProfileEntity` — the entity object may be cached locally.
  The entity is only in JVM heap, never serialised to a shared/distributed cache (Caffeine = in-process only). This complies with Standard §28 (no PII in shared/distributed cache).
- Cache is evicted by restart or TTL expiry only — no explicit `@CacheEvict` needed for this use case.

---

## 9. Resilience4j Configuration (Standard §20)

Located in `application-local.yml` (and managed by config server in production).

```yaml
resilience4j:
  circuitbreaker:
    instances:
      client-profile-service:
        slidingWindowSize: 20
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 5
        registerHealthIndicator: true
  timelimiter:
    instances:
      client-profile-service:
        timeoutDuration: 3s
        cancelRunningFuture: true
  bulkhead:
    instances:
      client-profile-service:
        maxConcurrentCalls: 20
        maxWaitDuration: 100ms
```

---

## 10. Micrometer Metrics (Standard §12)

| Metric | Type | Tags | Trigger |
|---|---|---|---|
| `client.profile.service.errors` | Counter | `statusCode` | HTTP error response from CRM |
| `client.profile.service.fallback` | Counter | `reason` | Fallback method activated |
| `client.profile.fetch.duration` | Timer | — | Wrap `fetchProfile` call |
| Circuit breaker state | Auto (Resilience4j→Micrometer) | `name=client-profile-service` | Automatic |

Resilience4j exports circuit breaker state, call counts, and failure rates to Micrometer
automatically when `registerHealthIndicator: true` — no additional instrumentation required
for those.

---

## 11. Exception: ClientProfileServiceException

Located in `exception/` package. Extends `BusinessException` (AML-FRAUD-002).
Thrown by `ClientProfileAdapterImpl` on HTTP errors from the external service.

---

## 12. Dependencies to Add to pom.xml

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-aop` | Resilience4j AOP support |
| `io.github.resilience4j:resilience4j-spring-boot3` | Resilience4j Spring Boot 3 autoconfigure |
| `io.github.resilience4j:resilience4j-micrometer` | Resilience4j → Micrometer metrics export |
| `org.springframework.boot:spring-boot-starter-webflux` | Required for `TimeLimiter` async support with `RestClient` |

---

## 13. Acceptance Criteria

| # | Criterion |
|---|---|
| AC-1 | `fetchProfile` calls the external service and returns a populated `Optional<ClientProfileEntity>` |
| AC-2 | When circuit breaker is open, `fetchProfileFallback` returns `Optional.empty()` |
| AC-3 | Fallback never returns `null` |
| AC-4 | `client.profile.service.fallback` counter increments on fallback activation |
| AC-5 | `client.profile.service.errors` counter increments on `HttpStatusCodeException` |
| AC-6 | Second call for same `clientId` within 5 minutes served from Caffeine cache (no HTTP call) |
| AC-7 | `HTTP 401` from external service throws `ClientProfileServiceException` |
| AC-8 | No HTTP call is made inside a `@Transactional` boundary (Standard §23) |
| AC-9 | `ClientProfileAdapterImpl` has no manual field-to-field assignments — mapping via `ClientProfileMapper` |
| AC-10 | Resilience4j config present in `application-local.yml` for `client-profile-service` instance |
| AC-11 | Unit tests cover: successful fetch, fallback on circuit open, HTTP error, cache hit |
| AC-12 | No PII in any log statement from the adapter |

---

## 14. Package Layout

```
za.co.capitecbank/
├── adapter/
│   ├── ClientProfileAdapter.java              (interface)
│   ├── ClientProfileProxyClient.java          (declarative @GetExchange client)
│   ├── config/
│   │   ├── ClientProfileProperties.java       (@ConfigurationProperties record)
│   │   └── ClientProfileProxyConfig.java      (factory @Configuration)
│   ├── impl/
│   │   └── ClientProfileAdapterImpl.java      (@Component + Resilience4j)
│   └── model/
│       └── ClientProfileResponse.java         (immutable record)
├── exception/
│   └── ClientProfileServiceException.java
└── mapper/
    └── ClientProfileMapper.java               (MapStruct)
```
