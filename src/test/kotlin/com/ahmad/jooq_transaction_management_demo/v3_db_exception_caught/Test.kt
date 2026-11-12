package com.ahmad.jooq_transaction_management_demo.v3_db_exception_caught

import com.ahmad.jooq_transaction_management_demo.TestcontainersConfiguration
import com.ahmad.jooq_transaction_management_demo.common.AccountTable
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class Test {

    @Autowired
    lateinit var dsl: DSLContext

    @Autowired
    @Qualifier("accountService_db_exception_caught")
    lateinit var accountService: AccountService

    @BeforeEach
    fun setUp() {
        dsl.execute("TRUNCATE account, outbox RESTART IDENTITY")
        dsl.execute("INSERT INTO account (id, status) VALUES (1, 'NEW')")
    }

    @Test
    fun `inner transaction fails, but exception is caught`() {
        accountService.changeStatus(1, "ACTIVE")

        val accounts = dsl.select().from(AccountTable.TABLE).fetch()
        val outboxMessages = dsl.select().from(OutboxTable.TABLE).fetch()

        // Unexpected behavior! Status is set but outbox didn't publish.
        assertThat(accounts.size).isEqualTo(1)
        assertThat(accounts[0].get(AccountTable.STATUS)).isEqualTo("ACTIVE")
        assertThat(outboxMessages.size).isEqualTo(0)
    }
}
