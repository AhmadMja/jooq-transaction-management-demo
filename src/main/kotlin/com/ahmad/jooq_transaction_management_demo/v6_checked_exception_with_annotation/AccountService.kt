package com.ahmad.jooq_transaction_management_demo.v6_checked_exception_with_annotation

import com.ahmad.jooq_transaction_management_demo.common.AccountTable.ID
import com.ahmad.jooq_transaction_management_demo.common.AccountTable.STATUS
import com.ahmad.jooq_transaction_management_demo.common.AccountTable.TABLE
import com.ahmad.jooq_transaction_management_demo.common.DslUtils.logTransactionInfo
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service("accountService_checked_exception_with_annotation")
class AccountService(
    private val dsl: DSLContext,
    private val outboxService: OutboxService,
) {

    val log = LoggerFactory.getLogger(AccountService::class.java)

    // Spring only rolls back on RuntimeException and Error by default. A checked exception -> COMMIT.
    @Transactional
    fun changeStatus(id: Int, status: String) {
        log.info("---changeStatus: inside transaction block before calling db.")
        dsl.update(TABLE)
            .set(STATUS, status)
            .where(ID.eq(id))
            .execute()
        logTransactionInfo(dsl)
        outboxService.publish("accountId: $id changed status to $status")
        log.info("---changeStatus: inside transaction block, after published returned.")
    }
}
