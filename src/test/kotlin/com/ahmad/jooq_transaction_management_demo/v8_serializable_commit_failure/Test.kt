package com.ahmad.jooq_transaction_management_demo.v8_serializable_commit_failure

import com.ahmad.jooq_transaction_management_demo.TestcontainersConfiguration
import com.ahmad.jooq_transaction_management_demo.common.AccountTable
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.CannotAcquireLockException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class Test {

    @Autowired
    lateinit var dsl: DSLContext
    @Autowired
    @Qualifier("accountService_serializable_commit_failure")
    lateinit var accountService: AccountService

    @BeforeEach
    fun setUp() {
        dsl.execute("TRUNCATE account, outbox RESTART IDENTITY")
        dsl.execute("INSERT INTO account (id, status) VALUES (1, 'NEW'), (2, 'NEW')")
        dsl.resultQuery("select pg_stat_clear_snapshot()").fetch()
    }

    @Test
    fun `concurrent activation commits first, our transaction fails at commit`() {
        // T1: activate account 1 on another thread. It pauses after publish, before COMMIT.
        val activation = CompletableFuture.runAsync { accountService.activate(1) }
        waitUntilOutboxIsWrittenByAnotherTransaction()

        // T2: activate account 2 concurrently. Sees no ACTIVE account (T1 is not committed yet) and commits first.
        dsl.transaction { _ ->
            dsl.execute("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE")
            assertThat(dsl.fetchCount(AccountTable.TABLE, AccountTable.STATUS.eq("ACTIVE"))).isEqualTo(0)
            dsl.update(AccountTable.TABLE)
                .set(AccountTable.STATUS, "ACTIVE")
                .where(AccountTable.ID.eq(2))
                .execute()
        }

        // Then: T1 fails at COMMIT, after every statement (and publish's try/catch) already succeeded.
        val exception = assertThrows<ExecutionException> { activation.get() }
        // Spring translates PG 40001 (serialization_failure) raised by "JDBC commit".
        assertThat(exception.cause).isInstanceOf(CannotAcquireLockException::class.java)
        assertThat(exception.cause!!.message).contains("JDBC commit", "could not serialize access")

        val accounts = dsl.select().from(AccountTable.TABLE).orderBy(AccountTable.ID).fetch()
        val outboxMessages = dsl.select().from(OutboxTable.TABLE).fetch()

        // Then: Only T2's activation is saved. Business rule holds, T1's outbox row is rolled back too.
        assertThat(accounts.map { it.get(AccountTable.STATUS) }).containsExactly("NEW", "ACTIVE")
        assertThat(outboxMessages.size).isEqualTo(0)
    }

    // T1 holds a ROW EXCLUSIVE lock on outbox from its INSERT until it commits.
    private fun waitUntilOutboxIsWrittenByAnotherTransaction() {
        repeat(100) {
            val outboxWriters = dsl.fetchValue(
                """
                select count(*)
                from pg_locks
                where relation = 'outbox'::regclass
                  and mode = 'RowExclusiveLock'
                  and pid <> pg_backend_pid()
                """.trimIndent()
            ) as Long
            if (outboxWriters > 0) return
            Thread.sleep(50)
        }
        error("T1 never wrote to outbox")
    }
}
