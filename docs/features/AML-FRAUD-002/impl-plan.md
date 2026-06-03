# AML-FRAUD-002 — Implementation Plan

**Status:** DRAFT  
**Standards Reference:** java-spring-boot-banking-standards-v2.md v2.1  
**Depends on approved:** requirements.md, tech-analysis.md

---

## Ordered Task List

Tasks are sequenced by dependency — each task is completable without a later task.

---

### TASK-01 — Add Maven Dependencies

**Files to touch:**
- `pom.xml`

**Description:**
Add all dependencies identified in tech-analysis A2, A3, B4:
- `spring-boot-starter-cache`
- `com.github.ben-manes.caffeine:caffeine`
- `io.micrometer:micrometer-tracing-bridge-otel`
- `io.opentelemetry:opentelemetry-exporter-otlp`
- `io.micrometer:micrometer-registry-prometheus`
- `spring-boot-starter-aop`

No version pins needed — all managed by Spring Boot 3.5.14 BOM.

**Test class + method:** N/A — validated by `mvn compile`.

---

### TASK-02 — Add @ConfigurationPropertiesScan to Application.java

**Files to touch:**
- `src/main/java/za/co/capitecbank/Application.java`
- `src/main/java/za/co/capitecbank/security/impl/AesGcmFieldEncryptionService.java`

**Description:**
Add `@ConfigurationPropertiesScan` to `Application.java` so all `@ConfigurationProperties`
records in the `za.co.capitecbank` package are auto-discovered (tech-analysis B6).

Remove `@EnableConfigurationProperties(EncryptionProperties.class)` from
`AesGcmFieldEncryptionService` — a `@Service` class should not be managing config registration.

**Test class + method:** Verified by existing unit tests still passing after the change.

---

### TASK-03 — BusinessException (abstract base)

**Files to touch:**
- `src/main/java/za/co/capitecbank/exception/BusinessException.java`

**Description:**
Create `BusinessException` as an abstract class extending `RuntimeException`:
- Two constructors: `(String message)` and `(String message, Throwable cause)`.
- No Lombok annotations on abstract class (Standard §18 — Lombok `@Data`/`@Builder` not
  appropriate for exception hierarchy).

**Test class + method:** No dedicated test — covered structurally by TASK-04 hierarchy test.

---

### TASK-04 — Update FraudEvaluationException to Extend BusinessException

**Files to touch:**
- `src/main/java/za/co/capitecbank/exception/FraudEvaluationException.java`

**Description:**
Change `extends RuntimeException` to `extends BusinessException`. Keep the two existing
constructors unchanged. This closes the stub created in AML-FRAUD-001 (tech-analysis B1).

**Test class + method:**
- `src/test/java/za/co/capitecbank/exception/ExceptionHierarchyTest.java`
- `fraudEvaluationException_shouldBeInstanceOfBusinessException()`
- `businessException_shouldBeInstanceOfRuntimeException()`

---

### TASK-05 — RuleEngineConfig

**Files to touch:**
- `src/main/java/za/co/capitecbank/config/RuleEngineConfig.java`
- `src/main/resources/application-local.yml`

**Description:**
Create `RuleEngineConfig` as a `@ConfigurationProperties(prefix = "app.fraud-rules")` record
with six boolean fields: `rule001Enabled` through `rule006Enabled`.

`application-local.yml` addition:
```yaml
app:
  fraud-rules:
    rule001-enabled: true
    rule002-enabled: true
    rule003-enabled: true
    rule004-enabled: true
    rule005-enabled: true
    rule006-enabled: true
```

Note: Spring Boot relaxed binding maps `rule001-enabled` (kebab-case in YAML) to
`rule001Enabled` (camelCase in record) automatically.

**Test class + method:** No dedicated test — binding verified by integration test in TASK-11.

---

### TASK-06 — CacheConfig

**Files to touch:**
- `src/main/java/za/co/capitecbank/config/CacheConfig.java`
- `src/main/resources/application-local.yml`

**Description:**
Create `CacheConfig`:
- `@Configuration`, `@EnableCaching`, `@Slf4j`
- Declare `@Bean CaffeineCacheManager` with two named caches:
  - `"clientProfiles"`: `maximumSize=500`, `expireAfterWrite=5min`,
    `refreshAfterWrite=4min`, `recordStats()`
  - `"recentTransactionCounts"`: `maximumSize=2000`, `expireAfterWrite=2min`,
    `recordStats()`
- Build each cache using `Caffeine.newBuilder()` — not YAML spec string (per tech-analysis B5).
- Register `CaffeineStatsCounter` for Micrometer export — use
  `CaffeineCacheMetrics.monitor(meterRegistry, cache, cacheName)` via
  `@PostConstruct` or by overriding `CaffeineCacheManager`.

`application-local.yml` addition:
```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
```

**Test class + method:**
- `src/test/java/za/co/capitecbank/config/CacheConfigTest.java`
- `cacheManager_shouldContainClientProfilesCache()`
- `cacheManager_shouldContainRecentTransactionCountsCache()`

---

### TASK-07 — FraudRule Interface

**Files to touch:**
- `src/main/java/za/co/capitecbank/fraudrule/FraudRule.java`

**Description:**
Create the `FraudRule` interface per requirements section 3:
- `String getRuleCode()`
- `String getRuleName()`
- `boolean isEnabled()`
- `boolean isApplicable(TransactionEntity transaction)` — no `final` on parameter (Standard §4)
- `Optional<FraudAlertEntity> evaluate(TransactionEntity transaction, RuleContext context)`
- No `public` modifier on methods (Standard §1 — redundant on interface methods).

**Test class + method:** No unit test — pure interface, covered by implementations in
AML-FRAUD-003.

---

### TASK-08 — RuleContext

**Files to touch:**
- `src/main/java/za/co/capitecbank/fraudrule/context/RuleContext.java`

**Description:**
Create `RuleContext` as a plain (non-Spring) class:
- Constructor: `(TransactionEntity transaction, FraudPersistenceService fraudPersistenceService, Supplier<Optional<ClientProfileEntity>> clientProfileSupplier, String traceId)`
- `getClientProfile()` → delegates to `clientProfileSupplier.get()` (memoised — at most one DB call)
- `getCashTransactionsSince(LocalDateTime since, BigDecimal belowAmount)` → delegates to
  `fraudPersistenceService.findCashTransactionsByClientSince(clientId, List.of(DEPOSIT, WITHDRAWAL), since, belowAmount, traceId)`
- `getTransactionCountSince(LocalDateTime since)` → delegates to
  `fraudPersistenceService.countTransactionsByClientSince(clientId, since, traceId)`
- `getTransactionsBetween(LocalDateTime from, LocalDateTime to)` → delegates to
  `fraudPersistenceService.findTransactionsByClientBetween(clientId, from, to, traceId)`
- `@Getter` on `transaction` field only — all other fields package-private.
- Class is `final` (no extension needed).

**Test class + method:**
- `src/test/java/za/co/capitecbank/fraudrule/context/RuleContextTest.java`
- `getClientProfile_shouldCallSupplierOnce_whenCalledMultipleTimes()` (memoisation test — tech-analysis C2)
- `getCashTransactionsSince_shouldDelegateToPersistenceService()`
- `getTransactionCountSince_shouldDelegateToPersistenceService()`
- `getTransactionsBetween_shouldDelegateToPersistenceService()`

---

### TASK-09 — RuleContextFactory

**Files to touch:**
- `src/main/java/za/co/capitecbank/fraudrule/context/RuleContextFactory.java`

**Description:**
Create `RuleContextFactory`:
- `@Component`, `@RequiredArgsConstructor`
- Constructor-injects `FraudPersistenceService`
- `create(TransactionEntity transaction, String traceId)` → `RuleContext`
- Builds the memoised `Supplier<Optional<ClientProfileEntity>>` using
  `Suppliers.memoize(() -> fraudPersistenceService.findClientProfile(clientId, traceId))`

**Test class + method:**
- `src/test/java/za/co/capitecbank/fraudrule/context/RuleContextFactoryTest.java`
- `create_shouldReturnRuleContextWithCorrectClientId()`

---

### TASK-10 — FraudEvaluationService Interface + FraudEvaluationServiceImpl

**Files to touch:**
- `src/main/java/za/co/capitecbank/service/FraudEvaluationService.java`
- `src/main/java/za/co/capitecbank/service/impl/FraudEvaluationServiceImpl.java`

**Description:**
Create `FraudEvaluationService` interface:
```java
public interface FraudEvaluationService {
    List<FraudAlertEntity> evaluate(TransactionEntity transaction);
}
```

Create `FraudEvaluationServiceImpl`:
- `@Service`, `@RequiredArgsConstructor`, `@Slf4j`
- NOT `final` (Standard §18 — CGLIB for `@Transactional` / AOP)
- Constructor-injects: `List<FraudRule> fraudRules`, `RuleContextFactory ruleContextFactory`,
  `FraudPersistenceService fraudPersistenceService`, `MeterRegistry meterRegistry`
- `@NewSpan("fraud-rule-evaluation")` on `evaluate()` method
- Full evaluation loop per requirements section 6.2:
  1. Build `RuleContext` via `ruleContextFactory.create(transaction, traceId)`
  2. Extract `traceId` from `Span.current().context().traceId()` (Micrometer Tracing)
  3. Wrap loop in `Timer.builder("fraud.evaluation.duration").tag("clientId", clientId).register(meterRegistry).record(...)`
  4. For each rule: check `isEnabled()` then `isApplicable()`, call `evaluate()`, persist alert,
     increment `fraud.alerts.generated` counter tagged `ruleCode`
  5. Catch unexpected `Exception` from rule, wrap in `FraudEvaluationException`, log `ERROR`,
     rethrow
- Structured logging: no PII, parameterised templates only (Standard §9)

**Test class + method:**
- `src/test/java/za/co/capitecbank/service/impl/FraudEvaluationServiceImplTest.java`
- `@ExtendWith(MockitoExtension.class)` with `SimpleMeterRegistry` for counter assertions
- `evaluate_allRulesEnabled_shouldCollectAndPersistAlerts()`
- `evaluate_disabledRule_shouldBeSkipped()`
- `evaluate_notApplicableRule_shouldBeSkipped()`
- `evaluate_ruleThrowsException_shouldWrapInFraudEvaluationException()`
- `evaluate_shouldIncrementAlertCounter_perRuleCode()`

---

### TASK-11 — Integration Test Update (Spring Context Smoke)

**Files to touch:**
- `src/test/java/za/co/capitecbank/persistence/FraudPersistenceIntegrationTest.java`

**Description:**
The existing integration test uses `@SpringBootTest` with `@ActiveProfiles("local")`. After
adding new beans (`CacheConfig`, `RuleEngineConfig`, `FraudEvaluationServiceImpl`, etc.), verify
the Spring context still loads cleanly with all new beans present.

Add one additional assertion to the existing `contextLoads_andFlywayMigrationsRun()` test:
```java
@Autowired
private FraudEvaluationService fraudEvaluationService;

// in contextLoads test:
assertThat(fraudEvaluationService).isNotNull();
```

Note: `FraudEvaluationServiceImpl` requires `List<FraudRule>` to be non-empty or Spring will
fail to inject it. Since no rule implementations exist yet (AML-FRAUD-003), inject an empty list
by adding a `@Bean List<FraudRule> emptyRules()` stub **only in the test** via a
`@TestConfiguration` inner class — **not** in production code.

**Test class + method:**
- `src/test/java/za/co/capitecbank/persistence/FraudPersistenceIntegrationTest.java`
- Existing tests still pass + `fraudEvaluationService` bean is present in context.

---

## Task Dependency Order

```
TASK-01 (pom.xml deps)
  └─► TASK-02 (@ConfigurationPropertiesScan)
        └─► TASK-03 (BusinessException)
              └─► TASK-04 (FraudEvaluationException update)
                    └─► TASK-05 (RuleEngineConfig)
                          └─► TASK-06 (CacheConfig)
                                └─► TASK-07 (FraudRule interface)
                                      └─► TASK-08 (RuleContext)
                                            └─► TASK-09 (RuleContextFactory)
                                                  └─► TASK-10 (FraudEvaluationService + Impl)
                                                        └─► TASK-11 (Integration test update)
```

## Estimated Task Count: 11 tasks
## New Source Files: ~8
## Modified Files: ~4 (pom.xml, Application.java, AesGcmFieldEncryptionService, FraudEvaluationException)
## New Test Files: ~5 (unit) + 1 (integration test update)
