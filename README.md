# Wallet & Payments Ledger Service

A Spring Boot backend for digital wallets: top up a wallet, transfer money between wallets, and read an immutable ledger of every movement. It is built around the guarantees a money-handling service needs: a balance can never go negative, a retried request is never applied twice, and every balance can be proven from the ledger.

## Features
- Create wallets (one currency each, default `INR`)
- **Top-ups** and **transfers**, each atomic in a single database transaction
- **Idempotency keys**: a retried request (same `Idempotency-Key`) returns the original result instead of moving money again, even when duplicates arrive at the same instant
- **No overdrafts, no lost updates**: wallet rows are locked in a fixed order during a transfer, so parallel transfers cannot overdraw a wallet or deadlock each other
- **Immutable, append-only ledger**: every top-up or transfer writes ledger entries (with the balance after each) that are never updated or deleted
- Paged transaction history per wallet, transaction lookup, and a **reconciliation** endpoint that re-derives a wallet's balance from its ledger
- Audit log line for every completed operation (separate `audit` logger)
- Validation and RFC 7807 error responses; health endpoint
- Runs with zero setup (in-memory H2) or on MySQL with Docker Compose

## Tech stack
Java 21, Spring Boot 4, Spring Web MVC, Spring Data JPA / Hibernate, MySQL 8.4 (H2 for quick runs and tests), Spring Boot Actuator, JUnit 5, MockMvc, Maven, Docker.

## Architecture
```
Client -> Controllers -> WalletService -> WalletOperations -> Repositories -> MySQL
                         (validation,      (one DB transaction:
                          idempotency)      lock, move money, write ledger)
```
- `web`: REST controllers and the global exception handler
- `service/WalletService`: validates input and handles idempotent retries. It is deliberately not transactional for money movement.
- `service/WalletOperations`: the money-moving core; every method is one database transaction
- `domain`: `Wallet`, `WalletTransaction`, `LedgerEntry` (the last two are `@Immutable`)
- `repository`: Spring Data JPA, including the ordered `SELECT ... FOR UPDATE` query
- `dto`: request and response records (entities are never exposed directly)

## How the guarantees work
**Atomicity.** A transfer debits one wallet, credits another, writes the transaction row and two ledger entries inside one transaction. Any failure rolls all of it back.

**No overdraft under concurrency.** A transfer first locks both wallet rows with `SELECT ... FOR UPDATE`, always in ascending id order. Two transfers touching the same wallet run one after the other, and a transfer A->B and another B->A lock in the same order, so they cannot deadlock. The balance check then runs on the locked, current balance. A `@Version` column is a second safety net.

**Idempotency.** Top-ups and transfers require an `Idempotency-Key` header. The key is stored with a fingerprint (SHA-256) of the request in a column with a **unique constraint**.
- Same key, same request: the original result is returned (`200` with `Idempotent-Replayed: true`), nothing is applied again.
- Same key, different request: `409 Conflict`.
- Two identical requests at the same instant: both try to insert the same key, the database lets exactly one win, the loser's transaction rolls back (undoing its balance changes), and it then returns the winner's result. The unique constraint, not application code, is the arbiter.
- A request that fails (for example insufficient funds) does not consume its key.

**Immutable ledger.** `LedgerEntry` and `WalletTransaction` are never updated. A wallet's balance always equals credits minus debits in its ledger, and `GET /api/wallets/{id}/reconciliation` checks exactly that.

## Run it

### Quick start (no database needed)
Requires Java 21+.
```bash
./mvnw spring-boot:run
```
On Windows use `.\mvnw.cmd spring-boot:run`. This uses an in-memory H2 database. The app is at http://localhost:8080.

### Docker Compose (MySQL)
1. Create your environment file and set your own values:
   ```bash
   cp .env.example .env
   ```
2. Start the stack:
   ```bash
   docker compose up --build
   ```
MySQL is exposed on `127.0.0.1:3309` so you can inspect the tables with MySQL Workbench.

To run the app yourself against your own MySQL, use the `mysql` profile and set `DB_URL`, `DB_USER` and `DB_PASSWORD`.

## API
Amounts are decimals with at most 2 decimal places and must be at least 0.01.

### Create a wallet
```bash
curl -i -X POST http://localhost:8080/api/wallets \
  -H "Content-Type: application/json" \
  -d '{"ownerName":"Asha","currency":"INR"}'
```
### Top up
```bash
curl -i -X POST http://localhost:8080/api/wallets/1/topups \
  -H "Idempotency-Key: topup-2026-0001" -H "Content-Type: application/json" \
  -d '{"amount":500.00,"note":"first deposit"}'
```
```json
{
  "id": 1, "type": "TOPUP", "fromWalletId": null, "toWalletId": 1,
  "amount": 500.00, "currency": "INR", "note": "first deposit",
  "createdAt": "2026-03-10T08:00:00",
  "entries": [
    { "id": 1, "transactionId": 1, "walletId": 1, "type": "CREDIT",
      "amount": 500.00, "balanceAfter": 500.00, "createdAt": "2026-03-10T08:00:00" }
  ]
}
```
Sending the same request again with the same key returns this same body with `200` and `Idempotent-Replayed: true`, and the balance stays 500.00.

### Transfer
```bash
curl -i -X POST http://localhost:8080/api/transfers \
  -H "Idempotency-Key: transfer-2026-0001" -H "Content-Type: application/json" \
  -d '{"fromWalletId":1,"toWalletId":2,"amount":120.25}'
```
The response contains two ledger entries: a `DEBIT` on the sender and a `CREDIT` on the receiver, each with its `balanceAfter`.

### Read data
```bash
curl http://localhost:8080/api/wallets/1                      # balance
curl "http://localhost:8080/api/wallets/1/ledger?page=0&size=20"   # history, newest first
curl http://localhost:8080/api/wallets/1/reconciliation       # balance vs ledger
curl http://localhost:8080/api/transactions/1                 # one transaction with its entries
```

### Status codes
| Code | When |
|---|---|
| `201` | Wallet, top-up or transfer created |
| `200` | Replay of an earlier identical request |
| `400` | Invalid amount, currency or key, missing `Idempotency-Key`, transfer to the same wallet |
| `404` | Unknown wallet or transaction |
| `409` | `Idempotency-Key` reused for a different request, or a concurrent-modification conflict |
| `422` | Insufficient funds, or wallets with different currencies |

Health check: `GET /actuator/health`.

## Tests
```bash
./mvnw test
```
24 tests:
- **Unit:** wallet balance rules (no overdraft, exact decimal math) and request fingerprinting
- **API (MockMvc, H2):** wallet creation and validation, top-up, replay, key reuse, transfers, insufficient funds leaving everything unchanged, currency mismatch, unknown wallets, bad amounts and keys, paging, reconciliation, health
- **Concurrency (real threads, real transactions):** ten identical requests at once are applied exactly once; ten parallel transfers of 30.00 from a 100.00 wallet succeed exactly three times; 120 opposing transfers between two wallets finish without deadlock, conserve the total and reconcile with the ledger

## Known limitations
- No authentication or authorization: anyone who can reach the API can move money. A real deployment needs both.
- Top-ups are a simulated credit; there is no payment gateway.
- No refunds or reversals (a correcting transfer is the way to undo one), and no currency conversion.
- The schema is created with `ddl-auto=update`; a production deployment should use a migration tool such as Flyway.
- Ledger entries reference wallets and transactions by id without database foreign keys.
- Timestamps are stored in UTC without a zone.
