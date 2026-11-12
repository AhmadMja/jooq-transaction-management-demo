package com.ahmad.jooq_transaction_management_demo.v7_coroutine_thread_switch

import com.ahmad.jooq_transaction_management_demo.common.DslUtils.logTransactionInfo
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.MESSAGE_ID
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.PAYLOAD
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.TABLE
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service("outboxService_coroutine_thread_switch")
class OutboxService(private val dsl: DSLContext) {

    val log = LoggerFactory.getLogger(OutboxService::class.java)

    /**
     * Same code as the base case. On a thread without a bound transaction,
     * this opens a brand-new transaction on a different connection: BEGIN/COMMIT, not SAVEPOINT/RELEASE.
     */
    fun publish(payload: String) {
        log.info("------publish: before transaction block. thread={}", Thread.currentThread().name)
        dsl.transactionResult { _ ->
            dsl.insertInto(TABLE)
                .set(PAYLOAD, payload)
                .returning(MESSAGE_ID)
                .fetchOne(MESSAGE_ID)!!
            logTransactionInfo(dsl)
        }
        log.info("------publish: after transaction block.")
    }
}
