package com.ahmad.jooq_transaction_management_demo.v6_jooq_default_tx_provider_outer_fails

import com.ahmad.jooq_transaction_management_demo.common.AccountTable.ID
import com.ahmad.jooq_transaction_management_demo.common.AccountTable.STATUS
import com.ahmad.jooq_transaction_management_demo.common.AccountTable.TABLE
import com.ahmad.jooq_transaction_management_demo.common.DslUtils.logTransactionInfo
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service("accountService_jooq_default_tx_provider_outer_fails")
class AccountService(
    private val dsl: DSLContext,
    private val outboxService: OutboxService,
) {

    val log = LoggerFactory.getLogger(AccountService::class.java)

    fun changeStatus(id: Int, status: String) {
        log.info("---changeStatus: beginning of the method, before transaction block")
        dsl.transaction { _ ->
            log.info("---changeStatus: before calling db")
            dsl.update(TABLE)
                .set(STATUS, status)
                .where(ID.eq(id))
                .execute()
            log.info("---changeStatus: after calling db.")
            logTransactionInfo(dsl)
            outboxService.publish("accountId: $id changed status to $status")
            // publish() used another TransactionProvider. It believed it was top-level and committed this connection.
            logTransactionInfo(dsl)
            log.info("---changeStatus: fail in the outer transaction, after published returned.")
            dsl.execute("SELECT 1/0")
        }
        log.info("---changeStatus: after transaction block is done.")
    }
}
