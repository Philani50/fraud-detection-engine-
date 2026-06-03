# AML-FRAUD-005 — Implementation Plan

**Status:** APPROVED  
**Depends on:** AML-FRAUD-001, AML-FRAUD-002, AML-FRAUD-004 (MapStruct available)

## Key Decisions

- Resilience4j JARs not in local cache — add `resilience4j-spring-boot3` + `resilience4j-micrometer`
  which will resolve via IntelliJ on rebuild
- `spring-boot-starter-webflux` 3.5.11 JAR is cached — use it for `TimeLimiter` async support
- `ClientProfileAdapterImpl` uses `@CircuitBreaker`, `@TimeLimiter`, `@Bulkhead` decorators
- Fallback returns `Optional.empty()` — never null
- Cache `"clientProfiles"` already declared in `CacheConfig`
- `ClientProfileMapper` uses MapStruct (available since AML-FRAUD-004)
- `ClientProfileServiceException` extends `BusinessException`

## Tasks

1. Add pom.xml deps: resilience4j-spring-boot3, resilience4j-micrometer, spring-boot-starter-webflux
2. ClientProfileServiceException
3. ClientProfileProperties record
4. ClientProfileResponse record
5. ClientProfileProxyClient interface (@GetExchange)
6. ClientProfileProxyConfig (@Configuration factory)
7. ClientProfileMapper (MapStruct)
8. ClientProfileAdapter interface
9. ClientProfileAdapterImpl with Resilience4j + Caffeine cache
10. application.yml additions: Resilience4j config + client-profile-service stub URL
