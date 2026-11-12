package com.ahmad.jooq_transaction_management_demo.v7_coroutine_thread_switch

import com.ahmad.jooq_transaction_management_demo.common.AccountTable.ID
import com.ahmad.jooq_transaction_management_demo.common.AccountTable.STATUS
import com.ahmad.jooq_transaction_management_demo.common.AccountTable.TABLE
import com.ahmad.jooq_transaction_management_demo.common.DslUtils.logTransactionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service("accountService_coroutine_thread_switch")
class AccountService(
    private val dsl: DSLContext,
    private val outboxService: OutboxService,
) {

    val log = LoggerFactory.getLogger(AccountService::class.java)

    fun changeStatus(id: Int, status: String) {
        dsl.transaction { _ ->
            dsl.update(TABLE)
                .set(STATUS, status)
                .where(ID.eq(id))
                .execute()
            logTransactionInfo(dsl)
            // Spring binds the transaction (and its connection) to the current thread.
            // Dispatchers.IO runs publish on another thread, which sees no transaction.
            runBlocking(Dispatchers.IO) {
                outboxService.publish("accountId: $id changed status to $status")
            }
            log.info("---changeStatus: fail in the outer transaction, after published returned.")
            dsl.execute("SELECT 1/0")
        }
    }
}
