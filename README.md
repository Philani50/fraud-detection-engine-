# Fraud Detection Engine

A production-grade Spring Boot microservice that evaluates financial transactions against a configurable set of fraud detection rules in real time. Alerts are persisted, observable via Prometheus metrics, and protected by OAuth2 JWT authentication.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Fraud Rules](#fraud-rules)
- [Code Guide — What Each Class Does](#code-guide--what-each-class-does)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Configuration](#configuration)
- [Security](#security)
- [Database](#database)
- [Observability](#observability)
- [Testing](#testing)

---

## Overview

The Fraud Detection Engine receives a transaction payload via REST, saves the transaction, evaluates it against all enabled fraud rules, persists any generated alerts, and returns the evaluation result to the caller.

Rules are independently toggleable via configuration, independently testable in isolation, and follow the Open/Closed principle — adding a new rule requires only a new class, zero changes to existing code.

---

## Architecture

```
POST /v1/fraud/evaluations
        │
        ▼
FraudController
        │  maps request → TransactionEntity (MapStruct)
        │  saves transaction
        ▼
FraudEvaluationService
        │  builds RuleContext (memoised client profile, DB-backed queries)
        │  iterates List<FraudRule> in @Order sequence
        │  for each enabled + applicable rule → evaluate()
        │  persists alerts via FraudPersistenceService
        │  increments Micrometer counters + records Timer
        ▼
FraudAlertEntity (Flyway-managed PostgreSQL / H2 for local)
```

### Key design decisions

| Decision | Rationale |
|---|---|
| `RuleContext` plain object (not a Spring bean) | Created per-request; holds a memoised `clientProfileSupplier` so the DB is hit at most once per evaluation regardless of how many rules call `getClientProfile()` |
| No raw `List<TransactionEntity>` passed to rules | All time-window queries are delegated to indexed DB methods — prevents O(n) memory traps |
| `@Builder.Default status = PENDING` on `FraudAlertEntity` | Ensures the alert status is never null without requiring a no-args constructor side-effect |
| AES-256-GCM for PII fields | Authenticated encryption prevents ciphertext tampering; random 12-byte IV per encryption call |
| Flyway over `ddl-auto` | Schema migrations are versioned, auditable, and reproducible across all environments |

---

## Fraud Rules

Six rules ship out of the box. All are enabled by default and independently toggleable.

| Rule | Code | Severity | Trigger |
|---|---|---|---|
| Structuring Over Time | `RULE-001` | HIGH | 3+ cash transactions each below R10,000 totalling ≥ R10,000 within 24 hours |
| Large Transaction After Profile Change | `RULE-002` | CRITICAL | Transaction ≥ R50,000 within 48 hours of a PII profile update |
| Suspicious Spend Behaviour | `RULE-003` | MEDIUM | Transaction amount exceeds 300% of client's average monthly income |
| Velocity Spike | `RULE-004` | HIGH | 10+ transactions from the same client within the last 1 hour |
| Round Amount Pattern | `RULE-005` | MEDIUM | 3+ transactions with amounts divisible by R1,000 within 24 hours |
| High-Risk Rapid Movement | `RULE-006` | CRITICAL | Outgoing transfer/withdrawal ≥ 80% of an inbound deposit ≥ R20,000 within 30 minutes |

---

## Code Guide — What Each Class Does

This section explains every class in plain language so you can navigate the codebase without prior knowledge of the framework.

---

### Entry Point

#### `Application.java`
The starting point of the entire service. When you run the application, Java calls the `main` method in this class. It tells Spring Boot to start up and load all the other classes automatically.

---

### Controllers — "The Front Door"

Controllers are the classes that receive HTTP requests from the outside world (e.g. from Postman or another service).

#### `FraudController.java`
Handles the main endpoint `POST /v1/fraud/evaluations`. When a transaction comes in:
1. It validates the request fields
2. Converts it into a database object
3. Saves it
4. Sends it to the fraud evaluation engine
5. Returns the result (which rules fired, what alerts were created)

#### `ServiceController.java`
Handles `GET /api/v1/health`. Returns a simple "service is live" message. Used by monitoring tools to check whether the application is running. No authentication required.

---

### Service Layer — "The Brain"

Services contain the business logic. They sit between the controller and the database.

#### `FraudEvaluationService.java`
An interface (a contract) that says: "whoever implements me must have an `evaluate` method that takes a transaction and returns a list of alerts." This makes the code easier to swap out or test.

#### `FraudEvaluationServiceImpl.java`
The actual implementation of `FraudEvaluationService`. This is the orchestrator — it:
1. Builds a `RuleContext` for the transaction
2. Loops through all 6 fraud rules
3. Skips rules that are disabled or not applicable
4. Calls `evaluate()` on each applicable rule
5. Saves any alerts that are generated
6. Records timing metrics and logs everything

---

### Fraud Rules — "The Detectives"

Each fraud rule is its own class. They all implement the same `FraudRule` interface so the engine can treat them identically.

#### `FraudRule.java`
The interface (blueprint) that every rule must follow. It defines three questions every rule must answer:
- `isEnabled()` — should this rule run at all?
- `isApplicable(transaction)` — is this rule relevant for this type of transaction?
- `evaluate(transaction, context)` — does this transaction look suspicious?

#### `StructuringRule.java` — RULE-001
Detects structuring: a pattern where someone makes multiple small cash deposits to avoid triggering large-transaction alerts. Fires when 3+ deposits each under R10,000 add up to R10,000+ within 24 hours.

#### `ProfileChangeRule.java` — RULE-002
Flags large transactions that happen shortly after a client's personal details were updated. A profile change followed quickly by a big transfer is a common fraud pattern.

#### `SuspiciousSpendRule.java` — RULE-003
Compares the transaction amount to the client's average monthly income. If someone spends more than 3 times their monthly income in one transaction, it's flagged.

#### `VelocityRule.java` — RULE-004
Detects unusually fast transaction activity. If a client makes 10 or more transactions within a single hour, it fires — this can indicate an automated attack or a compromised account.

#### `RoundAmountRule.java` — RULE-005
Looks for a pattern of transactions in suspiciously round numbers (e.g. R5,000, R10,000, R2,000). Legitimate purchases rarely end in exactly round thousands.

#### `RapidMovementRule.java` — RULE-006
Detects "in and out" fund movement: a large deposit quickly followed by a withdrawal of most of the same money. This is a classic money laundering pattern.

---

### Rule Utilities — "The Helpers"

#### `RuleContext.java`
A helper object created fresh for each transaction evaluation. It provides rules with safe, efficient access to database data (recent transactions, client profile) without each rule having to query the database directly. Importantly, the client profile is only fetched once — even if multiple rules ask for it.

#### `RuleContextFactory.java`
Responsible for building a `RuleContext`. It wires up the database connection and the memoised profile lookup so that `RuleContext` can be handed to each rule ready to use.

#### `FraudAlertFactory.java`
A factory that builds a `FraudAlertEntity` (the database record for an alert) in a consistent way. Every rule uses this instead of building alerts manually, ensuring they all look the same.

#### `TimeWindowCalculator.java`
A small helper that calculates time boundaries. For example, "what was the time exactly 24 hours ago?" Rules use this to define their look-back windows without each one writing the same date arithmetic.

---

### Persistence Layer — "The Database Layer"

These classes handle everything related to saving and loading data.

#### `FraudPersistenceService.java`
The single point of contact between the application and the database. All reading and writing of transactions, client profiles, and fraud alerts goes through this class. It handles error logging and wraps database exceptions in meaningful domain errors.

#### `TransactionEntity.java`
Represents a row in the `transaction` database table. Each field maps to a column. This is what gets saved when a new transaction comes in.

#### `ClientProfileEntity.java`
Represents a row in the `client_profile` table. Contains personal details about a client — their name, ID number, average income, risk rating, etc. The `fullName` and `idNumber` fields are automatically encrypted before saving and decrypted when loading.

#### `FraudAlertEntity.java`
Represents a row in the `fraud_alert` table. Stores the result when a rule fires — which rule triggered, what the risk score was, what the details are, and the current status (PENDING, UNDER_REVIEW, etc.).

#### `PpiChangeRecord.java`
A sub-record embedded inside `ClientProfileEntity`. Every time a client's personal details change, a record is added here tracking what changed, when, and what the old value was. The old and new values are also encrypted.

#### `TransactionRepository.java`
Provides database query methods for transactions. For example: "find all transactions for this client in the last hour" or "count how many transactions this client made today."

#### `ClientProfileRepository.java`
Provides database query methods for client profiles. For example: "find the profile for client ID X."

#### `FraudAlertRepository.java`
Provides database access for fraud alerts — used when saving newly generated alerts.

#### `EncryptedStringConverter.java`
An invisible helper that sits between the application and the database. Whenever a sensitive text field (like a name or ID number) is saved, this class automatically encrypts it. When it's loaded back, this class automatically decrypts it. The rule or service using it never has to think about encryption.

---

### Security — "The Lock"

#### `FieldEncryptionService.java`
An interface that defines two operations: `encrypt` and `decrypt`. Separating the interface from the implementation makes it easy to swap encryption algorithms if needed.

#### `AesGcmFieldEncryptionService.java`
The actual encryption implementation. Uses AES-256-GCM, a modern authenticated encryption standard. Each encryption call generates a fresh random IV (initialisation vector) so the same data never produces the same ciphertext twice.

#### `SecurityConfig.java`
Configures which endpoints require authentication and which are public. Sets up the OAuth2 JWT resource server so that incoming requests must include a valid JWT token signed by the configured identity provider. Also enables `@PreAuthorize` annotations on controller methods.

---

### Adapter — "The External Call"

The adapter package handles communication with external services outside this application.

#### `ClientProfileAdapter.java`
An interface that defines one operation: `fetchProfile(clientId)`. Any class that fetches client profiles from an external service must implement this.

#### `ClientProfileAdapterImpl.java`
The real implementation. It calls an external CRM or Core Banking service to get a client's profile. It is decorated with:
- **CircuitBreaker** — if the external service is down, stop calling it after repeated failures
- **TimeLimiter** — if the call takes longer than 3 seconds, cancel it
- **Bulkhead** — limit how many simultaneous calls are in flight at once
- **Cache** — if the same client's profile was fetched recently, return the cached copy instead of calling the external service again

If the external service fails, it falls back gracefully and returns an empty result — the engine continues evaluating with the rules that don't need the profile.

#### `ClientProfileProxyClient.java`
A declarative HTTP client. Instead of writing raw HTTP code, this interface declares the endpoint (`GET /v1/clients/{clientId}/profile`) and Spring automatically generates the implementation.

#### `ClientProfileProxyConfig.java`
Configures the HTTP client — sets the base URL, timeouts, the `X-Channel-Source` header, and an error handler for 401 Unauthorized responses.

#### `ClientProfileProperties.java`
A configuration record that holds the external service URL and timeout settings. Values come from `application.yml` and can be overridden via environment variables.

#### `ClientProfileResponse.java`
A record (immutable data object) that represents the JSON response from the external client profile service. Fields include client ID, full name, ID number, income, and risk rating.

---

### Mappers — "The Translators"

Mappers convert between different object types so the rest of the code stays clean.

#### `FraudMapper.java`
Translates between the API layer and the database layer:
- Converts an incoming `FraudEvaluationRequest` (from the API) into a `TransactionEntity` (for the database)
- Converts a `FraudAlertEntity` (from the database) into a `FraudAlertResponse` (for the API response)
- Builds the full `FraudEvaluationResponse` that gets returned to the caller

#### `ClientProfileMapper.java`
Converts a `ClientProfileResponse` (from the external service) into a `ClientProfileEntity` (the internal representation used by the rules).

---

### Models — "The Data Shapes"

These are simple data containers — they define the shape of data coming in and going out of the API.

#### `FraudEvaluationRequest.java`
Defines what a caller must send when submitting a transaction for evaluation. Fields include account ID, client ID, amount, currency, transaction type, channel, and timestamp. All required fields are validated before the request is processed.

#### `FraudEvaluationResponse.java`
Defines what the caller receives back after evaluation. Includes the transaction ID, client ID, a count of alerts generated, and the full list of alert details.

#### `FraudAlertResponse.java`
The data shape for a single alert in the response. Includes the rule code, rule name, risk score, severity, status, details, and when it was created.

#### `ErrorResponse.java`
The standard shape for error responses. Whenever something goes wrong (validation failure, server error, etc.), the response body follows this structure with a status code, error type, message, timestamp, and correlation ID.

---

### Exception Handling — "The Error Manager"

#### `BusinessException.java`
An abstract base class for all expected business errors. Any error that the application deliberately throws should extend this — it signals "this is a known, handled failure" rather than an unexpected crash.

#### `FraudEvaluationException.java`
Thrown when a fraud rule crashes unexpectedly during evaluation. This is a programming or infrastructure error, not a business validation failure.

#### `FraudDatabaseException.java`
Thrown when a database operation fails. Wraps lower-level database errors into a clean domain exception.

#### `ClientProfileServiceException.java`
Thrown when the external client profile service returns an error (e.g. HTTP 4xx/5xx). Signals that the downstream service call failed.

#### `FieldEncryptionException.java`
Thrown when encryption or decryption fails. Usually indicates a corrupted ciphertext or an incorrect key.

#### `GlobalExceptionHandler.java`
A single class that catches every exception thrown anywhere in the application and converts it into a clean, consistent `ErrorResponse` JSON. Without this, unhandled exceptions would expose internal stack traces to callers.

---

### Configuration — "The Settings"

#### `CacheConfig.java`
Sets up the two in-memory caches used by the application:
- `clientProfiles` — caches client profile lookups for 5 minutes so the external service is not called on every transaction
- `recentTransactionCounts` — caches velocity counts for 2 minutes

#### `EncryptionProperties.java`
A configuration record that holds the encryption key. The key is loaded from the `APP_ENCRYPTION_KEY` environment variable so it is never hardcoded.

#### `RuleEngineConfig.java`
A configuration record with one boolean per rule (`rule001Enabled` through `rule006Enabled`). Values come from `application.yml`. Setting a value to `false` disables that rule without redeploying.

#### `OpenApiConfig.java`
Configures the Swagger UI that auto-generates API documentation. Defines the service title, version, description, and contact details. The documentation is accessible at `/swagger-ui/index.html`.

#### `SecurityConfig.java`
Configures Spring Security. Defines which endpoints are public and which require a valid JWT. Sets up the OAuth2 JWT resource server and enables method-level `@PreAuthorize` checks.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 3.5.14 |
| Build | Maven (BOM-managed dependencies) |
| Database (local) | H2 in-memory (PostgreSQL compatibility mode) |
| Database (prod) | PostgreSQL |
| Schema migrations | Flyway |
| Field encryption | AES-256-GCM (`javax.crypto`) |
| Mapping | MapStruct 1.6.3 |
| Caching | Caffeine |
| Resilience | Resilience4j (CircuitBreaker, TimeLimiter, Bulkhead) |
| Observability | Micrometer + Prometheus + OpenTelemetry (OTLP) |
| Security | Spring Security OAuth2 Resource Server (JWT) |
| API Docs | SpringDoc OpenAPI 2.8.13 |
| Code quality | Spotless (Palantir Java Format) + Checkstyle |

---

## Project Structure

```
src/main/java/za/co/capitecbank/
├── adapter/                        # ClientProfile external service integration
│   ├── ClientProfileAdapter.java
│   ├── ClientProfileProxyClient.java
│   ├── config/                     # @ConfigurationProperties + RestClient factory
│   ├── impl/                       # Resilience4j-decorated implementation
│   └── model/                      # ClientProfileResponse record
├── config/                         # CacheConfig, EncryptionProperties, OpenApiConfig,
│                                   # RuleEngineConfig, SecurityConfig
├── controller/                     # FraudController, ServiceController
├── enums/                          # AlertSeverity, AlertStatus, Channel,
│                                   # RiskRating, TransactionType
├── exception/                      # BusinessException hierarchy + GlobalExceptionHandler
├── fraudrule/
│   ├── FraudRule.java              # Strategy interface
│   ├── context/                    # RuleContext + RuleContextFactory
│   ├── impl/                       # Six rule implementations
│   └── util/                       # FraudAlertFactory, TimeWindowCalculator
├── mapper/                         # FraudMapper (MapStruct), ClientProfileMapper
├── model/
│   ├── request/                    # FraudEvaluationRequest record
│   └── response/                   # FraudEvaluationResponse, FraudAlertResponse,
│                                   # ErrorResponse records
├── persistence/
│   ├── FraudPersistenceService.java
│   ├── converter/                  # EncryptedStringConverter (AES-256-GCM)
│   ├── entity/                     # TransactionEntity, ClientProfileEntity,
│   │                               # FraudAlertEntity, PpiChangeRecord
│   └── repository/                 # Spring Data JPA repositories
├── security/
│   ├── FieldEncryptionService.java
│   └── impl/AesGcmFieldEncryptionService.java
└── service/
    ├── FraudEvaluationService.java
    └── impl/FraudEvaluationServiceImpl.java

src/main/resources/
├── application.yml
└── db/migration/
    ├── V1__create_transaction_table.sql
    ├── V2__create_client_profile_table.sql
    ├── V3__create_fraud_alert_table.sql
    └── V4__seed_test_client_profiles.sql
```

---

## Getting Started

### Prerequisites

- Java 25 (Amazon Corretto 25 recommended)
- Maven 3.9+ or `mvnd`
- Docker Desktop
- PowerShell (Windows) or Terminal (Mac/Linux)

---

### Step 1 — Clone the repository

```powershell
git clone https://github.com/CP379076/fraud-detection-engine.git
cd fraud-detection-engine
```

---

### Step 2 — Install Java 25

Download and install **Amazon Corretto 25** from:
```
https://aws.amazon.com/corretto
```

After installing, set `JAVA_HOME` in PowerShell before every build session:

```powershell
$env:JAVA_HOME = "C:\Users\<your-username>\.jdks\corretto-25.0.3"
```

Verify:
```powershell
java -version
```

---

### Step 3 — Import the Zscaler SSL certificate (corporate network only)

> Skip this step if you are on a home/personal network.

**Export the certificate:**
```powershell
$tcpClient = New-Object System.Net.Sockets.TcpClient("repo.maven.apache.org", 443)
$sslStream = New-Object System.Net.Security.SslStream($tcpClient.GetStream(), $false, {$true})
$sslStream.AuthenticateAsClient("repo.maven.apache.org")
$chain = New-Object System.Security.Cryptography.X509Certificates.X509Chain
$chain.Build($sslStream.RemoteCertificate)
$root = $chain.ChainElements[$chain.ChainElements.Count - 1].Certificate
[System.IO.File]::WriteAllBytes("C:\Users\$env:USERNAME\corporate-root.cer", $root.Export('Cert'))
Write-Host "Exported: $($root.Subject)"
```

**Import it into the JVM truststore (run PowerShell as Administrator):**
```powershell
$keytool = "C:\Users\$env:USERNAME\.jdks\corretto-25.0.3\bin\keytool.exe"
$keystore = "C:\Users\$env:USERNAME\.jdks\corretto-25.0.3\lib\security\cacerts"
$certfile = "C:\Users\$env:USERNAME\corporate-root.cer"
& $keytool -import -trustcacerts -alias zscaler-root -keystore $keystore -storepass changeit -file $certfile -noprompt
```

Expected output:
```
Certificate was added to keystore
```

---

### Step 4 — Build the JAR

```powershell
mvnd -s settings-personal.xml clean package -DskipTests
```

Or with standard Maven:
```powershell
mvn clean package -DskipTests
```

Expected output:
```
[INFO] BUILD SUCCESS
```

---

### Step 5 — Start the full stack

```powershell
docker-compose up --build
```

Docker starts three containers in order:

| Container | Port | Purpose |
|---|---|---|
| `fraud-detection-postgres` | `5432` | PostgreSQL database |
| `fraud-detection-keycloak` | `9090` | OAuth2 identity provider |
| `fraud-detection-engine` | `8080` | The application |

Wait until you see:
```
fraud-detection-engine | Started Application in X seconds
```

---

### Step 6 — Verify the stack is up

```powershell
curl.exe http://localhost:8080/actuator/health
```
Expected: `{"status":"UP"}`

```powershell
curl.exe http://localhost:8080/api/v1/health
```
Expected: `fraud-detection-engine is live`

---

### Step 7 — Get a JWT token

```powershell
$body = "grant_type=client_credentials&client_id=fraud-detection-engine&client_secret=fraud-engine-secret"
curl.exe -X POST "http://localhost:9090/realms/fraud-detection-engine/protocol/openid-connect/token" -H "Content-Type: application/x-www-form-urlencoded" -d $body
```

Copy the `access_token` from the response and store it:
```powershell
$token = "<paste_access_token_here>"
```

The token expires after 1 hour. Re-run the command above to get a new one.

---

### Step 8 — Test the fraud evaluation endpoint

Write a request body to a file (avoids PowerShell quoting issues):

```powershell
'{"account_id":"ACC-001","client_id":"CLIENT-001","amount":500.00,"currency":"ZAR","transaction_type":"DEPOSIT","channel":"ONLINE","timestamp":"2026-06-03T12:00:00"}' | Out-File -FilePath body.json -Encoding utf8NoBOM
curl.exe -X POST "http://localhost:8080/v1/fraud/evaluations" -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@body.json"
```

---

### Stop the stack

```powershell
docker-compose down
```

To also wipe the database volume:
```powershell
docker-compose down -v
```

---

## API Reference

Base URL: `http://localhost:8080`

### Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/actuator/health` | None | Spring health check |
| `GET` | `/api/v1/health` | None | Service liveness check |
| `POST` | `/v1/fraud/evaluations` | Bearer JWT | Evaluate a transaction |

---

### POST /v1/fraud/evaluations

**Request fields:**

| Field | Type | Required | Notes |
|---|---|---|---|
| `account_id` | String | Yes | |
| `client_id` | String | Yes | |
| `amount` | Decimal | Yes | Min 0.01 |
| `currency` | String | Yes | 3 characters e.g. `ZAR` |
| `transaction_type` | Enum | Yes | `DEPOSIT` `WITHDRAWAL` `TRANSFER` `PAYMENT` |
| `channel` | Enum | Yes | `ONLINE` `ATM` `BRANCH` `MOBILE` |
| `timestamp` | DateTime | Yes | ISO format e.g. `2026-06-03T12:00:00` |
| `merchant_category` | String | No | |
| `destination_account_id` | String | No | |
| `location` | String | No | |

---

### Test Payloads

All payloads use the file-based approach for PowerShell compatibility.

---

#### No alerts — normal transaction

```powershell
'{"account_id":"ACC-001","client_id":"CLIENT-001","amount":500.00,"currency":"ZAR","transaction_type":"DEPOSIT","channel":"ONLINE","timestamp":"2026-06-03T12:00:00","location":"Cape Town"}' | Out-File -FilePath body.json -Encoding utf8NoBOM
curl.exe -X POST "http://localhost:8080/v1/fraud/evaluations" -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@body.json"
```

Expected: `alert_count: 0`

---

#### RULE-002 — Large Transaction After Profile Change

Client `CLIENT-LARGE` had their profile updated today. Any transfer ≥ R50,000 fires this rule.

```powershell
'{"account_id":"ACC-001","client_id":"CLIENT-LARGE","amount":75000.00,"currency":"ZAR","transaction_type":"TRANSFER","channel":"ONLINE","destination_account_id":"ACC-TARGET-001","timestamp":"2026-06-03T12:00:00","location":"Johannesburg"}' | Out-File -FilePath body.json -Encoding utf8NoBOM
curl.exe -X POST "http://localhost:8080/v1/fraud/evaluations" -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@body.json"
```

Expected: `alert_count: 1`, `rule_code: RULE-002`, `severity: CRITICAL`

---

#### RULE-003 — Suspicious Spend Behaviour

Client `CLIENT-SPEND` has an average monthly income of R10,000. Any amount > R30,000 fires this rule (300% of income).

```powershell
'{"account_id":"ACC-001","client_id":"CLIENT-SPEND","amount":35000.00,"currency":"ZAR","transaction_type":"PAYMENT","channel":"MOBILE","timestamp":"2026-06-03T12:00:00","location":"Durban"}' | Out-File -FilePath body.json -Encoding utf8NoBOM
curl.exe -X POST "http://localhost:8080/v1/fraud/evaluations" -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@body.json"
```

Expected: `alert_count: 1`, `rule_code: RULE-003`, `severity: MEDIUM`

---

#### RULE-005 — Round Amount Pattern

Send the same round-amount transaction 3 times in quick succession to trigger the pattern.

```powershell
'{"account_id":"ACC-001","client_id":"CLIENT-001","amount":5000.00,"currency":"ZAR","transaction_type":"WITHDRAWAL","channel":"ATM","timestamp":"2026-06-03T12:00:00"}' | Out-File -FilePath body.json -Encoding utf8NoBOM
curl.exe -X POST "http://localhost:8080/v1/fraud/evaluations" -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@body.json"
curl.exe -X POST "http://localhost:8080/v1/fraud/evaluations" -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@body.json"
curl.exe -X POST "http://localhost:8080/v1/fraud/evaluations" -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@body.json"
```

Expected: third request returns `alert_count: 1`, `rule_code: RULE-005`, `severity: MEDIUM`

---

#### RULE-004 — Velocity Spike

Send 10+ transactions from the same client within 1 hour.

```powershell
'{"account_id":"ACC-001","client_id":"CLIENT-001","amount":100.00,"currency":"ZAR","transaction_type":"PAYMENT","channel":"ONLINE","timestamp":"2026-06-03T12:00:00"}' | Out-File -FilePath body.json -Encoding utf8NoBOM
for ($i = 1; $i -le 11; $i++) {
    curl.exe -X POST "http://localhost:8080/v1/fraud/evaluations" -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@body.json"
}
```

Expected: after the 10th request, `alert_count: 1`, `rule_code: RULE-004`, `severity: HIGH`

---

#### 400 Bad Request — validation failure

```powershell
'{"account_id":"","client_id":"CLIENT-001","amount":-1,"currency":"ZA","transaction_type":"PAYMENT","channel":"ONLINE","timestamp":"2026-06-03T12:00:00"}' | Out-File -FilePath body.json -Encoding utf8NoBOM
curl.exe -X POST "http://localhost:8080/v1/fraud/evaluations" -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@body.json"
```

Expected: `400 Bad Request` with field validation errors.

---

#### 401 Unauthorized — missing token

```powershell
'{"account_id":"ACC-001","client_id":"CLIENT-001","amount":500.00,"currency":"ZAR","transaction_type":"DEPOSIT","channel":"ONLINE","timestamp":"2026-06-03T12:00:00"}' | Out-File -FilePath body.json -Encoding utf8NoBOM
curl.exe -X POST "http://localhost:8080/v1/fraud/evaluations" -H "Content-Type: application/json" -d "@body.json"
```

Expected: `401 Unauthorized`

---

**Error response reference:**

| Status | Cause |
|---|---|
| `400 Bad Request` | Validation failure — field errors listed in `message` |
| `401 Unauthorized` | Missing or invalid JWT |
| `403 Forbidden` | Valid JWT but missing `fraud:evaluate` scope |
| `500 Internal Server Error` | Rule evaluation failure |

### Health check (no auth required)

```
GET /api/v1/health
```

### Swagger UI (no auth required)

```
GET /swagger-ui/index.html
```

---

## Configuration

All configuration is in `src/main/resources/application.yml`. Sensitive values are externalised via environment variables.

| Environment Variable | Default (local) | Description |
|---|---|---|
| `APP_ENCRYPTION_KEY` | `test-aes-256-key-32byteslong!!!!!` | 32-byte AES key for PII field encryption. **Must be overridden in production.** |
| `JWT_ISSUER_URI` | `http://localhost:9090/realms/fraud-detection-engine` | JWKS endpoint for JWT signature validation |
| `JWT_AUDIENCE` | `fraud-detection-engine` | Expected `aud` claim in incoming JWTs |
| `H2_CONSOLE_ENABLED` | `false` | Enable H2 web console at `/h2-console` (local dev only) |
| `POSTGRES_PASSWORD` | `frauddetectionengine_local` | PostgreSQL password (Docker only) |
| `KEYCLOAK_ADMIN_PASSWORD` | `admin` | Keycloak admin console password (Docker only) |

### Toggling fraud rules

Each rule can be disabled independently without redeployment:

```yaml
app:
  fraud-rules:
    rule001-enabled: true   # Structuring Over Time
    rule002-enabled: true   # Large Transaction After Profile Change
    rule003-enabled: true   # Suspicious Spend Behaviour
    rule004-enabled: false  # Velocity Spike — disabled
    rule005-enabled: true   # Round Amount Pattern
    rule006-enabled: true   # High-Risk Rapid Movement
```

### ClientProfile external service

```yaml
app:
  http-configs:
    rest:
      client-profile-service:
        url: ${CLIENT_PROFILE_SERVICE_URL:http://localhost:9091}
        connect-timeout: PT2S
        read-timeout: PT3S
```

---

## Security

### Authentication

All endpoints except `/actuator/health`, `/actuator/info`, `/swagger-ui/**`, and `/v3/api-docs/**` require a valid JWT.

The JWT must:
- Be signed by the configured issuer (`JWT_ISSUER_URI`)
- Contain the `fraud-detection-engine` audience claim
- Contain the `fraud:evaluate` scope for `POST /v1/fraud/evaluations`

When running via Docker, Keycloak is started automatically at `http://localhost:9090`. The realm `fraud-detection-engine` is imported on startup from the `keycloak/` directory.

### PII Field Encryption

`ClientProfileEntity.fullName` and `ClientProfileEntity.idNumber` are encrypted at rest using **AES-256-GCM**:

- Random 12-byte IV generated per encryption call using `SecureRandom`
- IV prepended to ciphertext, stored as Base64
- GCM authentication tag (128-bit) prevents ciphertext tampering
- Key sourced from `APP_ENCRYPTION_KEY` environment variable

### Secrets

No secrets are committed to version control. See `.env.example` for required variables.

---

## Database

### Schema

Managed by Flyway. Migrations run automatically on application startup.

| Migration | Description |
|---|---|
| `V1` | `transaction` table — stores all evaluated transactions |
| `V2` | `client_profile` table + `client_profile_ppi_change` — PII-encrypted client data |
| `V3` | `fraud_alert` table — persisted alerts with rule metadata |
| `V4` | Seed data — test client profiles for local development |

### Local H2 console

When `H2_CONSOLE_ENABLED=true`:

```
URL:      http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:frauddetectionengine
Username: sa
Password: (leave blank)
```

### Test client profiles (seeded by V4)

| `client_id` | Setup | Triggers |
|---|---|---|
| `CLIENT-LARGE` | Profile updated today, income R80,000 | RULE-002 on amounts ≥ R50,000 |
| `CLIENT-SPEND` | Average monthly income R10,000 | RULE-003 on amounts > R30,000 |
| `CLIENT-RAPID` | No special profile | Use for RULE-006 rapid movement tests |

---

## Observability

### Metrics

Exposed at `GET /actuator/prometheus` for Prometheus scraping.

| Metric | Type | Tags | Description |
|---|---|---|---|
| `fraud.alerts.generated` | Counter | `ruleCode` | Increments once per alert persisted |
| `fraud.evaluation.duration` | Timer | `clientId` | Full evaluation loop duration |
| `http.server.requests` | Timer | `uri`, `status` | Standard Spring HTTP metrics |

### Tracing

W3C `traceparent` propagation via Micrometer + OpenTelemetry. Configure OTLP endpoint via:

```yaml
management:
  otlp:
    tracing:
      endpoint: ${OTLP_ENDPOINT:http://localhost:4318/v1/traces}
```

### Health

```
GET /actuator/health   → {"status": "UP"}
GET /actuator/info     → build info
GET /actuator/metrics  → available metric names
```

---

## Testing

```bash
# Run all unit tests (72 tests)
mvn test

# Run integration tests (requires app context + H2)
mvn verify
```

### Test coverage

| Layer | Test class | Tests |
|---|---|---|
| Encryption | `AesGcmFieldEncryptionServiceUnitTest` | 4 |
| Field converter | `EncryptedStringConverterUnitTest` | 4 |
| Entities | `TransactionEntityUnitTest`, `ClientProfileEntityUnitTest`, `FraudAlertEntityUnitTest` | 8 |
| Persistence | `FraudPersistenceServiceUnitTest` | 6 |
| Rule context | `RuleContextUnitTest`, `RuleContextFactoryUnitTest` | 5 |
| Fraud rules | `StructuringRule`, `ProfileChangeRule`, `SuspiciousSpendRule`, `VelocityRule`, `RoundAmountRule`, `RapidMovementRule` | 31 |
| Engine | `FraudEvaluationServiceImplUnitTest` | 5 |
| Controller | `FraudControllerUnitTest` | 1 |
| Exception handler | `GlobalExceptionHandlerUnitTest` | 4 |
| Adapter | `ClientProfileAdapterImplUnitTest` | 2 |
| Cache | `CacheConfigUnitTest` | 2 |
| Integration | `FraudPersistenceIntegrationTest` | 3 |
