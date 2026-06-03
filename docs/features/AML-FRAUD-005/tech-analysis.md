# AML-FRAUD-005 — Technical Analysis

**Status:** APPROVED  
**Depends on:** AML-FRAUD-001, AML-FRAUD-002

## Key Decisions

| ID | Decision |
|---|---|
| A1 | `spring-boot-starter-aop` already in pom (AML-FRAUD-002) |
| A2 | Resilience4j-spring-boot3 JAR not cached — will resolve via IntelliJ. Skipping for now; adapter implemented without Resilience4j decorators as a stub — AML-FRAUD-005 is marked deferred |
| A3 | `ClientProfileAdapterImpl` returns `Optional.empty()` as fallback — never null |
| A4 | Cache name `clientProfiles` already declared in `CacheConfig` (AML-FRAUD-002) |
| A5 | `ClientProfileMapper` uses MapStruct (once mapstruct dep added in AML-FRAUD-004) |
| A6 | Deferred to after AML-FRAUD-004 so MapStruct is available |

AML-FRAUD-005 deferred until after AML-FRAUD-004 ships MapStruct.
