package com.ahmad.jooq_transaction_management_demo.v5_base_with_jooq_default_tx_provider

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
    @Qualifier("accountService_base_with_jooq_default_tx_provider")
    lateinit var accountService: AccountService

    @BeforeEach
    fun setUp() {
        dsl.execute("TRUNCATE account, outbox RESTART IDENTITY")
        dsl.execute("INSERT INTO account (id, status) VALUES (1, 'NEW')")
    }

    @Test
    fun `changeStatus updates account and publishes outbox message`() {
        accountService.changeStatus(1, "ACTIVE")

        val accounts = dsl.select().from(AccountTable.TABLE).fetch()
        val outboxMessages = dsl.select().from(OutboxTable.TABLE).fetch()

        assertThat(accounts.size).isEqualTo(1)
        assertThat(accounts[0].get(AccountTable.STATUS)).isEqualTo("ACTIVE")
        assertThat(outboxMessages.size).isEqualTo(1)
        assertThat(outboxMessages[0].get(OutboxTable.PAYLOAD)).contains("ACTIVE")
    }
}
