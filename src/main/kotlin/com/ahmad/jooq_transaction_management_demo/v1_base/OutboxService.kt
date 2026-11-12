package com.ahmad.jooq_transaction_management_demo.v1_base

import com.ahmad.jooq_transaction_management_demo.common.DslUtils.logTransactionInfo
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.MESSAGE_ID
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.PAYLOAD
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.TABLE
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service("outboxService_base")
class OutboxService(private val dsl: DSLContext) {

    val log = LoggerFactory.getLogger(OutboxService::class.java)

    /**
     * Own transaction. Called standalone -> BEGIN/COMMIT.
     * Called from inside another transaction -> SAVEPOINT/RELEASE (or ROLLBACK TO SAVEPOINT on failure).
     */
    fun publish(payload: String) {
        log.info("------publish: before transaction block.")
        dsl.transactionResult { _ ->
            log.info("------publish: inside transaction block before calling db.")
            dsl.insertInto(TABLE)
                .set(PAYLOAD, payload)
                .returning(MESSAGE_ID)
                .fetchOne(MESSAGE_ID)!!
            logTransactionInfo(dsl)
            log.info("------publish: inside transaction block after calling db.")
        }
        log.info("------publish: after transaction block.")
    }
}
