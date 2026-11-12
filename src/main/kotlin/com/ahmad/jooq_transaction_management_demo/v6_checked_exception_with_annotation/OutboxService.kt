package com.ahmad.jooq_transaction_management_demo.v6_checked_exception_with_annotation

import com.ahmad.jooq_transaction_management_demo.common.DslUtils.logTransactionInfo
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.MESSAGE_ID
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.PAYLOAD
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.TABLE
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.IOException

@Service("outboxService_checked_exception_with_annotation")
class OutboxService(private val dsl: DSLContext) {

    val log = LoggerFactory.getLogger(OutboxService::class.java)

    /**
     * Kotlin has no checked exceptions, so nothing forces us to declare or handle IOException here.
     * Spring still treats it as a checked exception -> no rollback.
     */
    @Transactional
    fun publish(payload: String) {
        log.info("------publish: inside transaction block before calling db.")
        dsl.insertInto(TABLE)
            .set(PAYLOAD, payload)
            .returning(MESSAGE_ID)
            .fetchOne(MESSAGE_ID)!!
        logTransactionInfo(dsl)
        log.info("------publish: fail with a checked exception.")
        throw IOException("Failed to notify the broker")
    }
}
