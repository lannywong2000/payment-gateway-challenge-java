# Instructions for candidates

This is the Java version of the Payment Gateway challenge. If you haven't already read this [README.md](https://github.com/cko-recruitment/) on the details of this exercise, please do so now.

## Requirements
- JDK 17
- Docker

## Template structure

src/ - A skeleton SpringBoot Application

test/ - Some simple JUnit tests

imposters/ - contains the bank simulator configuration. Don't change this

.editorconfig - don't change this. It ensures a consistent set of rules for submissions when reformatting code

docker-compose.yml - configures the bank simulator


## API Documentation
For documentation openAPI is included, and it can be found under the following url: **http://localhost:8090/swagger-ui/index.html**

**Feel free to change the structure of the solution, use a different library etc.**

---

## Getting Started

```bash
# 1. Start the bank simulator
docker-compose up

# 2. Run the application (port 8090)
./gradlew bootRun
```

Swagger UI: **http://localhost:8090/swagger-ui/index.html**

### Running tests

```bash
# Unit tests only — no Docker required
./gradlew test --tests "com.checkout.payment.gateway.service.PaymentGatewayServiceTest"

# Spring integration tests only — no Docker required
./gradlew test --tests "com.checkout.payment.gateway.controller.PaymentGatewayControllerTest"

# E2E tests only — requires Docker (bank simulator must be running)
./gradlew test --tests "com.checkout.payment.gateway.controller.PaymentGatewayIntegrationTest"

# All tests — requires Docker
./gradlew test
```

### Quick smoke test
```bash
# Authorized (card ending in odd digit)
curl -X POST http://localhost:8090/payments \
  -H "Content-Type: application/json" \
  -d '{"card_number":"2222405343248877","expiry_month":6,"expiry_year":2028,"currency":"GBP","amount":100,"cvv":"123"}'

# Retrieve by ID
curl http://localhost:8090/payment/{id}
```

---

## Design

### Payment flow

```
POST /payments
      │
      ├─ isRequestValid? ──No──► REJECTED (stored, returned — bank never called)
      │
      ├─ Call bank simulator
      │       ├─ authorized: true  ──► AUTHORIZED
      │       ├─ authorized: false ──► DECLINED
      │       └─ 4xx / 5xx         ──► DECLINED
      │
      └─ Store & return response
```

### Validation rules

| Field | Rule |
|---|---|
| `card_number` | 14–19 numeric digits |
| `expiry_month` | 1–12 |
| `expiry_year` + `expiry_month` | Combined `YearMonth` must not be in the past |
| `currency` | One of: `USD`, `GBP`, `EUR` |
| `amount` | Positive integer (minor currency units, e.g. cents) |
| `cvv` | 3–4 numeric digits |

Any violation → `PostPaymentResponse { status: "Rejected" }` — same response shape as Authorized/Declined, stored with a UUID.

### Key decisions

- **Validation in service, not model** — all rules are private methods in `PaymentGatewayService` rather than JSR-380 annotations. Keeps the model a plain data carrier; validation is explicit and unit-testable.
- **Bank errors → Declined** — 4xx/5xx from the bank are treated as a decline. From the merchant's perspective the payment didn't go through regardless of the reason.
- **CVV never stored** — only `cardNumberLastFour` is persisted, consistent with PCI-DSS.
- **In-memory repository** — `HashMap<UUID, PostPaymentResponse>`, no persistence across restarts. Per spec, no real database is required.
- **Supported currencies** — restricted to `USD`, `GBP`, `EUR` stored in an immutable `Set.of(...)`.

### Test strategy

```
┌─────────────────────────────────────────────────────────────┐
│  Unit  PaymentGatewayServiceTest  (12 tests)                │
│  Service in isolation · Mockito · no Docker                 │
├─────────────────────────────────────────────────────────────┤
│  Spring  PaymentGatewayControllerTest  (20 tests)           │
│  Full HTTP stack · MockRestServiceServer · no Docker        │
├─────────────────────────────────────────────────────────────┤
│  E2E  PaymentGatewayIntegrationTest  (6 tests)              │
│  Real Docker bank · no mocks · requires docker-compose up   │
└─────────────────────────────────────────────────────────────┘
```
