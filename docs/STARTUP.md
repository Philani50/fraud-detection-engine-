# Local Startup Guide

Complete steps to build, run, and test the fraud-detection-engine locally using Docker.

---

## Prerequisites

- Java 25 (Corretto 25 or OpenJDK 25)
- Maven 3.9+ or `mvnd`
- Docker Desktop (running)
- PowerShell

---

## Step 1 — Set JAVA_HOME

Open PowerShell and set the correct JDK before every build session:

```powershell
$env:JAVA_HOME = "C:\Users\CP379076\.jdks\corretto-25.0.3"
```

---

## Step 2 — Build the JAR

From the project root:

```powershell
mvnd -s "C:\Users\CP379076\IdeaProjects\fraud-detection-engine\settings-personal.xml" clean package -DskipTests
```

Expected output:
```
[INFO] BUILD SUCCESS
```

The JAR is produced at `target/fraud-detection-engine-1.0.0.jar`.

---

## Step 3 — Start the full stack

```powershell
docker-compose up --build
```

Docker starts three containers in dependency order:

| Container | Port | Ready when |
|---|---|---|
| `fraud-detection-postgres` | `5432` | `pg_isready` passes |
| `fraud-detection-keycloak` | `9090` | realm endpoint responds |
| `fraud-detection-engine` | `8080` | Spring Boot logged `Started Application` |

Wait until you see this line in the logs:
```
fraud-detection-engine | Started Application in X seconds
```

---

## Step 4 — Verify the app is up

```powershell
curl.exe http://localhost:8080/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

```powershell
curl.exe http://localhost:8080/api/v1/health
```

Expected response:
```
fraud-detection-engine is live
```

---

## Step 5 — Get a JWT from Keycloak

```powershell
$body = "grant_type=client_credentials&client_id=fraud-detection-engine&client_secret=fraud-engine-secret"
curl.exe -X POST "http://localhost:9090/realms/fraud-detection-engine/protocol/openid-connect/token" -H "Content-Type: application/x-www-form-urlencoded" -d $body
```

Copy the `access_token` value from the response. It expires in 3600 seconds (1 hour).

Store it in a variable for the next step:

```powershell
$token = "<paste access_token here>"
```

---

## Step 6 — Evaluate a transaction

```powershell
$body = '{"account_id":"ACC-001","client_id":"CLIENT-001","amount":9999.00,"currency":"ZAR","transaction_type":"WITHDRAWAL","channel":"ONLINE","timestamp":"2026-06-03T12:00:00","location":"Cape Town"}'
curl.exe -X POST "http://localhost:8080/v1/fraud/evaluations" -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d $body
```

Expected response:
```json
{
    "transaction_id": "...",
    "client_id": "CLIENT-001",
    "alert_count": 0,
    "alerts": [],
    "evaluated_at": "2026-06-03T12:00:00"
}
```

---

## Test payloads that trigger alerts

Use these `client_id` values from the seeded test data to trigger specific rules.

### RULE-002 — Large Transaction After Profile Change
Client `CLIENT-LARGE` had their profile updated today. Any transfer ≥ R50,000 fires this rule.

```powershell
$body = '{"account_id":"ACC-001","client_id":"CLIENT-LARGE","amount":75000.00,"currency":"ZAR","transaction_type":"TRANSFER","channel":"ONLINE","timestamp":"2026-06-03T12:00:00","destination_account_id":"ACC-TARGET-001"}'
curl.exe -X POST "http://localhost:8080/v1/fraud/evaluations" -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d $body
```

### RULE-003 — Suspicious Spend Behaviour
Client `CLIENT-SPEND` has an average monthly income of R10,000. Any amount > R30,000 fires this rule.

```powershell
$body = '{"account_id":"ACC-001","client_id":"CLIENT-SPEND","amount":35000.00,"currency":"ZAR","transaction_type":"PAYMENT","channel":"ONLINE","timestamp":"2026-06-03T12:00:00"}'
curl.exe -X POST "http://localhost:8080/v1/fraud/evaluations" -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d $body
```

---

## Valid enum values

| Field | Allowed values |
|---|---|
| `transaction_type` | `DEPOSIT`, `WITHDRAWAL`, `TRANSFER`, `PAYMENT` |
| `channel` | `ONLINE`, `ATM`, `BRANCH`, `MOBILE` |

---

## Stop the stack

```powershell
docker-compose down
```

To also delete the PostgreSQL data volume:

```powershell
docker-compose down -v
```

---

## Keycloak admin console

Available at `http://localhost:9090` while the stack is running.

| Field | Value |
|---|---|
| Username | `admin` |
| Password | `admin` |
| Realm | `fraud-detection-engine` |
