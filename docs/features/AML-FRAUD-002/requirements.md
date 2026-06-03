# AML-FRAUD-002 — FraudRule Interface, RuleContext, Engine & Exception Framework

**Status:** DRAFT  
**Project:** AML-FRAUD  
**Depends on:** AML-FRAUD-001  
**Standards Reference:** java-spring-boot-banking-standards-v2.md v2.1

---

## 1. Overview

Define the core engine contracts and orchestration layer that all six fraud rules will plug into.
This ticket establishes the `FraudRule` interface, `RuleContext` (memory-safe query delegate),
`FraudEvaluationService` (orchestrator), exception hierarchy, and the `RuleEngineConfig` for
toggling rules on/off. No individual rule logic is implemented here (that is AML-FRAUD-003).

---

## 2. Scope

**In scope:**
- `FraudRule` interface
- `RuleContext` class (DB-backed query delegate with Caffeine cache)
- `FraudEvaluationService` (interface + impl) — orchestrates all registered rules
- `RuleEngineConfig` (`@ConfigurationProperties`) — enable/disable each rule
- Exception hierarchy: `BusinessException`, `FraudEvaluationException`
- `CacheConfig` — Caffeine cache configuration
- `RuleContextFactory` — builds a `RuleContext` per evaluation request

**Out of scope:**
- Individual rule implementations (AML-FRAUD-003)
- REST endpoint / request-response records (AML-FRAUD-004)
- External adapter / Resilience4j (AML-FRAUD-005)

---

## 3. FraudRule Interface

Located in `fraudrule/` package.

```java
public interface FraudRule {
    String getRuleCode();
    String getRuleName();
    boolean isEnabled();
    boolean isApplicable(TransactionEntity transaction);
    Optional<FraudAlertEntity> evaluate(TransactionEntity transaction, RuleContext context);
}
```

**Standard compliance:**
- Interface method parameters must **not** be `final` (Standard §4 — `RedundantModifier` rule).
- No redundant `public` on interface methods (Standard §1).
- `Optional` return — never `null` (Standard §16).
- Each of the six rule classes is its own dedicated implementation (Standard §5/§18 OCP/SRP).

---

## 4. RuleContext

Located in `fraudrule/context/` package.

**Purpose:** Prevents O(n²) memory traps by delegating all time-window lookups to indexed
database queries or a Caffeine cache. Rules must never receive a raw `List<TransactionEntity>`
(Standard §16, §28).

### 4.1 RuleContext Fields (constructor-injected)

| Field | Type | Purpose |
|---|---|---|
| `transaction` | `TransactionEntity` | The transaction under evaluation |
| `clientId` | `String` | Extracted from the transaction for convenience |
| `fraudPersistenceService` | `FraudPersistenceService` | DB query delegate |
| `clientProfileSupplier` | `Supplier<Optional<ClientProfileEntity>>` | Lazy-loaded, cached profile |

### 4.2 RuleContext Query Methods

All return types use `Optional` or collections — never `null`.

| Method | Description |
|---|---|
| `getCashTransactionsSince(LocalDateTime since, BigDecimal belowAmount)` | DEPOSIT/WITHDRAWAL below threshold in window |
| `getTransactionCountSince(LocalDateTime since)` | Count of all transactions for this client since timestamp |
| `getTransactionsBetween(LocalDateTime from, LocalDateTime to)` | All transactions in a window (for rapid movement rule) |
| `getClientProfile()` | `Optional<ClientProfileEntity>` — fetched once, cached in supplier |

**Important:** `RuleContext` is **not** a Spring bean. It is a plain object created per-request
by `RuleContextFactory`. It holds a reference to `FraudPersistenceService` which is the Spring
bean performing the actual DB calls.

### 4.3 RuleContextFactory

- `@Component` in `fraudrule/context/` package.
- Single method: `create(TransactionEntity transaction)` → `RuleContext`.
- Injects `FraudPersistenceService` via constructor.
- The `clientProfileSupplier` is created as a `Supplier` backed by `FraudPersistenceService.findClientProfile(clientId)` so the DB call is made at most once per evaluation.

---

## 5. Caffeine Cache (Standard §28)

Located in `config/CacheConfig.java`.

```
Cache name: "clientProfiles"
  - maximumSize: 500
  - expireAfterWrite: 5 minutes
  - refreshAfterWrite: 4 minutes
  - recordStats() enabled

Cache name: "recentTransactionCounts"
  - maximumSize: 2_000
  - expireAfterWrite: 2 minutes
  - recordStats() enabled
```

**Rules (Standard §28):**
- TTL **always** required — no unbounded caches.
- PII fields (names, IDs) must **never** be cached — only `clientId` (reference key) and aggregates.
- All cache names declared explicitly in `CacheConfig` (no on-demand creation).
- Caffeine `recordStats()` enabled and exposed via Micrometer.

**`@EnableCaching`** annotation on `CacheConfig`.

---

## 6. FraudEvaluationService

### 6.1 Interface (in `service/`)

```java
public interface FraudEvaluationService {
    List<FraudAlertEntity> evaluate(TransactionEntity transaction);
}
```

### 6.2 Implementation (in `service/impl/`)

`FraudEvaluationServiceImpl` — `@Service`, `@RequiredArgsConstructor`, `@Slf4j`.

**Behaviour:**
1. Receive a `TransactionEntity`.
2. Build a `RuleContext` via `RuleContextFactory`.
3. Iterate over all `List<FraudRule>` beans (Spring-injected, ordered by `@Order` or `Ordered`).
4. For each rule where `isEnabled()` is `true` and `isApplicable(transaction)` is `true`:
   - Call `rule.evaluate(transaction, context)`.
   - If `Optional<FraudAlertEntity>` is present: persist via `FraudPersistenceService.saveAlert(alert)`.
   - Increment Micrometer counter: `fraud.alerts.generated` tagged `ruleCode=<rule.getRuleCode()>` (Standard §12).
   - Log: `log.info("Fraud alert generated [correlationId={}, ruleCode={}, alertId={}]", traceId, ruleCode, alertId)`.
5. Return the list of all generated `FraudAlertEntity` objects.
6. Wrap evaluation in `@NewSpan("fraud-rule-evaluation")` for Micrometer Tracing (Standard §22).

**Structured logging rules (Standard §9):**
- Use parameterised templates only — no string concatenation.
- Never log PII (names, IDs, account numbers).
- `correlationId` = active Micrometer `traceId`.

**Micrometer Timer (Standard §12):**
Wrap the full evaluation loop in:
```
Timer.builder("fraud.evaluation.duration")
    .tag("clientId", clientId)  // clientId is not PII
    .register(meterRegistry)
    .record(() -> { ... })
```

---

## 7. Exception Hierarchy (Standard §10)

Located in `exception/` package.

```
RuntimeException
└── BusinessException          (abstract base — expected operational diversions)
    └── FraudEvaluationException  (thrown when rule evaluation fails unrecoverably)
```

**`BusinessException`:**
- Abstract class extending `RuntimeException`.
- Two constructors: `(String message)` and `(String message, Throwable cause)`.
- Logged at `WARN` level by the global handler.

**`FraudEvaluationException`:**
- Extends `BusinessException`.
- Used when a rule throws an unexpected exception during `evaluate(...)`.
- The engine catches unexpected `Exception` from individual rules, wraps in `FraudEvaluationException`, logs at `ERROR`, and rethrows.

**Global Exception Handler (`@RestControllerAdvice`)** — implemented in AML-FRAUD-004 but exception classes defined here.

---

## 8. Rule Engine Configuration (Standard §6, §25)

Located in `config/RuleEngineConfig.java`.

```java
@ConfigurationProperties(prefix = "app.fraud-rules")
public record RuleEngineConfig(
    boolean rule001Enabled,
    boolean rule002Enabled,
    boolean rule003Enabled,
    boolean rule004Enabled,
    boolean rule005Enabled,
    boolean rule006Enabled
) {}
```

- `@ConfigurationProperties` — never `@Value` for structured config (Standard §6).
- Enabled via `@EnableConfigurationProperties(RuleEngineConfig.class)` on a `@Configuration` class.
- Default all `true` in `application-local.yml`.

Each `FraudRule` implementation constructor-injects `RuleEngineConfig` and returns the relevant
boolean from `isEnabled()`.

---

## 9. Observability (Standard §9, §12, §22)

| Metric / Trace | Name | Tags | Trigger |
|---|---|---|---|
| Counter | `fraud.alerts.generated` | `ruleCode` | Every alert persisted |
| Timer | `fraud.evaluation.duration` | `clientId` | Full evaluation loop |
| Span | `fraud-rule-evaluation` | — | `@NewSpan` on service method |
| Log `INFO` | Alert generated | `correlationId`, `ruleCode`, `alertId` | Alert persisted |
| Log `WARN` | Rule evaluation skipped | `correlationId`, `ruleCode`, `reason` | Rule disabled or not applicable |
| Log `ERROR` | Rule evaluation failed | `correlationId`, `ruleCode`, exception | Unexpected exception in rule |

---

## 10. Dependencies to Add to pom.xml

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-cache` | Spring Cache abstraction |
| `com.github.ben-manes.caffeine:caffeine` | Caffeine local cache |
| `io.micrometer:micrometer-tracing-bridge-otel` | Micrometer → OTel bridge (Standard §22) |
| `io.opentelemetry:opentelemetry-exporter-otlp` | OTLP trace export |
| `io.micrometer:micrometer-registry-prometheus` | Prometheus metric export (Actuator) |

---

## 11. Acceptance Criteria

| # | Criterion |
|---|---|
| AC-1 | `FraudEvaluationServiceImpl` iterates all enabled rules and persists alerts |
| AC-2 | Disabled rules (via `RuleEngineConfig`) are skipped without error |
| AC-3 | `fraud.alerts.generated` counter increments once per alert, tagged with `ruleCode` |
| AC-4 | `fraud.evaluation.duration` timer records per-evaluation timing |
| AC-5 | `RuleContext` never holds a full `List<TransactionEntity>` in memory — all data via DB query methods |
| AC-6 | `clientProfileSupplier` calls `FraudPersistenceService` at most once per evaluation |
| AC-7 | `FraudEvaluationException` is thrown and logged at `ERROR` when a rule throws unexpectedly |
| AC-8 | No PII appears in any log statement |
| AC-9 | `CacheConfig` declares all cache names explicitly with TTL |
| AC-10 | Unit tests cover: all rules enabled, some rules disabled, rule throws exception, no rules applicable |

---

## 12. Package Layout

```
za.co.capitecbank/
├── config/
│   ├── CacheConfig.java
│   └── RuleEngineConfig.java
├── exception/
│   ├── BusinessException.java
│   └── FraudEvaluationException.java
└── fraudrule/
    ├── FraudRule.java                         (interface)
    ├── context/
    │   ├── RuleContext.java
    │   └── RuleContextFactory.java
    └── service/
        ├── FraudEvaluationService.java        (interface)
        └── impl/
            └── FraudEvaluationServiceImpl.java
```
