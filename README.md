docker run --rm confluentinc/cp-kafka:7.6.1 bash -lc "kafka-storage random-uuid"

# login-processing-service

A Spring Boot service that processes customer login events by:
- consuming events from a Kafka input topic
- calling an external REST endpoint (with Basic Auth) to track the login
- persisting the login tracking result in a database
- publishing the result to a Kafka output topic (via the Outbox pattern)

> Note: As stated in the challenge, the Kafka topics and the REST API target are examples/dummy. The minimum expectation is that the application is correctly targeted to those topic names and endpoint URL, even if no real external system exists.

## Features

- **Kafka consumer**: reads `customer-login` events.
- **REST call**: calls `https://customer-tracking-service/v1/api/trackLoging/{customerId}` with Basic Auth.
- **Retry**: retries REST failures using **Resilience4j Retry**.
- **Persistence**: stores login tracking results in PostgreSQL (via JPA/Hibernate).
- **Outbox pattern**: writes an outbox row in the same transaction as the DB write.
- **Kafka producer**: scheduled outbox publisher sends messages to `login-tracking-result`.
- **Idempotency**: handles duplicate messages based on `messageId` using DB constraints and insert-ignore operations.
- **Tests**: unit + integration tests using WireMock and Testcontainers (Kafka + Postgres).

---

## Architecture & Flow

1. **Consume** a message from Kafka topic `customer-login`.
2. **Process** the incoming event:
    - If `messageId` already exists in DB → return existing result (skip processing).
3. **Call REST** `GET /v1/api/trackLoging/{customerId}` (Basic Auth).
4. **Determine requestResult**: `SUCCESSFUL` or `UNSUCCESSFUL` based on the REST call outcome.
5. **Persist** the result into `login_processing.login_tracking_result`.
6. **Insert Outbox** event row into `login_processing.outbox_event`.
7. **Publish Outbox**:
    - `OutboxPublisher` picks NEW rows and publishes to Kafka topic `login-tracking-result`.
    - Marks each outbox row as `SENT` or eventually `FAILED` (based on configured max retries).

---

## Kafka Topics

- **Input topic**: `customer-login`
- **Output topic**: `login-tracking-result`

Configured in `application.yml` under:

```yaml
app:
  kafka:
    topic:
      input: customer-login
      output: login-tracking-result
```

## Event Schemas
Input: CustomerLoginEvent

DTO: com.codechallenge.loginprocessingservice.dto.CustomerLoginEvent
```
{
  "customerId": "2b93d8d7-9fe0-4a39-9a65-1cd918d04dcb",
  "username": "Samira",
  "client": "web",
  "timestamp": "2026-01-20T12:00:00Z",
  "messageId": "b5e2d49e-81aa-4f17-9b60-f64ea3225c1a",
  "customerIp": "10.0.0.1"
}

```

## REST Integration
Target Endpoint

The service calls the following endpoint of customer-tracking-service:

- **Method:** GET

- **Path:** /v1/api/trackLoging/{customerId}

- **Base URL:** app.customer-tracking.base-url

- **Authentication:** Basic Auth (app.customer-tracking.username / app.customer-tracking.password)

## Database & Migrations
### Database Choice

This project uses PostgreSQL.

###Migrations (Flyway)

Flyway is enabled and runs migrations from:

- **classpath:db/migration**

## Running the application locally
Deploy
```yaml
mvn clean package verify -DskipTests=true
docker compose --file docker-compose.yml --project-name dev up --build -d

```
Ports (localhost)
- App: http://localhost:8080
- PgAdmin: http://localhost:8081 
- Kafka UI: http://localhost:8080

Testing the application:
```yaml
mvn -Dit.test=*IT verify

```

```yaml
mvn -Dtest=*Test test

```
Define cluster:

```yaml
docker run --rm confluentinc/cp-kafka:7.6.1 bash -lc "kafka-storage random-uuid"

```


