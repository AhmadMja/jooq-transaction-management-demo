package com.ahmad.jooq_transaction_management_demo.v8_serializable_commit_failure

import com.ahmad.jooq_transaction_management_demo.common.AccountTable.ID
import com.ahmad.jooq_transaction_management_demo.common.AccountTable.STATUS
import com.ahmad.jooq_transaction_management_demo.common.AccountTable.TABLE
import com.ahmad.jooq_transaction_management_demo.common.DslUtils.logTransactionInfo
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service("accountService_serializable_commit_failure")
class AccountService(
    private val dsl: DSLContext,
    private val outboxService: OutboxService,
) {

    val log = LoggerFactory.getLogger(AccountService::class.java)

    /**
     * Business rule: at most one ACTIVE account.
     * SERIALIZABLE protects the rule against a concurrent activation (write skew),
     * but Postgres may only report the conflict at COMMIT.
     */
    fun activate(id: Int) {
        dsl.transaction { _ ->
            // Must be the first statement of the transaction.
            dsl.execute("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE")
            val activeAccounts = dsl.fetchCount(TABLE, STATUS.eq(ACTIVE))
            check(activeAccounts == 0) { "There is already an active account" }
            dsl.update(TABLE)
                .set(STATUS, ACTIVE)
                .where(ID.eq(id))
                .execute()
            logTransactionInfo(dsl)
            outboxService.publish("accountId: $id changed status to $ACTIVE")
            log.info("---activate: inside transaction block, after published returned. Doing some slow work before commit.")
            // Simulates slow work (e.g. calling another service) while the transaction is still open.
            Thread.sleep(SLOW_WORK_MILLIS)
            log.info("---activate: end of transaction block, about to commit.")
        }
        log.info("---activate: after transaction block is done.")
    }

    companion object {
        const val ACTIVE = "ACTIVE"
        const val SLOW_WORK_MILLIS = 2_000L
    }
}
