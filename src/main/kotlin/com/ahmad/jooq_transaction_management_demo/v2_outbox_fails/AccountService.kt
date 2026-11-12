package com.ahmad.jooq_transaction_management_demo.v2_outbox_fails

import com.ahmad.jooq_transaction_management_demo.common.AccountTable.ID
import com.ahmad.jooq_transaction_management_demo.common.AccountTable.STATUS
import com.ahmad.jooq_transaction_management_demo.common.AccountTable.TABLE
import com.ahmad.jooq_transaction_management_demo.common.DslUtils.logTransactionInfo
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service("accountService_outbox_fails")
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
            outboxService.publish("accountId: $id changed status to $status")
        }
    }
}
