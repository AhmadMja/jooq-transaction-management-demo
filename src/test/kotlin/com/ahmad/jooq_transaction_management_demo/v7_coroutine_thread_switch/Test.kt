package com.ahmad.jooq_transaction_management_demo.v7_coroutine_thread_switch

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
import org.springframework.dao.DataIntegrityViolationException

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class Test {

    @Autowired
    lateinit var dsl: DSLContext
    @Autowired
    @Qualifier("accountService_coroutine_thread_switch")
    lateinit var accountService: AccountService

    @BeforeEach
    fun setUp() {
        dsl.execute("TRUNCATE account, outbox RESTART IDENTITY")
        dsl.execute("INSERT INTO account (id, status) VALUES (1, 'NEW')")
        dsl.resultQuery("select pg_stat_clear_snapshot()").fetch()
    }

    @Test
    fun `outer transaction fails, but outbox message escaped on another thread`() {
        assertThrows<DataIntegrityViolationException> {
            accountService.changeStatus(1, "ACTIVE")
        }

        val accounts = dsl.select().from(AccountTable.TABLE).fetch()
        val outboxMessages = dsl.select().from(OutboxTable.TABLE).fetch()

        // Then: Account is rolled back, but the outbox message is already committed by its own transaction.
        assertThat(accounts.size).isEqualTo(1)
        assertThat(accounts[0].get(AccountTable.STATUS)).isEqualTo("NEW")
        assertThat(outboxMessages.size).isEqualTo(1)
    }
}
