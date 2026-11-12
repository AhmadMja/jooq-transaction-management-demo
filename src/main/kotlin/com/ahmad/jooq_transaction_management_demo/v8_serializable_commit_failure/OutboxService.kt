package com.ahmad.jooq_transaction_management_demo.v8_serializable_commit_failure

import com.ahmad.jooq_transaction_management_demo.common.DslUtils.logTransactionInfo
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.MESSAGE_ID
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.PAYLOAD
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.TABLE
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service("outboxService_serializable_commit_failure")
class OutboxService(private val dsl: DSLContext) {

    val log = LoggerFactory.getLogger(OutboxService::class.java)

    /**
     * Same defensive style as the caught-exception case: failures are handled here.
     * The serialization failure is raised at the outer COMMIT, so this catch never sees it.
     */
    fun publish(payload: String) {
        try {
            dsl.transactionResult { _ ->
                dsl.insertInto(TABLE)
                    .set(PAYLOAD, payload)
                    .returning(MESSAGE_ID)
                    .fetchOne(MESSAGE_ID)!!
                logTransactionInfo(dsl)
            }
            log.info("------publish: savepoint released successfully.")
        } catch (e: Exception) {
            log.info("Handled exception occurred while executing insert into table")
        }
    }
}
