package com.ahmad.jooq_transaction_management_demo.v6_checked_exception_with_annotation

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
import java.io.IOException

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class Test {

    @Autowired
    lateinit var dsl: DSLContext
    @Autowired
    @Qualifier("accountService_checked_exception_with_annotation")
    lateinit var accountService: AccountService

    @BeforeEach
    fun setUp() {
        dsl.execute("TRUNCATE account, outbox RESTART IDENTITY")
        dsl.execute("INSERT INTO account (id, status) VALUES (1, 'NEW')")
        dsl.resultQuery("select pg_stat_clear_snapshot()").fetch()
    }

    @Test
    fun `inner transaction fails with checked exception, everything is committed`() {
        assertThrows<IOException> {
            accountService.changeStatus(1, "ACTIVE")
        }

        val accounts = dsl.select().from(AccountTable.TABLE).fetch()
        val outboxMessages = dsl.select().from(OutboxTable.TABLE).fetch()

        // Then: Caller gets an exception, but everything is saved, including the outbox row written right before the failure.
        assertThat(accounts.size).isEqualTo(1)
        assertThat(accounts[0].get(AccountTable.STATUS)).isEqualTo("ACTIVE")
        assertThat(outboxMessages.size).isEqualTo(1)
    }
}
