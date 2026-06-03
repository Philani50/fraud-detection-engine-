# AML-FRAUD-003 — Six Fraud Rule Implementations (RULE-001 to RULE-006)

**Status:** DRAFT  
**Project:** AML-FRAUD  
**Depends on:** AML-FRAUD-001, AML-FRAUD-002  
**Standards Reference:** java-spring-boot-banking-standards-v2.md v2.1

---

## 1. Overview

Implement all six fraud detection rules as individual Spring `@Component` classes, each
implementing the `FraudRule` interface defined in AML-FRAUD-002. Each rule is independently
toggleable, fully testable in isolation, and must never load a raw `List<TransactionEntity>`
into memory — all time-window lookups delegate to `RuleContext`.

---

## 2. Scope

**In scope:**
- 6 `FraudRule` implementations: `StructuringRule`, `ProfileChangeRule`, `SuspiciousSpendRule`,
  `VelocityRule`, `RoundAmountRule`, `RapidMovementRule`
- Shared time-window utility extracted to `TimeWindowCalculator` component (Standard §5/§18 — DRY)
- Alert factory helper `FraudAlertFactory` (builds `FraudAlertEntity` consistently)

**Out of scope:**
- Engine orchestration (AML-FRAUD-002)
- REST API (AML-FRAUD-004)
- Persistence layer (AML-FRAUD-001)

---

## 3. Common Rule Contract

Every rule class must:

1. Be annotated `@Component`, `@RequiredArgsConstructor`, `@Slf4j`.
2. Constructor-inject `RuleEngineConfig` (for `isEnabled()`).
3. Implement `isApplicable(TransactionEntity transaction)` — guard before heavy queries.
4. Implement `evaluate(TransactionEntity transaction, RuleContext context)` → `Optional<FraudAlertEntity>`.
5. **Never** call a repository directly — only use `RuleContext` query methods.
6. Log at `DEBUG` on entry: `log.debug("Evaluating [correlationId={}, rule={}]", traceId, getRuleCode())`.
7. Log at `INFO` when an alert is generated: `log.info("Alert raised [correlationId={}, rule={}, clientId={}]", traceId, getRuleCode(), clientId)`.
8. Never log PII (Standard §9).

---

## 4. Rule Specifications

### RULE-001 — Structuring Over Time

| Property | Value |
|---|---|
| Class | `StructuringRule` |
| Rule code | `RULE-001` |
| Rule name | `Structuring Over Time` |
| Severity | `HIGH` |
| Risk score | `75` |
| `isEnabled()` | `ruleEngineConfig.rule001Enabled()` |

**Logic:**
1. `isApplicable`: `transactionType` is `DEPOSIT` or `WITHDRAWAL`.
2. `evaluate`:
   - Query `RuleContext.getCashTransactionsSince(now minus 24h, R10_000)` — returns cash transactions each below R10,000 in the 24-hour window (including the current transaction).
   - If count ≥ 3 **and** sum of amounts ≥ R10,000 → generate alert.
   - Details: `"Structuring detected: {count} cash transactions totalling {sum} ZAR within 24 hours, each below R10,000."`

**Constants (`private static final`):**
```
THRESHOLD_AMOUNT     = BigDecimal("10000")
MIN_TRANSACTION_COUNT = 3
WINDOW_HOURS         = 24
```

---

### RULE-002 — Large Transaction After Profile Change

| Property | Value |
|---|---|
| Class | `ProfileChangeRule` |
| Rule code | `RULE-002` |
| Rule name | `Large Transaction After Profile Change` |
| Severity | `CRITICAL` |
| Risk score | `90` |
| `isEnabled()` | `ruleEngineConfig.rule002Enabled()` |

**Logic:**
1. `isApplicable`: `transaction.amount` ≥ R50,000.
2. `evaluate`:
   - Fetch `clientProfile` via `RuleContext.getClientProfile()`.
   - If profile absent → return `Optional.empty()` (cannot evaluate — log `WARN`).
   - Check `clientProfile.lastProfileUpdateDate` is within the last 48 hours.
   - If yes → generate alert.
   - Details: `"Large transaction of {amount} ZAR occurred within 48 hours of a PII profile update on {updateDate}."`

**Constants:**
```
LARGE_AMOUNT_THRESHOLD = BigDecimal("50000")
PROFILE_CHANGE_WINDOW_HOURS = 48
```

---

### RULE-003 — Suspicious Spend Behaviour

| Property | Value |
|---|---|
| Class | `SuspiciousSpendRule` |
| Rule code | `RULE-003` |
| Rule name | `Suspicious Spend Behaviour` |
| Severity | `MEDIUM` |
| Risk score | `60` |
| `isEnabled()` | `ruleEngineConfig.rule003Enabled()` |

**Logic:**
1. `isApplicable`: always `true` (all transaction types evaluated).
2. `evaluate`:
   - Fetch `clientProfile` via `RuleContext.getClientProfile()`.
   - If profile absent or `averageMonthlyIncome` is zero → return `Optional.empty()`, log `WARN`.
   - If `transaction.amount` > `averageMonthlyIncome × 3` → generate alert.
   - Details: `"Transaction amount {amount} ZAR exceeds 300% of average monthly income ({income} ZAR)."`

**Constants:**
```
INCOME_MULTIPLIER_THRESHOLD = new BigDecimal("3")
```

---

### RULE-004 — Velocity Spike

| Property | Value |
|---|---|
| Class | `VelocityRule` |
| Rule code | `RULE-004` |
| Rule name | `Velocity Spike` |
| Severity | `HIGH` |
| Risk score | `70` |
| `isEnabled()` | `ruleEngineConfig.rule004Enabled()` |

**Logic:**
1. `isApplicable`: always `true`.
2. `evaluate`:
   - Query `RuleContext.getTransactionCountSince(now minus 1h)`.
   - If count ≥ 10 → generate alert.
   - Details: `"Velocity spike detected: {count} transactions within the last 1 hour."`

**Constants:**
```
VELOCITY_THRESHOLD = 10
WINDOW_MINUTES     = 60
```

---

### RULE-005 — Round Amount Pattern

| Property | Value |
|---|---|
| Class | `RoundAmountRule` |
| Rule code | `RULE-005` |
| Rule name | `Round Amount Pattern` |
| Severity | `MEDIUM` |
| Risk score | `55` |
| `isEnabled()` | `ruleEngineConfig.rule005Enabled()` |

**Logic:**
1. `isApplicable`: `transaction.amount.remainder(BigDecimal.valueOf(1000)).compareTo(BigDecimal.ZERO) == 0` — only process round-amount transactions.
2. `evaluate`:
   - Query `RuleContext.getCashTransactionsSince(now minus 24h, BigDecimal.MAX_VALUE)` to get all transactions.
   - Filter for those where `amount.remainder(1000) == 0`.
   - If count ≥ 3 → generate alert.
   - Details: `"Round amount pattern detected: {count} transactions with amounts divisible by R1,000 within 24 hours."`

**Note:** `isApplicable` acts as a fast exit — only transactions that are themselves round amounts enter the full evaluation.

**Constants:**
```
ROUND_AMOUNT_DIVISOR = BigDecimal("1000")
MIN_ROUND_COUNT      = 3
WINDOW_HOURS         = 24
```

---

### RULE-006 — High-Risk Rapid Movement

| Property | Value |
|---|---|
| Class | `RapidMovementRule` |
| Rule code | `RULE-006` |
| Rule name | `High-Risk Rapid Movement` |
| Severity | `CRITICAL` |
| Risk score | `85` |
| `isEnabled()` | `ruleEngineConfig.rule006Enabled()` |

**Logic:**
1. `isApplicable`: `transactionType` is `TRANSFER` or `WITHDRAWAL`.
2. `evaluate`:
   - Check if an incoming `DEPOSIT` or `TRANSFER` of ≥ R20,000 exists in the window `[now minus 30min, now]` for the same `clientId`.
   - Use `RuleContext.getTransactionsBetween(now minus 30min, now)`.
   - From this list, find the first inbound transaction ≥ R20,000.
   - If found: check if `transaction.amount` ≥ 80% of that inbound amount.
   - If yes → generate alert.
   - Details: `"Rapid fund movement detected: incoming {inAmount} ZAR followed by outgoing {outAmount} ZAR ({percent}%) within 30 minutes."`

**Constants:**
```
INBOUND_THRESHOLD        = BigDecimal("20000")
OUTBOUND_RATIO_THRESHOLD = new BigDecimal("0.80")
WINDOW_MINUTES           = 30
```

---

## 5. Shared Utilities

### 5.1 TimeWindowCalculator

`@Component` in `fraudrule/util/` package.

Extracted because multiple rules share time-window start calculation (Standard §5 — extract
when same logic appears in 2+ places):

```java
@Component
public class TimeWindowCalculator {
    public LocalDateTime hoursAgo(final int hours) {
        return LocalDateTime.now().minusHours(hours);
    }
    public LocalDateTime minutesAgo(final int minutes) {
        return LocalDateTime.now().minusMinutes(minutes);
    }
}
```

### 5.2 FraudAlertFactory

`@Component` in `fraudrule/util/` package. Builds `FraudAlertEntity` consistently:

```java
@Component
public class FraudAlertFactory {
    public FraudAlertEntity create(
        TransactionEntity transaction,
        String ruleCode,
        String ruleName,
        int riskScore,
        AlertSeverity severity,
        String details) { ... }
}
```

Sets: `alertId = UUID.randomUUID()`, `status = PENDING`, `createdAt = LocalDateTime.now()`.

---

## 6. Alert Details String Constants

All detail message templates must be declared as `private static final String` constants within
each rule class (Standard §1 — `AvoidDuplicateLiterals`). No inline string literals for messages
used more than once.

---

## 7. Acceptance Criteria

| # | Criterion |
|---|---|
| AC-1 | RULE-001: 3 cash transactions each < R10,000 totalling ≥ R10,000 in 24h → alert generated |
| AC-2 | RULE-001: 2 transactions (below threshold count) → no alert |
| AC-3 | RULE-001: 3 transactions each < R10,000 but total < R10,000 → no alert |
| AC-4 | RULE-002: transaction ≥ R50,000 + profile updated < 48h ago → alert |
| AC-5 | RULE-002: transaction ≥ R50,000 + profile updated > 48h ago → no alert |
| AC-6 | RULE-002: profile not found → no alert, WARN logged |
| AC-7 | RULE-003: transaction > 300% of averageMonthlyIncome → alert |
| AC-8 | RULE-003: transaction ≤ 300% of averageMonthlyIncome → no alert |
| AC-9 | RULE-004: 10+ transactions in last 1 hour → alert |
| AC-10 | RULE-004: 9 transactions → no alert |
| AC-11 | RULE-005: 3+ round-amount transactions in 24h → alert |
| AC-12 | RULE-005: non-round-amount transaction → `isApplicable` returns false, skipped |
| AC-13 | RULE-006: inbound ≥ R20,000 followed by outbound ≥ 80% within 30min → alert |
| AC-14 | RULE-006: outbound < 80% of inbound → no alert |
| AC-15 | All rules: disabled via `RuleEngineConfig` → `isEnabled()` returns false, engine skips |
| AC-16 | No rule calls a repository directly — all queries via `RuleContext` |
| AC-17 | Unit test per rule covers all AC scenarios above using mocked `RuleContext` |
| AC-18 | No PII in any log output from any rule |

---

## 8. Package Layout

```
za.co.capitecbank/
└── fraudrule/
    ├── FraudRule.java                      (interface — from AML-FRAUD-002)
    ├── impl/
    │   ├── StructuringRule.java
    │   ├── ProfileChangeRule.java
    │   ├── SuspiciousSpendRule.java
    │   ├── VelocityRule.java
    │   ├── RoundAmountRule.java
    │   └── RapidMovementRule.java
    └── util/
        ├── TimeWindowCalculator.java
        └── FraudAlertFactory.java
```
