# AML-FRAUD-001 — Core Domain Entities, Flyway Migrations & Persistence Setup

**Status:** DRAFT  
**Project:** AML-FRAUD  
**Standards Reference:** java-spring-boot-banking-standards-v2.md v2.1

---

## 1. Overview

Establish the foundational data model for the Fraud Rule Engine service. This ticket covers all
JPA entities, Flyway migration scripts, Spring Data repositories, and the persistence service
wrapper. No business logic or rule evaluation is included here — this is the data layer only.

---

## 2. Scope

**In scope:**
- `Transaction` entity + repository
- `ClientProfile` entity + `PpiChangeRecord` embedded history + repository
- `FraudAlert` entity + repository
- `EncryptedStringConverter` (AES-256-GCM via `FieldEncryptionService`)
- `FraudPersistenceService` wrapping all three repositories
- Flyway migration scripts: `V1__create_transaction_table.sql`, `V2__create_client_profile_table.sql`, `V3__create_fraud_alert_table.sql`
- H2 configuration for `local` and `test` profiles
- `application-local.yml` DB config

**Out of scope:**
- Fraud rules (AML-FRAUD-003)
- REST API (AML-FRAUD-004)
- External adapter / Resilience4j (AML-FRAUD-005)
- Security / OAuth2 config (AML-FRAUD-004)

---

## 3. Domain Model

### 3.1 Transaction Entity

| Field | Type | Constraints |
|---|---|---|
| `transactionId` | `UUID` | `@Id`, auto-generated (`UUID.randomUUID()`), `@Column(updatable = false)` |
| `accountId` | `String` | `@NotBlank`, `@Column(nullable = false)` |
| `clientId` | `String` | `@NotBlank`, `@Column(nullable = false)`, indexed |
| `amount` | `BigDecimal` | `@NotNull`, `@DecimalMin("0.01")`, `@Column(nullable = false, precision = 19, scale = 4)` |
| `currency` | `String` | Default `"ZAR"`, `@Column(nullable = false, length = 3)` |
| `transactionType` | `TransactionType` (enum) | `@Enumerated(EnumType.STRING)`, `@NotNull` |
| `channel` | `Channel` (enum) | `@Enumerated(EnumType.STRING)`, `@NotNull` |
| `merchantCategory` | `String` | nullable |
| `destinationAccountId` | `String` | nullable |
| `timestamp` | `LocalDateTime` | `@NotNull`, `@Column(nullable = false)`, indexed |
| `location` | `String` | nullable |
| `createdDate` | `LocalDateTime` | `@Column(updatable = false)` |
| `modifiedDate` | `LocalDateTime` | |
| `version` | `long` | `@Version` — optimistic locking (Standard §14) |

**Enums (in `enums/` package):**
- `TransactionType`: `DEPOSIT`, `WITHDRAWAL`, `TRANSFER`, `PAYMENT`, `CARD_PURCHASE`
- `Channel`: `BRANCH`, `ATM`, `ONLINE`, `MOBILE`, `POS`

### 3.2 ClientProfile Entity

| Field | Type | Constraints |
|---|---|---|
| `id` | `Long` | `@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)` |
| `clientId` | `String` | `@NotBlank`, `@Column(unique = true, nullable = false)`, indexed |
| `fullName` | `String` | `@Convert(converter = EncryptedStringConverter.class)` — PII (Standard §26) |
| `idNumber` | `String` | `@Convert(converter = EncryptedStringConverter.class)` — PII (Standard §26) |
| `accountOpenDate` | `LocalDate` | `@NotNull` |
| `averageMonthlyIncome` | `BigDecimal` | `@NotNull`, `@Column(precision = 19, scale = 4)` |
| `riskRating` | `RiskRating` (enum) | `@Enumerated(EnumType.STRING)`, `@NotNull` |
| `lastProfileUpdateDate` | `LocalDateTime` | `@Column(nullable = false)` |
| `ppiChangeHistory` | `List<PpiChangeRecord>` | `@ElementCollection`, `@CollectionTable` |
| `createdDate` | `LocalDateTime` | `@Column(updatable = false)` |
| `modifiedDate` | `LocalDateTime` | |
| `version` | `long` | `@Version` (Standard §14) |

**`PpiChangeRecord` (embeddable):**
- `fieldChanged` (String)
- `oldValue` (String) — encrypted at rest via `EncryptedStringConverter`
- `newValue` (String) — encrypted at rest via `EncryptedStringConverter`
- `changeDate` (LocalDateTime)

**Enum:**
- `RiskRating`: `LOW`, `MEDIUM`, `HIGH`

### 3.3 FraudAlert Entity

| Field | Type | Constraints |
|---|---|---|
| `alertId` | `UUID` | `@Id`, auto-generated, `@Column(updatable = false)` |
| `transactionId` | `UUID` | `@Column(nullable = false)` — FK reference (no JPA join — decoupled) |
| `clientId` | `String` | `@Column(nullable = false)`, indexed |
| `ruleCode` | `String` | `@Column(nullable = false, length = 20)` |
| `ruleName` | `String` | `@Column(nullable = false)` |
| `riskScore` | `Integer` | `@Min(1)`, `@Max(100)`, `@Column(nullable = false)` |
| `severity` | `AlertSeverity` (enum) | `@Enumerated(EnumType.STRING)`, `@NotNull` |
| `status` | `AlertStatus` (enum) | `@Enumerated(EnumType.STRING)`, `@NotNull`, default `PENDING` |
| `details` | `String` | `@Column(columnDefinition = "TEXT")` |
| `createdAt` | `LocalDateTime` | `@Column(nullable = false, updatable = false)` |
| `reviewedBy` | `String` | nullable |
| `reviewedAt` | `LocalDateTime` | nullable |
| `createdDate` | `LocalDateTime` | `@Column(updatable = false)` |
| `modifiedDate` | `LocalDateTime` | |
| `version` | `long` | `@Version` (Standard §14) |

**Enums:**
- `AlertSeverity`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- `AlertStatus`: `PENDING`, `UNDER_REVIEW`, `CONFIRMED_FRAUD`, `CLEARED`

---

## 4. Encryption at Rest (Standard §26)

### 4.1 FieldEncryptionService

- Interface in `security/` package with `encrypt(String plaintext)` and `decrypt(String ciphertext)` methods.
- Implementation uses **AES-256-GCM** (never AES-ECB).
- Encryption key must be sourced from Vault / environment — never from `application.yml`.
- For `local` and `test` profiles: use a fixed test key configured via `application-local.yml` (`app.encryption.key`).
- Supports envelope encryption structure for key rotation readiness.

### 4.2 EncryptedStringConverter

- `@Converter` `@Component` in `persistence/converter/` package.
- Implements `AttributeConverter<String, String>`.
- Delegates to `FieldEncryptionService`.
- Null-safe: returns `null` if input is `null`.
- Constructor-injected (`@RequiredArgsConstructor`) — no field injection (Standard §3).

---

## 5. Repository Interfaces

All in `persistence/repository/` package. Extend `JpaRepository`. No `@Repository` annotation needed (detected automatically).

### TransactionRepository
```
- findByClientIdAndTimestampAfter(String clientId, LocalDateTime after) → List<TransactionEntity>
- countByClientIdAndTimestampAfter(String clientId, LocalDateTime after) → long
- findByClientIdAndTransactionTypeInAndTimestampAfterAndAmountLessThan(
    String clientId, List<TransactionType> types, LocalDateTime after, BigDecimal amount) → List<TransactionEntity>
- findByClientIdAndTimestampBetweenOrderByTimestampAsc(
    String clientId, LocalDateTime from, LocalDateTime to) → List<TransactionEntity>
```

### ClientProfileRepository
```
- findByClientId(String clientId) → Optional<ClientProfileEntity>
```

### FraudAlertRepository
```
- findByClientId(String clientId) → List<FraudAlertEntity>
- findByTransactionId(UUID transactionId) → List<FraudAlertEntity>
- findByStatus(AlertStatus status) → List<FraudAlertEntity>
```

---

## 6. FraudPersistenceService

Located in `persistence/` package. Wraps all three repositories. **No rule evaluator may call a repository directly** (Standard §14).

### Read methods — `@Transactional(readOnly = true)` (Standard §23)
- `findTransactionsByClientSince(String clientId, LocalDateTime since)` → `List<TransactionEntity>`
- `countTransactionsByClientSince(String clientId, LocalDateTime since)` → `long`
- `findCashTransactionsByClientSince(String clientId, LocalDateTime since, BigDecimal belowAmount)` → `List<TransactionEntity>`
- `findTransactionsByClientBetween(String clientId, LocalDateTime from, LocalDateTime to)` → `List<TransactionEntity>`
- `findClientProfile(String clientId)` → `Optional<ClientProfileEntity>`
- `findAlertsByTransactionId(UUID transactionId)` → `List<FraudAlertEntity>`

### Write methods — `@Transactional` (short, no HTTP calls inside — Standard §23)
- `saveAlert(FraudAlertEntity alert)` → `FraudAlertEntity`
- `saveTransaction(TransactionEntity transaction)` → `TransactionEntity`

---

## 7. Flyway Migrations (Standard §21)

Location: `src/main/resources/db/migration/`

| File | Purpose |
|---|---|
| `V1__create_transaction_table.sql` | `transaction` table with all columns + indexes on `client_id`, `timestamp` |
| `V2__create_client_profile_table.sql` | `client_profile` table + `ppi_change_record` collection table |
| `V3__create_fraud_alert_table.sql` | `fraud_alert` table + index on `client_id`, `transaction_id` |

Rules:
- Use `CREATE TABLE IF NOT EXISTS` (idempotent — Standard §21).
- All columns match entity definitions exactly.
- Enum columns stored as `VARCHAR(50)`.
- PII columns (`full_name`, `id_number`, `old_value`, `new_value`) typed as `TEXT` (ciphertext is longer than plaintext).

---

## 8. Application Configuration

### H2 (local + test profiles) — `application-local.yml`
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:fraudengine;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
app:
  encryption:
    key: test-aes-key-32-bytes-for-local!
```

### PostgreSQL (production reference — no secrets in yml — Standard §25)
```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: none
  flyway:
    enabled: false  # run out-of-band in production (Standard §21)
```

---

## 9. Lombok & Entity Annotation Rules (Standard §14, §18)

Entity classes must use this exact annotation order:
```
@Builder @Setter @Getter @AllArgsConstructor @NoArgsConstructor @Entity @Table(name = "...")
```
- `@Data` is **forbidden** on JPA entities.
- `@Service` implementation classes must **not** be `final` (breaks CGLIB proxying — Standard §18).
- Utility classes (`FieldEncryptionService` impl if static helper) must be `final` with private constructor.

---

## 10. Dependencies to Add to pom.xml

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-data-jpa` | JPA / Hibernate |
| `flyway-core` | Migrations |
| `flyway-database-postgresql` | PostgreSQL dialect for Flyway |
| `postgresql` (runtime) | PostgreSQL JDBC driver |
| `com.h2database:h2` (test/runtime local) | In-memory DB for local/test |
| `spring-boot-starter-validation` | Jakarta Bean Validation |

---

## 11. Acceptance Criteria

| # | Criterion |
|---|---|
| AC-1 | All three entities persist and retrieve correctly via `FraudPersistenceService` under H2 |
| AC-2 | `fullName` and `idNumber` are stored encrypted in the DB; the application layer sees plaintext |
| AC-3 | All three Flyway scripts execute cleanly on startup with H2 in PostgreSQL mode |
| AC-4 | `@Version` field increments on concurrent update; `OptimisticLockException` thrown on conflict |
| AC-5 | `FraudPersistenceService` read methods annotated `@Transactional(readOnly = true)` |
| AC-6 | No repository is called directly from outside `FraudPersistenceService` |
| AC-7 | PII fields (`fullName`, `idNumber`) never appear in log output (Standard §9) |
| AC-8 | Unit tests cover `EncryptedStringConverter` encrypt/decrypt, null input, and round-trip |
| AC-9 | `@Data` is absent from all entity classes |
| AC-10 | All entity enum columns use `@Enumerated(EnumType.STRING)` |

---

## 12. Package Layout

```
za.co.capitecbank/
├── enums/
│   ├── TransactionType.java
│   ├── Channel.java
│   ├── RiskRating.java
│   ├── AlertSeverity.java
│   └── AlertStatus.java
├── persistence/
│   ├── converter/
│   │   └── EncryptedStringConverter.java
│   ├── entity/
│   │   ├── TransactionEntity.java
│   │   ├── ClientProfileEntity.java
│   │   ├── PpiChangeRecord.java
│   │   └── FraudAlertEntity.java
│   ├── repository/
│   │   ├── TransactionRepository.java
│   │   ├── ClientProfileRepository.java
│   │   └── FraudAlertRepository.java
│   └── FraudPersistenceService.java
└── security/
    ├── FieldEncryptionService.java           (interface)
    └── impl/
        └── AesGcmFieldEncryptionService.java (implementation)
```
