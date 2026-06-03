# AML-FRAUD-003 — Technical Analysis

**Status:** APPROVED  
**Depends on:** AML-FRAUD-001 (COMPLETE), AML-FRAUD-002 (COMPLETE)

## Key Decisions

| ID | Decision |
|---|---|
| A1 | No new dependencies needed — all types (`FraudRule`, `RuleContext`, entities, enums) already exist |
| A2 | `@Order(N)` on each rule class controls injection order in `List<FraudRule>` |
| A3 | All `private static final` string constants for alert detail templates — `AvoidDuplicateLiterals` |
| A4 | `FraudAlertFactory` sets `createdAt = LocalDateTime.now()` and `status = PENDING` |
| A5 | `TimeWindowCalculator` injected into each rule via `@RequiredArgsConstructor` |
| A6 | `isApplicable` returns early — prevents expensive `evaluate` call when irrelevant |
| A7 | `String.format(TEMPLATE, values...)` used for all detail messages |

All questions resolved. No open items.
