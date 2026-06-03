# AML-FRAUD-001 — Implementation Plan

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
Add all dependencies identified in tech-analysis A1:
- `spring-boot-starter-data-jpa`
- `flyway-core`
- `flyway-database-postgresql`
- `postgresql` (runtime scope)
- `h2` (runtime scope)
- `spring-boot-starter-validation`

No version pins needed — all managed by Spring Boot 3.5.14 BOM.

**Test class + method:** N/A — validated by `mvn validate` (Checkstyle + Spotless) and `mvn compile`.

---

### TASK-02 — Create Enums

**Files to touch:**
- `src/main/java/za/co/capitecbank/enums/TransactionType.java`
- `src/main/java/za/co/capitecbank/enums/Channel.java`
- `src/main/java/za/co/capitecbank/enums/RiskRating.java`
- `src/main/java/za/co/capitecbank/enums/AlertSeverity.java`
- `src/main/java/za/co/capitecbank/enums/AlertStatus.java`

**Description:**
Create all five enums in the `enums/` package:
- `TransactionType`: `DEPOSIT`, `WITHDRAWAL`, `TRANSFER`, `PAYMENT`, `CARD_PURCHASE`
- `Channel`: `BRANCH`, `ATM`, `ONLINE`, `MOBILE`, `POS`
- `RiskRating`: `LOW`, `MEDIUM`, `HIGH`
- `AlertSeverity`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- `AlertStatus`: `PENDING`, `UNDER_REVIEW`, `CONFIRMED_FRAUD`, `CLEARED`

**Test class + method:** No dedicated test — covered by entity tests (TASK-05 onwards).

---

### TASK-03 — FieldEncryptionService Interface + AES-256-GCM Implementation

**Files to touch:**
- `src/main/java/za/co/capitecbank/security/FieldEncryptionService.java`
- `src/main/java/za/co/capitecbank/security/impl/AesGcmFieldEncryptionService.java`
- `src/main/resources/application-local.yml` (add `app.encryption.key` config)

**Description:**
Create the `FieldEncryptionService` interface with `encrypt(String)` and `decrypt(String)`.
Implement `AesGcmFieldEncryptionService`:
- `@Service`, `@RequiredArgsConstructor`, `@Slf4j`
- Reads key from `@ConfigurationProperties` record `EncryptionProperties` (`app.encryption.key`)
- Uses `AES/GCM/NoPadding`, 256-bit key, 12-byte random IV prepended to ciphertext
- Key sourced from `${APP_ENCRYPTION_KEY:test-aes-256-key-32byteslong!!!!!}` (tech-analysis A3)
- `EncryptionProperties` record in `config/` package annotated `@ConfigurationProperties(prefix = "app.encryption")`

`application-local.yml` addition:
```yaml
app:
  encryption:
    key: ${APP_ENCRYPTION_KEY:test-aes-256-key-32byteslong!!!!!}
```

**Test class + method:**
- `src/test/java/za/co/capitecbank/security/impl/AesGcmFieldEncryptionServiceTest.java`
- `encrypt_shouldProduceCiphertextDifferentFromPlaintext()`
- `decrypt_shouldRecoverOriginalPlaintext()`
- `convertToDatabaseColumn_whenNull_shouldReturnNull()`
- `encryptDecrypt_roundTrip_shouldBeIdempotent()`

---

### TASK-04 — EncryptedStringConverter

**Files to touch:**
- `src/main/java/za/co/capitecbank/persistence/converter/EncryptedStringConverter.java`

**Description:**
Create `EncryptedStringConverter` implementing `AttributeConverter<String, String>`:
- `@Converter(autoApply = false)`, `@Component`, `@RequiredArgsConstructor`
- Delegates to `FieldEncryptionService`
- Null-safe in both directions (tech-analysis C4)

**Test class + method:**
- `src/test/java/za/co/capitecbank/persistence/converter/EncryptedStringConverterTest.java`
- `convertToDatabaseColumn_shouldEncrypt()`
- `convertToEntityAttribute_shouldDecrypt()`
- `convertToDatabaseColumn_whenNull_shouldReturnNull()`
- `convertToEntityAttribute_whenNull_shouldReturnNull()`

---

### TASK-05 — PpiChangeRecord Embeddable

**Files to touch:**
- `src/main/java/za/co/capitecbank/persistence/entity/PpiChangeRecord.java`

**Description:**
Create `PpiChangeRecord` as `@Embeddable`:
- Fields: `fieldChanged` (String), `oldValue` (String, encrypted), `newValue` (String, encrypted), `changeDate` (LocalDateTime)
- `@Convert(converter = EncryptedStringConverter.class)` on `oldValue` and `newValue`
- Lombok: `@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor`
- No `@Data` (Standard §14)

**Test class + method:** Covered by `ClientProfileEntityTest` in TASK-07.

---

### TASK-06 — TransactionEntity

**Files to touch:**
- `src/main/java/za/co/capitecbank/persistence/entity/TransactionEntity.java`

**Description:**
Create `TransactionEntity`:
- Annotation order: `@Builder @Setter @Getter @AllArgsConstructor @NoArgsConstructor @Entity @Table(name = "transaction")`
- `@Id` field `transactionId` of type `UUID` with `@GeneratedValue(strategy = GenerationType.UUID)` (tech-analysis B1)
- All fields per requirements section 3.1
- `@Enumerated(EnumType.STRING)` on `transactionType` and `channel` (Standard §14)
- `@Version private long version`
- `currency` default not set via field initializer — set by service layer on create
- `@Column(updatable = false)` on `transactionId` and `createdDate`

**Test class + method:**
- `src/test/java/za/co/capitecbank/persistence/entity/TransactionEntityTest.java`
- `builder_shouldSetAllFields()`
- `version_shouldBePresent()`

---

### TASK-07 — ClientProfileEntity

**Files to touch:**
- `src/main/java/za/co/capitecbank/persistence/entity/ClientProfileEntity.java`

**Description:**
Create `ClientProfileEntity`:
- Annotation order: `@Builder @Setter @Getter @AllArgsConstructor @NoArgsConstructor @Entity @Table(name = "client_profile")`
- `@Id` field `id` of type `Long` with `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- `@Convert(converter = EncryptedStringConverter.class)` on `fullName` and `idNumber`
- `ppiChangeHistory` as `@ElementCollection(fetch = FetchType.LAZY)` with `@CollectionTable(name = "client_profile_ppi_change", joinColumns = @JoinColumn(name = "client_profile_id"))` (tech-analysis B4)
- `@OrderBy("changeDate ASC")` on `ppiChangeHistory`
- `@Version private long version`
- `@Enumerated(EnumType.STRING)` on `riskRating`

**Test class + method:**
- `src/test/java/za/co/capitecbank/persistence/entity/ClientProfileEntityTest.java`
- `builder_shouldSetAllFields()`
- `ppiChangeHistory_shouldBeEmptyByDefault()`
- `version_shouldBePresent()`

---

### TASK-08 — FraudAlertEntity

**Files to touch:**
- `src/main/java/za/co/capitecbank/persistence/entity/FraudAlertEntity.java`

**Description:**
Create `FraudAlertEntity`:
- Annotation order: `@Builder @Setter @Getter @AllArgsConstructor @NoArgsConstructor @Entity @Table(name = "fraud_alert")`
- `@Id` field `alertId` of type `UUID` with `@GeneratedValue(strategy = GenerationType.UUID)`
- `transactionId` as plain `UUID @Column(nullable = false)` — no FK (tech-analysis B3)
- `@Enumerated(EnumType.STRING)` on `severity` and `status`
- `status` default `AlertStatus.PENDING` set in no-args constructor or `@Builder.Default`
- `details` with `@Column(columnDefinition = "TEXT")`
- `@Version private long version`
- `@Column(updatable = false)` on `alertId` and `createdAt`

**Test class + method:**
- `src/test/java/za/co/capitecbank/persistence/entity/FraudAlertEntityTest.java`
- `builder_shouldSetAllFields()`
- `status_defaultShouldBePending()`
- `version_shouldBePresent()`

---

### TASK-09 — Spring Data Repositories

**Files to touch:**
- `src/main/java/za/co/capitecbank/persistence/repository/TransactionRepository.java`
- `src/main/java/za/co/capitecbank/persistence/repository/ClientProfileRepository.java`
- `src/main/java/za/co/capitecbank/persistence/repository/FraudAlertRepository.java`

**Description:**
Create the three `JpaRepository` interfaces with derived query methods per requirements section 5.
No `@Repository` annotation — Spring Data detects them automatically (tech-analysis C1).

`TransactionRepository`:
- `findByClientIdAndTimestampAfter(String clientId, LocalDateTime after)`
- `countByClientIdAndTimestampAfter(String clientId, LocalDateTime after)`
- `findByClientIdAndTransactionTypeInAndTimestampAfterAndAmountLessThan(...)`
- `findByClientIdAndTimestampBetweenOrderByTimestampAsc(...)`

`ClientProfileRepository`:
- `findByClientId(String clientId)` → `Optional<ClientProfileEntity>`

`FraudAlertRepository`:
- `findByClientId(String clientId)`
- `findByTransactionId(UUID transactionId)`
- `findByStatus(AlertStatus status)`

**Test class + method:** Covered by `FraudPersistenceServiceTest` in TASK-10.

---

### TASK-10 — FraudPersistenceService

**Files to touch:**
- `src/main/java/za/co/capitecbank/persistence/FraudPersistenceService.java`

**Description:**
Create `FraudPersistenceService`:
- `@Service`, `@Slf4j`, `@RequiredArgsConstructor`
- NOT `final` (Standard §18 — CGLIB proxying for `@Transactional`)
- All read methods annotated `@Transactional(readOnly = true)` (Standard §23)
- All write methods annotated `@Transactional` (short, no external HTTP calls — Standard §23)
- Wrap `DataIntegrityViolationException` on `saveAlert` → throw `FraudEvaluationException` (defined in AML-FRAUD-002 but stubbed here as a placeholder until that ticket ships)
- Structured logging: `log.debug("Retrieving transactions [correlationId={}, clientId={}]", traceId, clientId)`
- Never log PII (Standard §9)

**Test class + method:**
- `src/test/java/za/co/capitecbank/persistence/FraudPersistenceServiceTest.java`
- `@ExtendWith(MockitoExtension.class)` — mock all three repositories
- `findTransactionsByClientSince_shouldDelegateToRepository()`
- `countTransactionsByClientSince_shouldDelegateToRepository()`
- `findClientProfile_shouldReturnEmpty_whenNotFound()`
- `saveAlert_shouldPersistAndReturn()`
- `saveAlert_whenDuplicateKey_shouldThrowFraudEvaluationException()`

---

### TASK-11 — Flyway Migration Scripts

**Files to touch:**
- `src/main/resources/db/migration/V1__create_transaction_table.sql`
- `src/main/resources/db/migration/V2__create_client_profile_table.sql`
- `src/main/resources/db/migration/V3__create_fraud_alert_table.sql`

**Description:**
Create all three migration scripts using constructs compatible with both H2 PostgreSQL mode
and real PostgreSQL (tech-analysis A4):
- `CREATE TABLE IF NOT EXISTS`
- `UUID` columns for IDs (no `gen_random_uuid()` defaults — generated in Java)
- `TEXT` for variable-length / ciphertext columns
- `BIGINT GENERATED BY DEFAULT AS IDENTITY` for `client_profile.id`
- `VARCHAR(50)` for enum columns
- `DECIMAL(19,4)` for `BigDecimal` columns
- Indexes on: `transaction(client_id)`, `transaction(timestamp)`, `client_profile(client_id)` (UNIQUE), `fraud_alert(client_id)`, `fraud_alert(transaction_id)`

**Test class + method:** Verified implicitly by application startup with H2 in TASK-12 (`mvn test`).

---

### TASK-12 — Application Config Updates

**Files to touch:**
- `src/main/resources/application-local.yml`

**Description:**
Update `application-local.yml` with all config required by this ticket:
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
    key: ${APP_ENCRYPTION_KEY:test-aes-256-key-32byteslong!!!!!}
```

**Test class + method:** Verified by `mvn test` — Spring context loads and Flyway runs.

---

### TASK-13 — Spring Boot Context / Smoke Test

**Files to touch:**
- `src/test/java/za/co/capitecbank/persistence/FraudPersistenceIntegrationTest.java` *(if running under `mvn verify`)*

**Description:**
Write a lightweight `@SpringBootTest` smoke test (using H2) to confirm:
- Application context loads with all new beans
- Flyway scripts execute without error
- `FraudPersistenceService` can save and retrieve a `TransactionEntity`
- `EncryptedStringConverter` round-trips correctly through the DB

Annotate with `@ActiveProfiles("local")` and `@SpringBootTest`.

Note: Named `FraudPersistenceIntegrationTest` — runs under `mvn verify` (Failsafe), not `mvn test` (per existing pom.xml surefire exclusion pattern).

**Test class + method:**
- `src/test/java/za/co/capitecbank/persistence/FraudPersistenceIntegrationTest.java`
- `contextLoads_andFlywayMigrationsRun()`
- `saveAndFindTransaction_shouldRoundTripCorrectly()`
- `encryptedFields_shouldBeEncryptedInDb_andDecryptedInMemory()`

---

## Task Dependency Order

```
TASK-01 (pom.xml)
  └─► TASK-02 (enums)
        └─► TASK-03 (FieldEncryptionService)
              └─► TASK-04 (EncryptedStringConverter)
                    └─► TASK-05 (PpiChangeRecord)
                          ├─► TASK-06 (TransactionEntity)
                          ├─► TASK-07 (ClientProfileEntity)
                          └─► TASK-08 (FraudAlertEntity)
                                └─► TASK-09 (Repositories)
                                      └─► TASK-10 (FraudPersistenceService)
                                            └─► TASK-11 (Flyway scripts)
                                                  └─► TASK-12 (Config)
                                                        └─► TASK-13 (Integration test)
```

## Estimated Task Count: 13 tasks
## New Source Files: ~20
## New Test Files: ~7 (unit) + 1 (integration)
