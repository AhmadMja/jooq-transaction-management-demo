# jooq-transaction-management-demo

How nested transactions behave with jOOQ + Spring Boot, and what they turn into on PostgreSQL.

Stack: Spring Boot 4.1, jOOQ 3.21, Kotlin, PostgreSQL 18 (Testcontainers).

---

## 1. The scenario

One business operation that has to be atomic:

```
AccountService.changeStatus(id, status)      // outer transaction
 ├─ UPDATE account SET status = ...
 └─ OutboxService.publish(payload)           // "inner" transaction
     └─ INSERT INTO outbox ...
```

The goal is to change the account status and write the outbox event together, or not at all.
`publish()` also opens its own transaction, because it can be called on its own.

**The question:** what happens when a transaction opens inside another one?

---

## 2. Background

### 2.1 What PostgreSQL offers

PostgreSQL has **no real nested transactions**. Inside a transaction it only gives you savepoints:

| SQL                            | Meaning                                         |
|--------------------------------|-------------------------------------------------|
| `BEGIN` / `COMMIT` / `ROLLBACK` | the one real (top-level) transaction            |
| `SAVEPOINT sp`                 | marks a point inside the current transaction    |
| `RELEASE SAVEPOINT sp`         | "inner commit": keeps the work, still uncommitted |
| `ROLLBACK TO SAVEPOINT sp`     | "inner rollback": undoes only the work after `sp` |

- Once a statement errors, PG marks the whole transaction as aborted (`current transaction is aborted...`). Only `ROLLBACK TO SAVEPOINT` lets it continue.
- Any "nested transaction" in Java ends up as one of: a savepoint, joining the outer transaction, or a second real transaction.

### 2.2 How jOOQ starts transactions

`dsl.transaction { }` / `dsl.transactionResult { }` hand the work to a **`TransactionProvider`**:

| Provider                                   | Used when                     | Nested call becomes |
|--------------------------------------------|-------------------------------|---------------------|
| `SpringTransactionProvider` (Spring Boot)  | auto-configured `DSLContext`  | Spring `PROPAGATION_NESTED`, i.e. a **savepoint** |
| `DefaultTransactionProvider` (plain jOOQ)  | you build it yourself         | a savepoint, **but only if it knows about the outer transaction** |

Spring Boot also wraps the `DataSource` in a `TransactionAwareDataSourceProxy`, so jOOQ queries reuse the connection that Spring has bound to the current transaction.

### 2.3 How to read the logs

- `JdbcTransactionManager: DEBUG` shows `Creating new transaction`, `Creating nested transaction` (savepoint), `Participating in existing transaction`, `Releasing transaction savepoint`, `Rolling back transaction to savepoint`, `Committing`.
- `DslUtils.logTransactionInfo()` asks Postgres directly:
  - `txId`: `pg_current_xact_id()`. Savepoints share the parent's id. **A new id means a new real transaction.**
  - `pid`: `pg_backend_pid()`, i.e. which physical connection.
  - `subxactCount`: subtransactions (savepoints) that wrote data. A value above 0 means "we are inside a savepoint".

---

## 3. The cases

Each case is a package under `src/main/kotlin/.../vN_*` with a matching test under `src/test/kotlin/.../vN_*/Test.kt`.

### v1: Base case: `dsl.transaction` inside `dsl.transaction`

- Code: `v1_base`
- Outer `dsl.transaction`, inner `dsl.transactionResult`, both on the Spring-managed `DSLContext`.
- Logs:
  ```
  Creating new transaction ... PROPAGATION_NESTED      -> BEGIN
  changeStatus: txId=758, pid=72, subxactCount=0
  Creating nested transaction                          -> SAVEPOINT
  publish:      txId=758, pid=72, subxactCount=1       (same tx, same connection, inside savepoint)
  Releasing transaction savepoint                      -> RELEASE SAVEPOINT
  Committing JDBC transaction                          -> COMMIT
  ```
- Result: account `ACTIVE`, 1 outbox row.
- Takeaway: with Spring Boot's default setup, a nested jOOQ transaction **is a savepoint**.

### v2: Inner transaction fails and the exception propagates

- Code: `v2_outbox_fails`
- `publish()` inserts, then runs `SELECT 1/0`.
- Logs:
  ```
  BEGIN -> SAVEPOINT -> (error) -> Rolling back transaction to savepoint
        -> exception reaches the outer block -> Rolling back JDBC transaction
  ```
- Result: `DataIntegrityViolationException` (Spring's translation of PG `22012 division_by_zero`). Account still `NEW`, 0 outbox rows.
- Takeaway: the inner block rolls back to its savepoint, the exception keeps going up, and the outer block rolls everything back. **Atomicity is kept.**

### v3: Inner transaction fails and the exception is caught

- Code: `v3_db_exception_caught`
- Same as v2, but `publish()` wraps `transactionResult` in `try/catch` and swallows the error.
- Logs:
  ```
  BEGIN -> SAVEPOINT -> (error) -> Rolling back transaction to savepoint
        -> "Handled exception..." -> Committing JDBC transaction
  ```
- Result: account `ACTIVE`, **0 outbox rows**.
- Takeaway: the savepoint does its job. Only the inner work is undone, and the outer transaction is healthy again and can commit.
  For the business, though, the result is **inconsistent**: the status changed but no event was published.
  - Partial rollback is a feature of savepoints. Swallowing the exception is what breaks the business rule.
  - Callers can't tell that the inner block failed.

### v4: Inner method uses `@Transactional` instead of `dsl.transaction`

- Code: `v4_base_with_annotation`
- `publish()` is `@Transactional` with the default propagation `REQUIRED`. It has no jOOQ transaction block.
- Logs:
  ```
  Creating new transaction ... PROPAGATION_NESTED      -> BEGIN
  changeStatus: txId=770, subxactCount=0
  Participating in existing transaction                -> no SQL at all
  publish:      txId=770, subxactCount=0               (same tx, NO savepoint)
  Committing JDBC transaction                          -> COMMIT
  ```
- Result: account `ACTIVE`, 1 outbox row.
- Takeaway: both styles run through the same Spring `JdbcTransactionManager`, so they mix fine.
  The propagation is what changes:
  - `dsl.transaction` (via `SpringTransactionProvider`) uses `NESTED`, so you get a savepoint.
  - `@Transactional` uses `REQUIRED`, so the call **joins** the outer transaction and gets no savepoint.
  - So the same "nested" code can produce different SQL. Failure handling also differs: v3 relies on the savepoint for its partial rollback. With `REQUIRED` there is none: a failure marks the whole transaction rollback-only (standard Spring behaviour; this repo does not demo it).

### v5: Inner block uses jOOQ's own `DefaultTransactionProvider`

- Code: `v5_base_with_jooq_default_tx_provider`
- `OutboxService` derives a `DSLContext` with `DefaultTransactionProvider(connectionProvider)`, which is jOOQ's native provider, nested by default.
- Logs:
  ```
  Creating new transaction ... PROPAGATION_NESTED      -> BEGIN (Spring)
  changeStatus: txId=773, subxactCount=0
  publish: transactionProvider=org.jooq.impl.DefaultTransactionProvider
  publish:      txId=773, subxactCount=0               (same connection, NO savepoint)
  Committing JDBC transaction                          -> COMMIT (Spring)
  ```
- The test passes (account `ACTIVE`, 1 outbox row), but that is **misleading**:
  - `DefaultTransactionProvider` keeps its savepoint stack in its *own* `Configuration`. It doesn't know Spring already opened a transaction, so it treats itself as **top-level**. It creates no savepoint and, at the end of the block, calls `connection.commit()` (see `DefaultTransactionProvider.begin/commit`).
  - Thanks to `TransactionAwareDataSourceProxy`, that is the **same connection Spring is using**. jOOQ therefore commits the outer transaction's work halfway through.
  - Checked with a throwaway test where the outer block throws *after* `publish()` returns:
    ```
    xid before publish=758, after publish=759   <- a new real transaction started
    outer block threw -> Spring rolled back
    final state: account=ACTIVE, outbox=1       <- the rollback undid nothing
    ```
- Takeaway: **don't mix transaction providers.** The inner block silently commits the outer transaction early, and an outer rollback can no longer undo it.

### v6: `@Transactional` + checked exception: the failure gets committed

- Code: `v6_checked_exception_with_annotation`
- `changeStatus()` and `publish()` are both `@Transactional`. `publish()` inserts, then throws `IOException`. Kotlin doesn't force anyone to declare or handle it.
- Logs:
  ```
  Creating new transaction ... PROPAGATION_REQUIRED    -> BEGIN
  Participating in existing transaction                -> no SQL
  publish: fail with a checked exception.
  Initiating transaction commit                        -> COMMIT  (!)
  ```
- Result: the caller gets `IOException`, yet the account is `ACTIVE` and **the outbox row written just before the failure is committed**.
- Why: the two mechanisms use different rollback rules.

  | Mechanism         | Rolls back on                                      | Checked exception                                 |
  |-------------------|----------------------------------------------------|---------------------------------------------------|
  | `@Transactional`  | `RuntimeException`, `Error` only                   | **commit**, then rethrow                          |
  | `dsl.transaction` | any `Throwable`                                    | rollback, rethrow wrapped in `DataAccessException` |

  The jOOQ column comes from `DefaultDSLContext.transactionResult0`. With `dsl.transaction` as the outer boundary (as in v1–v3), the same code would roll back.
- Takeaway: the same "inner failure" is either rolled back or committed, depending on which mechanism draws the boundary. Kotlin code passes checked exceptions around without anyone noticing.
  Fix: `@Transactional(rollbackFor = [Exception::class])`, or a team-wide convention to throw only unchecked exceptions.

### v7: Coroutine thread switch: the inner write escapes the transaction

- Code: `v7_coroutine_thread_switch`
- The outer `dsl.transaction` calls `publish()` through `runBlocking(Dispatchers.IO) { ... }`, then fails with `SELECT 1/0`. `OutboxService` is identical to the base case.
- Logs (note the thread names):
  ```
  [Test worker]       Creating new transaction ... PROPAGATION_NESTED   -> BEGIN       txId=761, pid=72
  [DefaultDispatcher] Creating new transaction ... PROPAGATION_NESTED   -> BEGIN (!)   txId=762, pid=73
  [DefaultDispatcher] Committing JDBC transaction                       -> COMMIT
  [Test worker]       Rolling back JDBC transaction                     -> ROLLBACK
  ```
- Result: `DataIntegrityViolationException`. The account is still `NEW`, but **1 outbox row exists**: an event for a change that never happened.
- Why: Spring stores the transaction and its connection in a `ThreadLocal` (`TransactionSynchronizationManager`).
  The IO thread sees no transaction, so `PROPAGATION_NESTED` starts a **new top-level transaction on a second connection** (a different `pid`) and commits it on its own.
- Same trap: `@Async`, `CompletableFuture.supplyAsync`, `parallelStream()`, and `withContext(...)` inside a transaction.
- Takeaway: a Spring transaction belongs to a **thread**, not to the code block it's written in. Keep transactional work on a single thread, and only start coroutines outside the boundary.

### v8: SERIALIZABLE: the error shows up at COMMIT, outside any catch

- Code: `v8_serializable_commit_failure`
- Business rule: **at most one ACTIVE account**. `activate()` runs `SET TRANSACTION ISOLATION LEVEL SERIALIZABLE` as its first statement (Spring's jOOQ provider has no isolation option). It counts the ACTIVE accounts, updates, then calls `publish()`. `publish()` wraps its savepoint in try/catch, as in v3. A sleep before commit stands in for slow work.
- The test interleaves two transactions (write skew):
  ```
  T1 (activate 1)                          T2 (test, activate 2)
  BEGIN; SERIALIZABLE
  count(ACTIVE) = 0
  UPDATE account 1
  SAVEPOINT; INSERT outbox; RELEASE
  ... slow work ...                        BEGIN; SERIALIZABLE
                                           count(ACTIVE) = 0   (T1 not committed)
                                           UPDATE account 2
                                           COMMIT  ✅ first committer wins
  COMMIT  ❌ 40001
  ```
- Error:
  ```
  CannotAcquireLockException: JDBC commit; ERROR: could not serialize access due to read/write dependencies among transactions
  Detail: Reason code: Canceled on identification as a pivot, during commit attempt.
  Hint: The transaction might succeed if retried.
  ```
- Result: account 1 `NEW`, account 2 `ACTIVE`, 0 outbox rows. The rule holds and T1 is rolled back completely.
- Why: in SERIALIZABLE (SSI), Postgres tracks read/write dependencies. Each transaction read what the other wrote, so the two form a cycle.
  Neither statement conflicts on a row, so no statement fails. The conflict is resolved when T2 commits, which dooms T1, and Postgres reports it at **T1's COMMIT**.
- Takeaways:
  - Every statement succeeded, including the savepoint and the inner try/catch. The only place the failure surfaces is the **outermost** boundary.
  - So **retry the whole outer transaction** on `40001`. Retrying inside it (an inner `@Retryable`, catching in `publish`) can't work.
  - Spring maps this to `CannotAcquireLockException` (a `PessimisticLockingFailureException`), not to anything with "serialization" in the name. Use it as the retry trigger.
  - Without SERIALIZABLE (READ COMMITTED, the default), both commits succeed and you end up with **two ACTIVE accounts**.

---

## 4. Summary

| Case | Inner mechanism                     | Inner becomes on PG          | Inner fails?           | Final state                   | Atomic? |
|------|-------------------------------------|------------------------------|------------------------|-------------------------------|---------|
| v1   | `dsl.transaction` (Spring provider) | `SAVEPOINT` / `RELEASE`      | no                     | account ✅ outbox ✅           | ✅      |
| v2   | `dsl.transaction` (Spring provider) | `ROLLBACK TO SAVEPOINT`      | yes, rethrown          | account ❌ outbox ❌           | ✅      |
| v3   | `dsl.transaction` (Spring provider) | `ROLLBACK TO SAVEPOINT`      | yes, swallowed         | account ✅ outbox ❌           | ❌ (by business rule) |
| v4   | `@Transactional` (`REQUIRED`)       | nothing, joins outer tx      | no                     | account ✅ outbox ✅           | ✅      |
| v5   | jOOQ `DefaultTransactionProvider`   | `COMMIT` of the outer tx     | no                     | account ✅ outbox ✅ (early)   | ❌ (outer can't roll back) |
| v6   | `@Transactional` + checked exception | nothing, joins outer tx     | yes, checked, rethrown | account ✅ outbox ✅           | ❌ (failure got committed) |
| v7   | `dsl.transaction` on another thread | separate `BEGIN`/`COMMIT` on another connection | outer fails | account ❌ outbox ✅    | ❌ (inner escaped) |
| v8   | `dsl.transaction`, SERIALIZABLE     | `SAVEPOINT` / `RELEASE`      | no, outer `COMMIT` fails (40001) | account ❌ outbox ❌ | ✅ (retry the outer tx) |

---

## 5. Key takeaways

1. PostgreSQL has no nested transactions, only savepoints. Every "nested transaction" API maps to a savepoint, a join, or a separate commit.
2. With Spring Boot's auto-configured `DSLContext`, a nested `dsl.transaction` becomes a **savepoint** (`PROPAGATION_NESTED`).
3. A savepoint rollback is partial on purpose. If you **catch** an inner failure, the outer transaction commits whatever is left (v3). Rethrow unless partial success is what you want.
4. `@Transactional` defaults to `REQUIRED` (join, no savepoint), not `NESTED`. Mixing it with `dsl.transaction` works, but the semantics differ.
5. Use **one** `TransactionProvider` per connection. A second jOOQ `DefaultTransactionProvider` can commit the Spring transaction early (v5).
6. `@Transactional` commits on checked exceptions, while `dsl.transaction` rolls back on anything. Know which one draws the boundary (v6).
7. A Spring transaction is bound to the thread. Coroutines, `@Async` and parallel streams inside it run outside it, on another connection (v7).
8. Under SERIALIZABLE the error can come at `COMMIT`. Handle it, and retry, at the outermost boundary only (v8).
9. Don't guess. Verify with `JdbcTransactionManager` DEBUG logs and `pg_current_xact_id()` / `pg_backend_pid()` / `subxact_count`.

---

## 6. Running the demo

Requires Docker (Testcontainers starts `postgres:18.6-alpine`).

```bash
./gradlew test                                               # all cases
./gradlew test --tests '*v3_db_exception_caught*'            # a single case
```

Transaction logs show up in the test output (`build/reports/tests/test/index.html` → Standard output).
Log levels are set in `src/main/resources/application.yaml`.

### Ideas for live demo / Q&A

- v4: make `publish()` throw and catch the exception in `changeStatus`. You should see `UnexpectedRollbackException`, because nothing can be partially rolled back without a savepoint.
- v4: switch to `@Transactional(propagation = Propagation.NESTED)` and look for `Creating nested transaction` in the logs.
- v5: add `throw` after `publish()` in `changeStatus` and watch the account update survive.
