package com.ahmad.jooq_transaction_management_demo.v4_base_with_annotation

import com.ahmad.jooq_transaction_management_demo.common.AccountTable.ID
import com.ahmad.jooq_transaction_management_demo.common.AccountTable.STATUS
import com.ahmad.jooq_transaction_management_demo.common.AccountTable.TABLE
import com.ahmad.jooq_transaction_management_demo.common.DslUtils.logTransactionInfo
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service("accountService_base_with_annotation")
class AccountService(
    private val dsl: DSLContext,
    private val outboxService: OutboxService,
) {

    val log = LoggerFactory.getLogger(AccountService::class.java)

    fun changeStatus(id: Int, status: String) {
        log.info("---changeStatus: beginning of the method, before transaction block")
        // TODO: This is a spring provider, is this sentence correct? does spring provider always use nested?
        // TODO: What happens for this case that the transaction provider for the outer and inner transactions are different? Do they point to the same transaction? And why?
        log.info("---changeStatus: transactionProvider={}", dsl.configuration().transactionProvider().javaClass.name)
        dsl.transaction { _ ->
            log.info("---changeStatus: before calling db")
            dsl.update(TABLE)
                .set(STATUS, status)
                .where(ID.eq(id))
                .execute()
            log.info("---changeStatus: after calling db.")
            logTransactionInfo(dsl)
            outboxService.publish("accountId: $id changed status to $status")
            log.info("---changeStatus: inside transaction block, after published returned.")
        }
        log.info("---changeStatus: after transaction block is done.")
    }
}
