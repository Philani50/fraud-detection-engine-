# AML-FRAUD-002 — Technical Analysis

**Status:** DRAFT  
**Standards Reference:** java-spring-boot-banking-standards-v2.md v2.1  
**Codebase baseline:** Spring Boot 3.5.14, Java 25, Maven, root package `za.co.capitecbank`  
**Depends on:** AML-FRAUD-001 (COMPLETE)

---

## Domain A — Dependencies

### A1: Caffeine JAR Availability in Local Cache

**Question:** Is the Caffeine JAR locally cached to avoid an SSL/PKIX failure similar to what was
seen with surefire?

**Proposed Answer:**

Checked `.m2/repository/com/github/ben-manes/caffeine/caffeine/3.2.3/` — only the POM is cached,
not the JAR. However `spring-boot-starter-cache` itself is also not yet in the local cache.
Both will need to resolve at build time.

**Decision:** The Capitec internal Artifactory (JFrog) proxies Maven Central and the SSL cert
issue is a JDK truststore problem specific to running Maven outside IntelliJ. Running the build
via IntelliJ's Maven runner (which uses its own bundled truststore) resolves this. No version
pinning or workaround required — these artifacts will resolve normally through IntelliJ.

**Resolved:** Yes

---

### A2: Micrometer Tracing — OTel Bridge Availability

**Question:** `micrometer-tracing-bridge-otel` and `opentelemetry-exporter-otlp` are not in the
local `.m2` cache at all. Can we use them, and do they introduce any compatibility constraints?

**Proposed Answer:**

Neither `micrometer-tracing-bridge-otel` nor `opentelemetry-exporter-otlp` are cached locally.
Both are managed by the Spring Boot 3.5.14 BOM (via `spring-boot-dependencies`), so no version
pins are needed in `pom.xml`. They will resolve through IntelliJ's Maven runner.

However, `@NewSpan` requires the `spring-boot-starter-actuator` + `micrometer-tracing` on the
classpath and a configured tracer. For local/test without a running OTLP collector, Spring Boot
auto-configuration will use a no-op tracer if no exporter is configured — the code compiles and
runs fine. No test failures caused by missing exporter.

**Configuration addition for `application-local.yml`:**
```yaml
management:
  tracing:
    sampling:
      probability: 1.0
```
This ensures all spans are sampled locally (required to see tracing in logs).

**Resolved:** Yes

---

### A3: Micrometer Prometheus Registry

**Question:** Is `micrometer-registry-prometheus` cached and what Spring Boot autoconfiguration
is needed?

**Proposed Answer:**

`micrometer-registry-prometheus:1.16.3` JAR is present in the local cache. Spring Boot
autoconfigures a `PrometheusMeterRegistry` automatically when the dependency is on the classpath —
no additional `@Bean` registration needed. The `/actuator/prometheus` endpoint is enabled by
adding it to `management.endpoints.web.exposure.include`.

**`application-local.yml` addition:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
```

**Resolved:** Yes

---

## Domain B — Design Decisions

### B1: FraudEvaluationException — Stub vs Full Hierarchy

**Question:** `FraudEvaluationException` was stubbed in AML-FRAUD-001 as a direct
`RuntimeException` subclass. AML-FRAUD-002 requires it to extend `BusinessException` (abstract).
How to reconcile?

**Proposed Answer:**

The stub in `exception/FraudEvaluationException.java` must be replaced. The full hierarchy is:

```
RuntimeException
└── BusinessException  (abstract — new class)
    └── FraudEvaluationException  (replaces stub)
```

`BusinessException` is created as a new abstract class. `FraudEvaluationException` is updated to
extend `BusinessException` instead of `RuntimeException`. The existing two constructors are kept
unchanged — only the `extends` clause changes.

No other code references `FraudEvaluationException` beyond `FraudPersistenceService` which only
throws it — no callers catch it — so this is a safe in-place replacement.

**Resolved:** Yes

---

### B2: RuleContext — Plain Object vs Spring Bean

**Question:** `RuleContext` is specified as a plain object (not a Spring bean). What constraints
does this impose on its construction and the `clientProfileSupplier`?

**Proposed Answer:**

`RuleContext` is a plain Java class with a package-level or public constructor taking:
- `TransactionEntity transaction`
- `FraudPersistenceService fraudPersistenceService`
- `String traceId` (for structured logging inside query methods)
- `Supplier<Optional<ClientProfileEntity>> clientProfileSupplier`

The supplier is constructed in `RuleContextFactory.create()` as a memoising supplier:

```java
final String clientId = transaction.getClientId();
final Supplier<Optional<ClientProfileEntity>> profileSupplier = Suppliers.memoize(
    () -> fraudPersistenceService.findClientProfile(clientId, traceId));
```

`Suppliers.memoize` from Guava ensures the DB call is made at most once per `RuleContext`
instance regardless of how many rules call `getClientProfile()`.

**Guava availability:** Guava is a transitive dependency of Spring Boot — already on classpath.
No extra `pom.xml` entry needed.

**Resolved:** Yes

---

### B3: FraudEvaluationServiceImpl — Rule Ordering

**Question:** Rules are injected as `List<FraudRule>`. What controls their evaluation order?

**Proposed Answer:**

Spring injects `List<FraudRule>` ordered by `@Order` annotation value (lower = earlier).
`FraudEvaluationServiceImpl` declares:

```java
private final List<FraudRule> fraudRules;
```

Each rule implementation will carry `@Order(N)` where N matches its rule number (RULE-001 = 1,
RULE-006 = 6). This is set in AML-FRAUD-003 when the rules are implemented.

For AML-FRAUD-002, no `@Order` is needed on the `FraudEvaluationServiceImpl` itself — the
ordering concern belongs to the rule classes.

**Resolved:** Yes

---

### B4: @NewSpan — Dependency and Wiring

**Question:** `@NewSpan("fraud-rule-evaluation")` requires Micrometer Tracing on the classpath
and AOP. Does `spring-boot-starter-aop` need to be added?

**Proposed Answer:**

`@NewSpan` is processed by `ObservationAspect` which ships inside
`micrometer-tracing-bridge-otel` / `spring-boot-starter-actuator`. However it requires
Spring AOP proxying. `spring-boot-starter-aop` is **not yet in the pom.xml** and must be added.

Note: AML-FRAUD-005 also adds `spring-boot-starter-aop` for Resilience4j. Adding it here in
AML-FRAUD-002 is the correct sequencing — AML-FRAUD-005 will find it already present.

**Resolved:** Yes

---

### B5: CacheConfig — @EnableCaching Placement

**Question:** Should `@EnableCaching` go on `CacheConfig` or on the main `Application` class?

**Proposed Answer:**

Place `@EnableCaching` on `CacheConfig` (a dedicated `@Configuration` class) — this is the
standard Spring Boot pattern for feature-scoped config. The main `Application` class should
remain minimal (`@SpringBootApplication` only — Standard §3).

`CacheConfig` also declares `@Bean CaffeineCacheManager` with explicit per-cache specs built
via `CaffeineSpec`. Do **not** use `spring.cache.caffeine.spec` in YAML for this ticket —
per-cache TTL config requires programmatic `CacheManager` construction.

**Resolved:** Yes

---

### B6: RuleEngineConfig — @EnableConfigurationProperties Placement

**Question:** Where should `@EnableConfigurationProperties(RuleEngineConfig.class)` be declared?

**Proposed Answer:**

Place it on a new `@Configuration` class — either on `CacheConfig` (since it already exists) or
on a dedicated `EngineConfig`. Using `CacheConfig` keeps the number of config classes low.

Alternatively, Spring Boot 2.2+ supports adding `@ConfigurationPropertiesScan` to the main
application class to auto-detect all `@ConfigurationProperties` records in the package tree.
This is cleaner and avoids needing `@EnableConfigurationProperties` at all.

**Decision:** Add `@ConfigurationPropertiesScan` to `Application.java`. This auto-discovers
both `EncryptionProperties` (AML-FRAUD-001) and `RuleEngineConfig` (AML-FRAUD-002) without
individual `@EnableConfigurationProperties` annotations on config classes.

The `AesGcmFieldEncryptionService` currently uses `@EnableConfigurationProperties(EncryptionProperties.class)` —
this annotation can be removed from the service class once `@ConfigurationPropertiesScan` is on
`Application.java` (cleaner; a service should not be registering config properties).

**Resolved:** Yes

---

## Domain C — Testing

### C1: FraudEvaluationServiceImpl Unit Test Strategy

**Question:** The service injects `List<FraudRule>`. How do you unit-test iteration, disabled
rules, exception wrapping, and metric increments in isolation?

**Proposed Answer:**

Use `@ExtendWith(MockitoExtension.class)`. Create mock `FraudRule` instances for each scenario:

- `allRulesEnabled_shouldCollectAlerts()`: Two mock rules, both `isEnabled()=true`,
  both `isApplicable()=true`, each returning `Optional.of(alert)`. Assert 2 alerts returned,
  `saveAlert` called twice, counter incremented twice.

- `disabledRule_shouldBeSkipped()`: One rule `isEnabled()=false`. Assert `evaluate()` never
  called, no alert, counter not incremented.

- `notApplicableRule_shouldBeSkipped()`: Rule `isEnabled()=true` but `isApplicable()=false`.
  Assert `evaluate()` never called.

- `ruleThrowsException_shouldWrapInFraudEvaluationException()`: Rule `evaluate()` throws
  `RuntimeException`. Assert service throws `FraudEvaluationException`.

For Micrometer: inject a real `SimpleMeterRegistry` (no mocking needed) and assert counter
value via `registry.counter("fraud.alerts.generated", "ruleCode", ...).count()`.

For `MeterRegistry`/`Tracer`: inject `SimpleMeterRegistry` and a no-op `Tracer` from
`io.micrometer:micrometer-tracing` test support.

**Resolved:** Yes

---

### C2: RuleContext Unit Test Strategy

**Question:** How to test the `clientProfileSupplier` memoisation (at-most-once call)?

**Proposed Answer:**

Construct `RuleContext` directly with a mock `FraudPersistenceService`. Call `getClientProfile()`
twice. Verify `fraudPersistenceService.findClientProfile(...)` is invoked exactly once
(`verify(..., times(1))`). This confirms memoisation.

**Resolved:** Yes

---

## Summary of Open Items

All questions resolved. No open items remain.

| ID | Question | Resolved |
|---|---|---|
| A1 | Caffeine JAR availability | Yes |
| A2 | Micrometer Tracing OTel bridge | Yes |
| A3 | Prometheus registry | Yes |
| B1 | FraudEvaluationException hierarchy fix | Yes |
| B2 | RuleContext plain object + memoised supplier | Yes |
| B3 | Rule evaluation ordering via @Order | Yes |
| B4 | @NewSpan and spring-boot-starter-aop | Yes |
| B5 | @EnableCaching placement | Yes |
| B6 | RuleEngineConfig + @ConfigurationPropertiesScan | Yes |
| C1 | FraudEvaluationServiceImpl unit test strategy | Yes |
| C2 | RuleContext memoisation test | Yes |
